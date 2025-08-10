package com.bswap.server

import com.bswap.server.data.dexscreener.models.TokenBoost
import com.bswap.server.data.dexscreener.models.TokenProfile
import com.bswap.server.data.solana.jito.JitoBundlerService
import com.bswap.server.data.solana.pumpfun.TokenTradeResponse
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.solana.transaction.DefaultTransactionExecutor
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
import com.bswap.shared.wallet.WalletConfig
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

internal val logger = LoggerFactory.getLogger("SolanaTokenSwapBot")

@OptIn(FlowPreview::class)
class SolanaTokenSwapBot(
    override val walletConfig: WalletConfig = WalletConfig.current(),
    override val config: SolanaSwapBotConfig = SolanaSwapBotConfig(),
    private val executor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
    private val jupiterSwapService: JupiterSwapService = JupiterSwapService(client),
    private val jitoBundlerService: JitoBundlerService = JitoBundlerService(
        client = client,
        jitoFeeLamports = 10_000,
        tipAccounts = listOf(
            "HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe",
            "Cw8CFyM9FkoMi7K7Crf6HNQqf4uEMzpKw6QNghXLvLkY",
            "DttWaMuVvTiduZRnguLF7jNxTgiMBZ1hyAumKUiL2KRL",
            "ADuUkR4vqLUMWXxW9gh6D6L8pMSawimctcNZ5pGwDcEt",
            "DfXygSm4jCyNCybVYYK6DwvWqjKee8pbDmJGcLWNDXjh",
            "ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49",
            "3AVi9Tg9Uo68tJfuvoKvqKNWKkC5wPdSSdeBnizKZ6jT",
            "96gYZGLnJYVFmbjzopPSU6QiEV5fGqZNyN9nmNhvrZU5"
        )
    ),
    private val managementService: com.bswap.server.service.BotManagementService? = null,
    private val tokenValidator: TokenValidator = TokenValidator(client, ValidationConfig()),
    private val priceService: com.bswap.server.service.PriceService? = null
) : TradingRuntime {
    private val strategy: TradingStrategy = TradingStrategyFactory.create(config.strategySettings)
    private var processingTokens = AtomicInteger(0)
    private val stateMap = ConcurrentHashMap<String, TokenStatus>()
    private val lastSell = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isActive = AtomicBoolean(false)

    fun start() {
        if (_isActive.get()) return
        _isActive.set(true)
        logger.info("Bot starting. Wallet: ${walletConfig.publicKey}, UseJito=${config.useJito}, SOL/Trade=${config.solAmountToTrade}")
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
            if (!_isActive.get()) return@collect
            list.filter { it.chainId == "solana" }.forEach { p ->
                // Validate token before passing to strategy
                val validationResult = withContext(Dispatchers.IO) {
                    tokenValidator.validateToken(p.tokenAddress)
                }
                if (validationResult.isValid) {
                    strategy.onDiscovered(
                        TokenMeta(p.tokenAddress, TokenSource.PROFILE, profile = p),
                        this@SolanaTokenSwapBot
                    )
                } else {
                    logger.debug("PROFILE: Token validation failed for ${p.tokenAddress}: not valid")
                }
            }
        }
    }

    fun observePumpFun(flow: Flow<TokenTradeResponse>) = scope.launch {
        flow.debounce(2000).collect { t ->
            if (!_isActive.get()) return@collect
            // Validate token before passing to strategy
            val validationResult = withContext(Dispatchers.IO) {
                tokenValidator.validateToken(t.mint)
            }
            if (validationResult.isValid) {
                strategy.onDiscovered(TokenMeta(t.mint, TokenSource.PUMPFUN, pump = t), this@SolanaTokenSwapBot)
            } else {
                logger.debug("PUMPFUN: Token validation failed for ${t.mint}: not valid")
            }
        }
    }

    fun observeBoosted(flow: Flow<List<TokenBoost>>) = scope.launch {
        flow.sample(1_000).collect { list ->
            if (!_isActive.get()) return@collect
            list.filter { it.chainId == "solana" }.shuffled().take(5).forEach { b ->
                // Validate token before passing to strategy
                val validationResult = withContext(Dispatchers.IO) {
                    tokenValidator.validateToken(b.tokenAddress)
                }
                if (validationResult.isValid) {
                    strategy.onDiscovered(
                        TokenMeta(b.tokenAddress, TokenSource.BOOST, boost = b),
                        this@SolanaTokenSwapBot
                    )
                } else {
                    logger.debug("BOOST: Token validation failed for ${b.tokenAddress}: not valid")
                }
            }
        }
    }

    override fun now(): Long = System.currentTimeMillis()

    override fun isNew(mint: String): Boolean = !stateMap.containsKey(mint)

    override fun status(mint: String): TokenStatus? = stateMap[mint]

    override suspend fun buy(mint: String): Boolean {
        if (config.blockBuy) {
            return false
        }

        logger.info("Attempting BUY: mint=$mint, wallet=${walletConfig.publicKey}")
        stateMap[mint] = TokenStatus(mint, TokenState.TradePending)

        return withContext(Dispatchers.IO) {
            runCatching {
                jupiterSwapService.getQuoteAndPerformSwap(
                    config.solAmountToTrade.toPlainString(),
                    config.swapMint.base58(),
                    mint,
                    walletConfig.publicKey
                )
            }.fold(
                onSuccess = { swap ->
                    logger.info("BUY: Received swap response for $mint")
                    if (swap.swapTransaction == null) {
                        logger.warn("BUY: No swap transaction returned for $mint")
                        stateMap.remove(mint)
                        false
                    } else {
                        if (config.useJito) {
                            try {
                                logger.info("BUY: Creating and enqueueing transaction for Jito")
                                val swapTx = createSwapTransaction(swap.swapTransaction)
                                jitoBundlerService.enqueue(swapTx)
                                stateMap[mint]?.state = TokenState.Swapped
                                managementService?.incrementSuccessfulTrades()
                                logger.info("BUY: Successfully enqueued to Jito for $mint")
                                true
                            } catch (e: Exception) {
                                logger.error("BUY: Failed to create/enqueue transaction for Jito: ${e.message}", e)
                                stateMap[mint]?.state = TokenState.SellFailed("Jito enqueue failed: ${e.message}")
                                managementService?.incrementFailedTrades()
                                false
                            }
                        } else {
                            logger.info("BUY: Executing transaction directly")
                            val success = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                            if (success) {
                                stateMap[mint]?.state = TokenState.Swapped
                                managementService?.incrementSuccessfulTrades()
                                logger.info("BUY: Transaction successful for $mint")
                                true
                            } else {
                                stateMap[mint]?.state = TokenState.SellFailed("Swap failed")
                                managementService?.incrementFailedTrades()
                                logger.warn("BUY: Transaction execution failed for $mint")
                                false
                            }
                        }
                    }
                },
                onFailure = { ex ->
                    logger.warn("BUY exception for $mint: ${ex.message}", ex)
                    stateMap[mint]?.state = TokenState.SellFailed("Swap exception: ${ex.message}")
                    managementService?.incrementFailedTrades()
                    false
                }
            )
        }
    }

    override suspend fun sell(mint: String): Boolean {
        logger.info("Attempting SELL: mint=$mint, wallet=${walletConfig.publicKey}")
        val status = stateMap[mint]

        // Check if token exists in wallet first
        val tokenInfo = tokenInfo(mint)
        if (tokenInfo == null) {
            logger.warn("SELL: Token $mint not found in wallet")
            return false
        }

        if (tokenInfo.tokenAmount.amount == "0") {
            logger.warn("SELL: Token $mint has zero balance")
            return false
        }

        // If token not in state map, add it (for manual sells of existing tokens)
        if (status == null) {
            logger.info("SELL: Adding existing token $mint to state map")
            stateMap[mint] = TokenStatus(mint, TokenState.Swapped)
        } else {
            // Only restrict based on state if token is tracked
            if (status.state != TokenState.Swapped && status.state !is TokenState.SellFailed) {
                logger.warn("SELL: Token $mint is in state ${status.state}, cannot sell")
                // return false
            }
        }
        stateMap[mint] = TokenStatus(mint, TokenState.Selling)
        setLastSell(mint)
        return withContext(Dispatchers.IO) {
            runCatching {
                jupiterSwapService.getQuoteAndPerformSwap(
                    tokenInfo.tokenAmount.amount.toDouble(),
                    mint,
                    config.swapMint.base58(),
                    walletConfig.publicKey
                )
            }.fold(onSuccess = { swap ->
                if (swap.swapTransaction == null) {
                    stateMap[mint]?.state = TokenState.SellFailed("No route")
                    managementService?.incrementFailedTrades()
                    false
                } else {
                    if (config.useJito) {
                        try {
                            logger.info("SELL: Creating and enqueueing transaction for Jito")
                            val swapTx = createSwapTransaction(swap.swapTransaction)
                            jitoBundlerService.enqueue(swapTx)
                            stateMap[mint]?.state = TokenState.Sold
                            managementService?.incrementSuccessfulTrades()
                            logger.info("SELL: Successfully enqueued to Jito for $mint")
                            true
                        } catch (e: Exception) {
                            logger.error("SELL: Failed to create/enqueue transaction for Jito: ${e.message}", e)
                            stateMap[mint]?.state = TokenState.SellFailed("Jito enqueue failed: ${e.message}")
                            managementService?.incrementFailedTrades()
                            false
                        }
                    } else {
                        logger.info("SELL: Executing transaction directly")
                        val sold = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                        if (sold) {
                            stateMap[mint]?.state = TokenState.Sold
                            managementService?.incrementSuccessfulTrades()
                            logger.info("SELL: Transaction successful for $mint")
                            true
                        } else {
                            stateMap[mint]?.state = TokenState.SellFailed("Sell failed")
                            managementService?.incrementFailedTrades()
                            logger.warn("SELL: Transaction execution failed for $mint")
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

    override suspend fun getTokenUsdPrice(mint: String): Double? {
        return try {
            priceService?.getTokenPrice(mint)?.priceUsd
        } catch (e: Exception) {
            logger.warn("Failed to get USD price for token $mint: ${e.message}")
            null
        }
    }

    private fun setLastSell(mint: String) {
        lastSell[mint] = System.currentTimeMillis()
    }

    private fun canSell(mint: String): Boolean {
        val last = lastSell[mint] ?: return true
        return System.currentTimeMillis() - last >= config.sellWaitMs
    }

    private suspend fun closeZeroAccounts() {
        allAccounts()?.filter { it.account.data.parsed.info.tokenAmount.amount == "0" }
            ?.take(config.zeroBalanceCloseBatch)?.let { zeroList ->
                runCatching {
                    val instruction =
                        zeroList.map {
                            createCloseAccountInstruction(
                                PublicKey(it.pubkey),
                                PublicKey(walletConfig.publicKey),
                            )
                        }
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
                        token.tokenAmount.amount.toDouble(),
                        token.address,
                        config.swapMint.base58(),
                        walletConfig.publicKey
                    )
                }.onSuccess { swap ->
                    if (swap.swapTransaction == null) return@onSuccess
                    if (config.useJito) {
                        jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                        // Mark as Sold or keep as Selling depending on your logic
                        stateMap[token.address]?.state = TokenState.Sold
                    } else {
                        val sold =
                            executeSwapTransaction(rpc, swap.swapTransaction, executor)
                        stateMap[token.address]?.state =
                            if (sold) TokenState.Sold
                            else TokenState.SellFailed("Failed selling ${token.address}")
                    }
                }.onFailure {
                    stateMap[token.address]?.state =
                        TokenState.SellFailed("Bulk swap error ${token.address}: ${it.message}")
                }

                // The original code had delay(500) here as a spacing between sells
                // You can remove or make it config-based if you prefer not to hard-code.
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
