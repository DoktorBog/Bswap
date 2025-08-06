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
import com.bswap.server.data.solana.transaction.createTransactionWithInstructions
import com.bswap.server.data.solana.transaction.executeSolTransaction
import com.bswap.server.data.solana.transaction.executeSwapTransaction
import com.bswap.server.data.solana.transaction.getTokenAccountsByOwner
import com.bswap.server.validation.TokenValidator
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

private val logger = LoggerFactory.getLogger("SolanaTokenSwapBot")

/** Our token states, including a new TradePending state. */
sealed interface TokenState {
    /** Newly queued token for swapping; we haven't completed the Jupiter swap yet. */
    data object TradePending : TokenState

    /** The token was successfully swapped from SOL into the SPL token. */
    data object Swapped : TokenState

    /** We are in the process of selling/swapping from the SPL token back to SOL. */
    data object Selling : TokenState

    /** The token was successfully sold back to SOL. */
    data object Sold : TokenState

    /** A failure state for any reason (swap, sell, etc.). */
    data class SellFailed(val reason: String) : TokenState
}

/**
 * Track a token's address, current state, and when it was created
 * (helps us time out if still pending after N seconds).
 */
data class TokenStatus(
    val tokenAddress: String,
    var state: TokenState,
    val createdAt: Long = System.currentTimeMillis()
)

@OptIn(FlowPreview::class)
class SolanaTokenSwapBot(
    private val config: SolanaSwapBotConfig = SolanaSwapBotConfig(),
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
    private val tokenValidator: TokenValidator = TokenValidator(client)
) {
    private var processingTokens = AtomicInteger(0)
    private val stateMap = ConcurrentHashMap<String, TokenStatus>()
    private val lastSell = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isActive = AtomicBoolean(true)

    fun start() {
        if (_isActive.get()) return
        _isActive.set(true)

        // 1) Periodically sell all SPL tokens
        if (config.autoSellAllSpl) {
            scope.launch {
                while (_isActive.get()) {
                    sellAllOnce()
                    delay(config.sellAllSplIntervalMs)
                }
            }
        }

        // 2) Periodically close zero-balance accounts
        scope.launch {
            while (_isActive.get()) {
                closeZeroAccounts()
                delay(config.closeAccountsIntervalMs)
            }
        }

        // 3) Periodically remove tokens that have been TradePending > 30 seconds
        scope.launch {
            while (_isActive.get()) {
                delay(5_000) // check every 5 seconds
                clearUnboughtCoins()
            }
        }

        logger.info("SolanaTokenSwapBot started")
    }

    fun stop() {
        _isActive.set(false)
        logger.info("SolanaTokenSwapBot stopped")
    }

    fun isActive(): Boolean = _isActive.get()

    fun getCurrentState(): Map<String, TokenStatus> = stateMap.toMap()

    fun getActiveTokensCount(): Int = stateMap.size

    /** Let external callers request a single trade. */
    fun singleTrade(mint: String) = trade(mint)

    /** Observe DexScreener-provided token profiles. */
    fun observeProfiles(flow: Flow<List<TokenProfile>>) = scope.launch {
        flow.collect { list ->
            list.filter { it.chainId == "solana" }
                .forEach { p ->
                    if (isNew(p.tokenAddress)) {
                        // The user’s original code had a delay(1_000) here.
                        // If you prefer no hard-coded delay, remove or make it configurable.
                        delay(1_000)
                        setLastSell(p.tokenAddress)
                        trade(p.tokenAddress)
                    }
                }
        }
    }

    /** Observe PumpFun stream (TokenTradeResponse). */
    fun observePumpFun(flow: Flow<TokenTradeResponse>) = scope.launch {
        flow.debounce(2000).collect {
            if (isNew(it.mint) && isTokenValid(it.mint)) {
                setLastSell(it.mint)
                trade(it.mint)
            }
        }
    }

    /** Observe your “boosted” tokens from DexScreener or other source. */
    fun observeBoosted(flow: Flow<List<TokenBoost>>) = scope.launch {
        flow.sample(1_000).collect { list ->
            list.filter { it.chainId == "solana" }
                .shuffled()
                .take(5)
                .forEach { b ->
                    if (isNew(b.tokenAddress)) {
                        delay(2_000)
                        setLastSell(b.tokenAddress)
                        trade(b.tokenAddress)
                    }
                }
        }
    }

    /**
     * Initiate a trade from SOL -> new token. Mark it as TradePending.
     * If the Jupiter swap transaction is successful, mark as Swapped.
     * If it fails or no route, remove from map or mark as SellFailed.
     */
    private fun trade(mint: String) {
        logger.info("Trade mint = $mint")
        processingTokens.incrementAndGet()

        // Mark as pending initially
        stateMap[mint] = TokenStatus(mint, TokenState.TradePending)

        scope.launch(Dispatchers.IO) {
            // First validate the token
            val validationResult = tokenValidator.validateToken(mint)
            if (!validationResult.isValid) {
                logger.warn("Token $mint failed validation: ${validationResult.reasons.joinToString(", ")}")
                stateMap[mint]?.state = TokenState.SellFailed("Validation failed: ${validationResult.reasons.firstOrNull() ?: "Invalid token"}")
                managementService?.incrementFailedTrades()
                return@launch
            }

            if (validationResult.riskScore > 0.7) {
                logger.warn("Token $mint has high risk score: ${validationResult.riskScore}")
                stateMap[mint]?.state = TokenState.SellFailed("High risk token: risk score ${validationResult.riskScore}")
                managementService?.incrementFailedTrades()
                return@launch
            }
            runCatching {
                // Attempt the Jupiter swap
                jupiterSwapService.getQuoteAndPerformSwap(
                    config.solAmountToTrade.toPlainString(),
                    config.swapMint.base58(),
                    mint,
                    config.walletPublicKey.base58()
                )
            }.onSuccess { swap ->
                // If Jupiter provided no instructions, remove it from map
                if (swap.swapTransaction == null) {
                    logger.warn("No instructions from Jupiter for $mint. Removing from map.")
                    stateMap.remove(mint)
                    return@launch
                }

                // We have a valid transaction -> either enqueue to Jito or execute immediately
                if (config.useJito) {
                    jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                    // We won't know success/fail until Jito executes it, so you may want to
                    // keep the state as TradePending or set it to Swapped if you're confident
                    // Jito will handle it. Example:
                    stateMap[mint]?.state = TokenState.Swapped
                    managementService?.incrementSuccessfulTrades()
                } else {
                    val success = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                    if (success) {
                        stateMap[mint]?.state = TokenState.Swapped
                        managementService?.incrementSuccessfulTrades()
                    } else {
                        stateMap[mint]?.state =
                            TokenState.SellFailed("Transaction failed for $mint")
                        managementService?.incrementFailedTrades()
                    }
                }
            }.onFailure { ex ->
                logger.error("Swap error for $mint: ${ex.message}")
                stateMap[mint]?.state = TokenState.SellFailed("Swap exception: ${ex.message}")
                managementService?.incrementFailedTrades()
            }
        }
    }

    /**
     * Sell a single token on request (token minted -> convert back to SOL).
     * Typically used for quick manual sells.
     */
    fun sellOneToken(mint: String) = scope.launch(Dispatchers.IO) {
        val status = stateMap[mint] ?: return@launch
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
                jupiterSwapService.getQuoteAndPerformSwap(
                    info.tokenAmount.amount,
                    mint,
                    config.swapMint.base58(),
                    config.walletPublicKey.base58()
                )
            }.onSuccess { swap ->
                if (swap.swapTransaction == null) {
                    stateMap[mint]?.state = TokenState.SellFailed("No route/instructions for $mint")
                    return@onSuccess
                }
                if (config.useJito) {
                    jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                    // same note: either keep it as Selling, or mark it Sold preemptively
                    stateMap[mint]?.state = TokenState.Sold
                    managementService?.incrementSuccessfulTrades()
                } else {
                    val sold = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                    if (sold) {
                        stateMap[mint]?.state = TokenState.Sold
                        managementService?.incrementSuccessfulTrades()
                    } else {
                        stateMap[mint]?.state =
                            TokenState.SellFailed("Transaction failed for $mint")
                        managementService?.incrementFailedTrades()
                    }
                }
            }.onFailure {
                stateMap[mint]?.state = TokenState.SellFailed("Swap error for $mint: ${it.message}")
                managementService?.incrementFailedTrades()
            }
        }
    }

    // --- Helpers / housekeeping ---

    private fun isNew(mint: String) = !stateMap.containsKey(mint)

    private fun setLastSell(mint: String) {
        lastSell[mint] = System.currentTimeMillis()
    }

    /**
     * Whether enough time (sellWaitMs) has passed to allow us to re-sell or do an operation again
     * on this token.
     */
    private fun canSell(mint: String): Boolean {
        val last = lastSell[mint] ?: 0L
        return System.currentTimeMillis() - last >= config.sellWaitMs
    }

    /**
     * Close zero-balance accounts in batches, triggered on a config-based interval.
     */
    private suspend fun closeZeroAccounts() {
        allAccounts()?.filter {
            it.account.data.parsed.info.tokenAmount.amount == "0"
        }?.take(config.zeroBalanceCloseBatch)?.let { zeroList ->
            runCatching {
                val instruction = zeroList.map {
                    createCloseAccountInstruction(PublicKey(it.pubkey), config.walletPublicKey)
                }
                if (config.useJito) {
                    jitoBundlerService.enqueue(createTransactionWithInstructions(instruction).serialize())
                } else {
                    executeSolTransaction(
                        rpc,
                        instruction
                    )
                }
            }
        }
    }

    /**
     * Sell all SPL tokens (except SOL) in a batch, triggered on a config-based interval.
     */
    private suspend fun sellAllOnce() {
        allTokens()
            ?.filterNot { it.mint == config.swapMint.base58() || it.tokenAmount.amount == "0" }
            ?.filter { canSell(it.mint) }
            ?.take(config.splSellBatch)
            ?.forEach { token ->
                stateMap[token.mint] = TokenStatus(token.mint, TokenState.Selling)
                setLastSell(token.mint)

                runCatching {
                    jupiterSwapService.getQuoteAndPerformSwap(
                        token.tokenAmount.amount,
                        token.mint,
                        config.swapMint.base58(),
                        config.walletPublicKey.base58()
                    )
                }.onSuccess { swap ->
                    if (swap.swapTransaction == null) return@onSuccess
                    if (config.useJito) {
                        jitoBundlerService.enqueue(createSwapTransaction(swap.swapTransaction))
                        // Mark as Sold or keep as Selling depending on your logic
                        stateMap[token.mint]?.state = TokenState.Sold
                    } else {
                        val sold =
                            executeSwapTransaction(rpc, swap.swapTransaction, executor)
                        stateMap[token.mint]?.state =
                            if (sold) TokenState.Sold
                            else TokenState.SellFailed("Failed selling ${token.mint}")
                    }
                }.onFailure {
                    stateMap[token.mint]?.state =
                        TokenState.SellFailed("Bulk swap error ${token.mint}: ${it.message}")
                }

                // The original code had delay(500) here as a spacing between sells
                // You can remove or make it config-based if you prefer not to hard-code.
                delay(500)
            }
    }

    /** Fetch the token info for a single mint, or null if not found. */
    private suspend fun fetchSingleTokenInfo(mint: String): TokenInfo? {
        return allTokens()?.firstOrNull { it.mint == mint }
    }

    /** Get all token accounts for the owner. */
    private suspend fun allAccounts() = runCatching {
        // Replace `client` with your actual network client if needed
        getTokenAccountsByOwner(NetworkDriver(client), config.walletPublicKey)
    }.getOrNull().also {
        processingTokens.set(it?.size ?: 0)
    }

    /** Get all token info from those accounts. */
    private suspend fun allTokens() = allAccounts()?.map { it.account.data.parsed.info }

    /**
     * Clear tokens that are still TradePending after 30 seconds
     */
    private suspend fun clearUnboughtCoins() {
        val now = System.currentTimeMillis()
        val expiredMints = stateMap.filterValues { status ->
            // still pending
            status.state is TokenState.TradePending &&
                    // older than 30 seconds
                    now - status.createdAt >= 30_000
        }.keys

        if (expiredMints.isNotEmpty()) {
            expiredMints.forEach { mint ->
                logger.warn("Removing $mint from stateMap (TradePending > 30s).")
                stateMap.remove(mint)
            }
        }
    }
}
