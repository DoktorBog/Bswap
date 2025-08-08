package com.bswap.server

import com.bswap.server.data.dexscreener.models.TokenBoost
import com.bswap.server.data.dexscreener.models.TokenProfile
import com.bswap.server.data.solana.jito.JitoBundlerService
import com.bswap.server.data.solana.pumpfun.TokenTradeResponse
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.solana.transaction.DefaultTransactionExecutor
import com.bswap.shared.wallet.WalletConfig
import com.bswap.server.data.solana.transaction.TokenInfo
import com.bswap.server.data.solana.transaction.createCloseAccountInstruction
import com.bswap.server.data.solana.transaction.createSwapTransaction
import com.bswap.server.data.solana.transaction.createTransactionWithInstructions
import com.bswap.server.data.solana.transaction.executeSolTransaction
import com.bswap.server.data.solana.transaction.executeSwapTransaction
import com.bswap.server.data.solana.transaction.getTokenAccountsByOwner
import com.bswap.server.stratagy.TradingStrategy
import com.bswap.server.stratagy.TradingStrategyFactory
import com.bswap.server.validation.TokenValidator
import com.bswap.server.validation.ValidationConfig
import foundation.metaplex.rpc.networking.NetworkDriver
import foundation.metaplex.solanapublickeys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val logger = LoggerFactory.getLogger("SolanaTokenSwapBot")

@OptIn(FlowPreview::class)
class SolanaTokenSwapBot(
    override val walletConfig: WalletConfig = WalletConfig.current(),
    override val config: SolanaSwapBotConfig = SolanaSwapBotConfig(),
    private val executor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
    private val jupiterSwapService: JupiterSwapService = JupiterSwapService(client),
    private val jitoBundlerService: JitoBundlerService = JitoBundlerService(
        client = client,
        jitoFeeLamports = 1000,
        tipAccounts = listOf(
            "Cw8CFyM9FkoMi7K7Crf6HNQqf4uEMzpKw6QNghXLvLkY",
            "DttWaMuVvTiduZRnguLF7jNxTgiMBZ1hyAumKUiL2KRL",
            "96gYZGLnJYVFmbjzopPSU6QiEV5fGqZNyN9nmNhvrZU5",
            "3AVi9Tg9Uo68tJfuvoKvqKNWKkC5wPdSSdeBnizKZ6jT",
            "HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe",
            "ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49",
            "ADuUkR4vqLUMWXxW9gh6D6L8pMSawimctcNZ5pGwDcEt",
            "DfXygSm4jCyNCybVYYK6DwvWqjKee8pbDmJGcLWNDXjh"
        )
    ),
    private val managementService: com.bswap.server.service.BotManagementService? = null,
    private val tokenValidator: TokenValidator = TokenValidator(client, ValidationConfig())
) : TradingRuntime {
    private val strategy: TradingStrategy = TradingStrategyFactory.create(config.strategySettings, tokenValidator)
    private var processingTokens = AtomicInteger(0)
    private val stateMap = ConcurrentHashMap<String, TokenStatus>()
    private val lastSell = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isActive = AtomicBoolean(false)

    fun start() {
        if (_isActive.get()) return
        _isActive.set(true)
        if (config.autoSellAllSpl) {
            scope.launch {
                while (_isActive.get()) {
                    sellAllOnce()
                    delay(config.sellAllSplIntervalMs)
                }
            }
        }
        scope.launch {
            while (_isActive.get()) {
                closeZeroAccounts()
                delay(config.closeAccountsIntervalMs)
            }
        }
        scope.launch {
            while (_isActive.get()) {
                delay(5_000)
                clearUnboughtCoins()
            }
        }
        scope.launch {
            while (_isActive.get()) {
                strategy.onTick(this@SolanaTokenSwapBot)
                delay(config.strategyTickMs)
            }
        }
        logger.info("SolanaTokenSwapBot started with strategy ${strategy.type}")
    }

    fun stop() {
        _isActive.set(false)
        logger.info("SolanaTokenSwapBot stopped")
    }

    fun isActive(): Boolean = _isActive.get()

    fun getCurrentState(): Map<String, TokenStatus> = stateMap.toMap()

    fun getActiveTokensCount(): Int = stateMap.size

    fun singleTrade(mint: String) = scope.launch(Dispatchers.IO) { buy(mint) }

    fun sellOneToken(mint: String) = scope.launch(Dispatchers.IO) { sell(mint) }

    fun observeProfiles(flow: Flow<List<TokenProfile>>) = scope.launch {
        flow.collect { list ->
            list.filter { it.chainId == "solana" }.forEach { p ->
                strategy.onDiscovered(
                    TokenMeta(p.tokenAddress, TokenSource.PROFILE, profile = p),
                    this@SolanaTokenSwapBot
                )
            }
        }
    }

    fun observePumpFun(flow: Flow<TokenTradeResponse>) = scope.launch {
        flow.debounce(2000).collect { t ->
            strategy.onDiscovered(TokenMeta(t.mint, TokenSource.PUMPFUN, pump = t), this@SolanaTokenSwapBot)
        }
    }

    fun observeBoosted(flow: Flow<List<TokenBoost>>) = scope.launch {
        flow.sample(1_000).collect { list ->
            list.filter { it.chainId == "solana" }.shuffled().take(5).forEach { b ->
                strategy.onDiscovered(TokenMeta(b.tokenAddress, TokenSource.BOOST, boost = b), this@SolanaTokenSwapBot)
            }
        }
    }


    override fun now(): Long = System.currentTimeMillis()

    override fun isNew(mint: String): Boolean = !stateMap.containsKey(mint)

    override fun status(mint: String): TokenStatus? = stateMap[mint]

    override suspend fun buy(mint: String): Boolean {
        stateMap[mint] = TokenStatus(mint, TokenState.TradePending)
        val validationResult = withContext(Dispatchers.IO) { tokenValidator.validateToken(mint) }
        if (!validationResult.isValid) {
            stateMap[mint]?.state = TokenState.SellFailed("Validation failed")
            managementService?.incrementFailedTrades()
            return false
        }
        if (validationResult.riskScore > config.validationMaxRisk) {
            stateMap[mint]?.state = TokenState.SellFailed("High risk ${validationResult.riskScore}")
            managementService?.incrementFailedTrades()
            return false
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                jupiterSwapService.getQuoteAndPerformSwap(
                    config.solAmountToTrade.toPlainString(),
                    config.swapMint.base58(),
                    mint,
                    walletConfig.publicKey
                )
            }.fold(onSuccess = { swap ->
                if (swap.swapTransaction == null) {
                    stateMap.remove(mint)
                    false
                } else {
                    if (config.useJito) {
                        jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                        stateMap[mint]?.state = TokenState.Swapped
                        managementService?.incrementSuccessfulTrades()
                        true
                    } else {
                        val success = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                        if (success) {
                            stateMap[mint]?.state = TokenState.Swapped
                            managementService?.incrementSuccessfulTrades()
                            true
                        } else {
                            stateMap[mint]?.state = TokenState.SellFailed("Swap failed")
                            managementService?.incrementFailedTrades()
                            false
                        }
                    }
                }
            }, onFailure = { ex ->
                stateMap[mint]?.state = TokenState.SellFailed("Swap exception: ${ex.message}")
                managementService?.incrementFailedTrades()
                false
            })
        }
    }

    override suspend fun sell(mint: String): Boolean {
        val status = stateMap[mint] ?: return false
        if (status.state != TokenState.Swapped && status.state !is TokenState.SellFailed) return false
        stateMap[mint] = TokenStatus(mint, TokenState.Selling)
        val info = tokenInfo(mint) ?: run {
            stateMap[mint]?.state = TokenState.SellFailed("No token info")
            return false
        }
        if (info.tokenAmount.amount == "0") {
            stateMap[mint]?.state = TokenState.SellFailed("Zero balance")
            return false
        }
        setLastSell(mint)
        return withContext(Dispatchers.IO) {
            runCatching {
                jupiterSwapService.getQuoteAndPerformSwap(
                    info.tokenAmount.amount,
                    mint,
                    config.swapMint.base58(),
                    walletConfig.publicKey
                )
            }.fold(onSuccess = { swap ->
                if (swap.swapTransaction == null) {
                    stateMap[mint]?.state = TokenState.SellFailed("No route")
                    false
                } else {
                    if (config.useJito) {
                        jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                        stateMap[mint]?.state = TokenState.Sold
                        managementService?.incrementSuccessfulTrades()
                        true
                    } else {
                        val sold = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                        if (sold) {
                            stateMap[mint]?.state = TokenState.Sold
                            managementService?.incrementSuccessfulTrades()
                            true
                        } else {
                            stateMap[mint]?.state = TokenState.SellFailed("Sell failed")
                            managementService?.incrementFailedTrades()
                            false
                        }
                    }
                }
            }, onFailure = {
                stateMap[mint]?.state = TokenState.SellFailed("Sell exception: ${it.message}")
                managementService?.incrementFailedTrades()
                false
            })
        }
    }

    override suspend fun tokenInfo(mint: String): TokenInfo? {
        return allTokens().firstOrNull { it.address == mint }
    }

    override suspend fun allTokens(): List<TokenInfo> {
        return allAccounts()?.map { it.account.data.parsed.info } ?: emptyList()
    }

    private fun setLastSell(mint: String) {
        lastSell[mint] = System.currentTimeMillis()
    }

    private fun canSell(mint: String): Boolean {
        val last = lastSell[mint] ?: 0L
        return System.currentTimeMillis() - last >= config.sellWaitMs
    }

    private suspend fun closeZeroAccounts() {
        allAccounts()?.filter { it.account.data.parsed.info.tokenAmount.amount == "0" }
            ?.take(config.zeroBalanceCloseBatch)?.let { zeroList ->
            runCatching {
                val instruction =
                    zeroList.map { createCloseAccountInstruction(PublicKey(it.pubkey), PublicKey(walletConfig.publicKey)) }
                if (config.useJito) {
                    jitoBundlerService.enqueue(createTransactionWithInstructions(instruction).serialize())
                } else {
                    executeSolTransaction(rpc, instruction)
                }
            }
        }
    }

    private suspend fun sellAllOnce() {
        allTokens()
            .filterNot { it.address == config.swapMint.base58() || it.tokenAmount.amount == "0" }
            .filter { canSell(it.address) }
            .take(config.splSellBatch)
            .forEach { token ->
                stateMap[token.address] = TokenStatus(token.address, TokenState.Selling)
                setLastSell(token.address)
                runCatching {
                    jupiterSwapService.getQuoteAndPerformSwap(
                        token.tokenAmount.amount,
                        token.address,
                        config.swapMint.base58(),
                        walletConfig.publicKey
                    )
                }.onSuccess { swap ->
                    if (swap.swapTransaction == null) return@onSuccess
                    if (config.useJito) {
                        jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                        stateMap[token.address]?.state = TokenState.Sold
                    } else {
                        val sold = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                        stateMap[token.address]?.state =
                            if (sold) TokenState.Sold else TokenState.SellFailed("Failed selling ${token.address}")
                    }
                }.onFailure {
                    stateMap[token.address]?.state = TokenState.SellFailed("Bulk swap error ${token.address}: ${it.message}")
                }
                delay(500)
            }
    }

    private suspend fun allAccounts() = runCatching {
        getTokenAccountsByOwner(NetworkDriver(client), PublicKey(walletConfig.publicKey))
    }.getOrNull().also {
        processingTokens.set(it?.size ?: 0)
    }

    private suspend fun clearUnboughtCoins() {
        val now = System.currentTimeMillis()
        val expiredMints = stateMap.filterValues { status ->
            status.state is TokenState.TradePending && now - status.createdAt >= 30_000
        }.keys
        if (expiredMints.isNotEmpty()) {
            expiredMints.forEach { mint ->
                stateMap.remove(mint)
            }
        }
    }
}
