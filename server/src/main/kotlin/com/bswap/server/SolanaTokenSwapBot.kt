package com.bswap.server

import com.bswap.server.data.dexscreener.models.TokenBoost
import com.bswap.server.data.dexscreener.models.TokenProfile
import com.bswap.server.data.solana.jito.JitoBundlerService
import com.bswap.server.data.solana.pumpfun.TokenTradeResponse
import com.bswap.server.data.solana.pumpfun.isTokenValid
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.solana.transaction.DefaultTransactionExecutor
import com.bswap.server.data.solana.transaction.TokenInfo
import com.bswap.server.data.solana.transaction.createCloseAccountInstruction
import com.bswap.server.data.solana.transaction.createSwapTransaction
import com.bswap.server.data.solana.transaction.executeSolTransaction
import com.bswap.server.data.solana.transaction.executeSwapTransaction
import com.bswap.server.data.solana.transaction.getTokenAccountsByOwner
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.networking.NetworkDriver
import foundation.metaplex.solanapublickeys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed interface TokenState {
    data object Swapped : TokenState
    data object Selling : TokenState
    data object Sold : TokenState
    data class SellFailed(val reason: String) : TokenState
}

data class TokenStatus(val tokenAddress: String, var state: TokenState)

data class SolanaSwapBotConfig(
    val rpc: RPC,
    val jupiterSwapService: JupiterSwapService,
    val walletPublicKey: PublicKey = PublicKey(""),
    val swapMint: PublicKey = PublicKey("So11111111111111111111111111111111111111112"),
    val solAmountToTrade: BigDecimal = BigDecimal("0.0001"),
    val autoSellAllSpl: Boolean = true,
    val maxKnownTokens: Int = 3,
    val sellWaitMs: Long = 35_000,
    val zeroBalanceCloseBatch: Int = 9,
    val splSellBatch: Int = 10,
    val closeAccountsIntervalMs: Long = 60_000,
    val sellAllSplIntervalMs: Long = 10_000,
    val clearMapIntervalMs: Long = 60_000 * 60,
    val useJito: Boolean = false
)

@OptIn(FlowPreview::class)
class SolanaTokenSwapBot(
    private val config: SolanaSwapBotConfig,
    private val executor: DefaultTransactionExecutor = DefaultTransactionExecutor(config.rpc),
    private val jitoBundlerService: JitoBundlerService? = null
) {
    private var processingTokens = AtomicInteger(0)
    private val stateMap = ConcurrentHashMap<String, TokenStatus>()
    private val lastSell = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        if (config.autoSellAllSpl) scope.launch {
            while (isActive) {
                sellAllOnce()
                delay(config.sellAllSplIntervalMs)
            }
        }
        scope.launch {
            while (isActive) {
                closeZeroAccounts()
                delay(config.closeAccountsIntervalMs)
            }
        }
        scope.launch {
            while (isActive) {
                delay(config.clearMapIntervalMs)
                stateMap.clear()
            }
        }
    }

    fun singleTrade(mint: String) = trade(mint)

    fun observeProfiles(flow: Flow<List<TokenProfile>>) = scope.launch {
        flow.collect { list ->
            list.filter { it.chainId == "solana" }
                .shuffled().take(5).forEach { p ->
                    if (isNew(p.tokenAddress)) {
                        delay(1_000)
                        setLastSell(p.tokenAddress)
                        trade(p.tokenAddress)
                    }
                }
        }
    }

    fun observePumpFun(flow: Flow<TokenTradeResponse>) = scope.launch {
        flow.sample(5000).collect {
            if (isNew(it.mint)) {
                if (!isTokenValid(it.mint)) return@collect
                setLastSell(it.mint)
                trade(it.mint)
            }
        }
    }

    fun observeBoosted(flow: Flow<List<TokenBoost>>) = scope.launch {
        flow.sample(1_000).collect { list ->
            list.filter { it.chainId == "solana" }
                .shuffled().take(5).forEach { b ->
                    if (isNew(b.tokenAddress)) {
                        delay(2_000)
                        setLastSell(b.tokenAddress)
                        trade(b.tokenAddress)
                    }
                }
        }
    }

    private fun trade(mint: String) {
        processingTokens.incrementAndGet()
        stateMap[mint] = TokenStatus(mint, TokenState.Swapped)
        scope.launch(Dispatchers.IO) {
            runCatching {
                config.jupiterSwapService.getQuoteAndPerformSwap(
                    config.solAmountToTrade,
                    config.swapMint.base58(),
                    mint
                )
            }.onSuccess { swap ->
                if (swap.swapTransaction == null) return@launch
                if (config.useJito && jitoBundlerService != null) {
                    jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                } else {
                    if (executeSwapTransaction(config.rpc, swap.swapTransaction, executor)) {
                        stateMap[mint]?.state = TokenState.Swapped
                    }
                }
            }.onFailure {
                stateMap.remove(mint)
            }
        }
    }

    fun sellOneToken(mint: String) = scope.launch(Dispatchers.IO) {
        stateMap[mint]?.let { status ->
            if (status.state == TokenState.Swapped || status.state is TokenState.SellFailed) {
                stateMap[mint] = TokenStatus(mint, TokenState.Selling)
                val info = fetchSingleTokenInfo(mint) ?: run {
                    stateMap[mint]?.state = TokenState.SellFailed("No token info found for $mint")
                    return@launch
                }
                if (info.tokenAmount.amount == "0") {
                    stateMap[mint]?.state = TokenState.SellFailed("Token balance is zero for $mint")
                    return@launch
                }
                setLastSell(mint)
                runCatching {
                    config.jupiterSwapService.getQuoteAndPerformSwap(
                        info.tokenAmount.amount,
                        mint,
                        config.swapMint.base58()
                    )
                }.onSuccess { swap ->
                    if (swap.swapTransaction == null) return@onSuccess
                    if (config.useJito && jitoBundlerService != null) {
                        jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                    } else {
                        val sold =
                            executeSwapTransaction(config.rpc, swap.swapTransaction, executor)
                        if (sold) {
                            stateMap[mint]?.state = TokenState.Sold
                        } else {
                            stateMap[mint]?.state =
                                TokenState.SellFailed("Transaction failed for $mint")
                        }
                    }
                }.onFailure {
                    stateMap[mint]?.state =
                        TokenState.SellFailed("Swap error for $mint: ${it.message}")
                }
            }
        }
    }

    private fun isNew(mint: String) =
        processingTokens.get() < config.maxKnownTokens && !stateMap.containsKey(mint)

    private fun setLastSell(mint: String) {
        lastSell[mint] = System.currentTimeMillis()
    }

    private fun canSell(mint: String) =
        System.currentTimeMillis() - (lastSell[mint] ?: 0L) >= config.sellWaitMs

    private suspend fun closeZeroAccounts() {
        allAccounts()?.filter {
            it.account.data.parsed.info.tokenAmount.amount == "0"
        }?.take(config.zeroBalanceCloseBatch)?.let { zeroList ->
            runCatching {
                executeSolTransaction(
                    config.rpc,
                    zeroList.map {
                        createCloseAccountInstruction(PublicKey(it.pubkey), config.walletPublicKey)
                    }
                )
            }
        }
    }

    private suspend fun sellAllOnce() {
        allTokens()?.filterNot {
            it.mint == config.swapMint.base58() || it.tokenAmount.amount == "0"
        }?.filter { canSell(it.mint) }
            ?.take(config.splSellBatch)?.forEach { token ->
                stateMap[token.mint] = TokenStatus(token.mint, TokenState.Selling)
                setLastSell(token.mint)
                runCatching {
                    config.jupiterSwapService.getQuoteAndPerformSwap(
                        token.tokenAmount.amount,
                        token.mint,
                        config.swapMint.base58()
                    )
                }.onSuccess { swap ->
                    if (swap.swapTransaction == null) return@onSuccess
                    if (config.useJito && jitoBundlerService != null) {
                        jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                    } else {
                        val sold =
                            executeSwapTransaction(config.rpc, swap.swapTransaction, executor)
                        if (sold) {
                            stateMap[token.mint]?.state = TokenState.Sold
                        } else {
                            stateMap[token.mint]?.state =
                                TokenState.SellFailed("Failed selling ${token.mint}")
                        }
                    }
                }.onFailure {
                    stateMap[token.mint]?.state =
                        TokenState.SellFailed("Bulk swap error ${token.mint}: ${it.message}")
                }
                delay(500)
            }
    }

    private suspend fun fetchSingleTokenInfo(mint: String): TokenInfo? {
        return allTokens()?.firstOrNull { it.mint == mint }
    }

    private suspend fun allAccounts() = runCatching {
        getTokenAccountsByOwner(NetworkDriver(client), config.walletPublicKey)
    }.getOrNull().also {
        processingTokens.set(it?.size ?: 0)
    }

    private suspend fun allTokens() = allAccounts()?.map { it.account.data.parsed.info }
}