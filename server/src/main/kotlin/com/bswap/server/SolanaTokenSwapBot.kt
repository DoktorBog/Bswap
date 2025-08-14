package com.bswap.server

import com.bswap.server.core.GlobalRateLimiter
import com.bswap.server.core.PriceMissTracker
import com.bswap.server.core.RpcRateLimiter
import com.bswap.server.core.SellOrder
import com.bswap.server.core.SellQueue
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
import com.bswap.server.service.JupiterTokensClient
import com.bswap.server.service.WhitelistResolver
import com.bswap.server.stratagy.TradingStrategy
import com.bswap.server.stratagy.TradingStrategyFactory
import com.bswap.server.validation.TokenValidator
import com.bswap.server.validation.ValidationConfig
import com.bswap.shared.wallet.WalletConfig
import foundation.metaplex.rpc.RPC
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
    override val getPriceHistory: (suspend (String) -> List<Double>?)? = null,
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
    private val priceService: com.bswap.server.service.PriceService = com.bswap.server.service.PriceService(
        httpClient = client,
        dexScreenerClient = com.bswap.server.data.dexscreener.DexScreenerClientImpl(client),
        jupiterSwapService = jupiterSwapService
    )
) : TradingRuntime {
    private val strategy: TradingStrategy = TradingStrategyFactory.create(config.strategySettings)
    private var processingTokens = AtomicInteger(0)
    private val stateMap = ConcurrentHashMap<String, TokenStatus>()
    private val lastSell = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isActive = AtomicBoolean(false)

    // New components
    private val whitelistResolver = if (config.whitelist.enabled) {
        WhitelistResolver(JupiterTokensClient(client), config.whitelist.symbols)
    } else null

    // Removed multi-RPC pool - using single RPC from config via Constants.RPC_URL

    private val priceMissTracker = PriceMissTracker(config.priceService)

    private lateinit var sellQueue: SellQueue


    fun start() {
        if (_isActive.get()) return
        _isActive.set(true)
        logger.info("Bot starting. Wallet: ${walletConfig.publicKey}, UseJito=${config.useJito}, SOL/Trade=${config.solAmountToTrade}")

        // First, clean up existing wallet tokens
        scope.launch {
            try {
                cleanupWalletOnStartup()
            } catch (e: Exception) {
                logger.error("Failed to cleanup wallet on startup: ${e.message}", e)
            }
        }

        // Initialize whitelist
        whitelistResolver?.let { resolver ->
            scope.launch {
                try {
                    resolver.refresh()
                    logger.info("Whitelist initialized with ${resolver.getCacheSize()} tokens")
                } catch (e: Exception) {
                    logger.error("Failed to initialize whitelist: ${e.message}", e)
                }
            }
        }

        // Initialize sell queue
        if (config.sellQueue.enabled) {
            sellQueue = SellQueue(
                workerScope = scope,
                sellHandler = { order -> sell(order.mint) },
                maxConcurrency = config.sellQueue.maxConcurrency,
                spacingMs = config.sellQueue.spacingMs,
                retryCount = config.sellQueue.retryCount,
                retryDelayMs = config.sellQueue.retryDelayMs
            )
            sellQueue.start()
        }

        // Persistent sell queue removed - using direct sells only

        // Start background tasks
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

        // Start price miss monitoring
        scope.launch {
            while (_isActive.get()) {
                checkPriceMisses()
                priceMissTracker.cleanup()
                delay(10_000) // Check every 10 seconds
            }
        }

        logger.info("SolanaTokenSwapBot started with strategy ${strategy.type}")
    }

    fun stop() {
        _isActive.set(false)

        // Stop sell queue
        if (::sellQueue.isInitialized && config.sellQueue.enabled) {
            sellQueue.stop()
        }

        // Persistent sell queue removed - no stop needed

        logger.info("SolanaTokenSwapBot stopped")
    }

    fun isActive(): Boolean = _isActive.get()

    fun getCurrentState(): Map<String, TokenStatus> = stateMap.toMap()

    fun getActiveTokensCount(): Int = stateMap.size

    fun singleTrade(mint: String) = scope.launch(Dispatchers.IO) { buy(mint) }

    fun sellOneToken(mint: String) = scope.launch(Dispatchers.IO) { sell(mint) }

    fun observeProfiles(flow: Flow<List<TokenProfile>>) = scope.launch {
        flow.collect { list ->
            if (!_isActive.get()) {
                logger.debug("🛑 Bot not active, skipping ${list.size} profiles")
                return@collect
            }

            val solanaProfiles = list.filter { it.chainId == "solana" }
            logger.info("📋 Processing ${solanaProfiles.size} Solana token profiles")

            solanaProfiles.forEach { p ->
                logger.info("🔍 Discovered PROFILE token: ${p.tokenAddress}")

                // Check whitelist first
                if (whitelistResolver != null && !whitelistResolver.isAllowed(p.tokenAddress)) {
                    logger.info("⚠️ Skip ${p.tokenAddress}: not in whitelist")
                    return@forEach
                }

                // Validate token before passing to strategy
                logger.info("✅ Validating token: ${p.tokenAddress}")
                val validationResult = withContext(Dispatchers.IO) {
                    tokenValidator.validateToken(p.tokenAddress)
                }
                if (validationResult.isValid) {
                    logger.info("🎯 PROFILE token passed validation: ${p.tokenAddress} - sending to strategy")
                    strategy.onDiscovered(
                        TokenMeta(p.tokenAddress, TokenSource.PROFILE, profile = p),
                        this@SolanaTokenSwapBot
                    )
                } else {
                    logger.warn("❌ PROFILE: Token validation failed for ${p.tokenAddress}: ${validationResult.reasons}")
                }
            }
        }
    }

    fun observePumpFun(flow: Flow<TokenTradeResponse>) = scope.launch {
        flow.debounce(2000).collect { t ->
            if (!_isActive.get()) {
                logger.debug("🛑 Bot not active, skipping PumpFun token ${t.mint}")
                return@collect
            }

            logger.info("🚀 Discovered PUMPFUN token: ${t.mint}")

            // Check whitelist first
            if (whitelistResolver != null && !whitelistResolver.isAllowed(t.mint)) {
                logger.info("⚠️ Skip ${t.mint}: not in whitelist")
                return@collect
            }

            // Validate token before passing to strategy
            logger.info("✅ Validating PumpFun token: ${t.mint}")
            val validationResult = withContext(Dispatchers.IO) {
                tokenValidator.validateToken(t.mint)
            }
            if (validationResult.isValid) {
                logger.info("🎯 PUMPFUN token passed validation: ${t.mint} - sending to strategy")
                strategy.onDiscovered(TokenMeta(t.mint, TokenSource.PUMPFUN, pump = t), this@SolanaTokenSwapBot)
            } else {
                logger.warn("❌ PUMPFUN: Token validation failed for ${t.mint}: ${validationResult.reasons}")
            }
        }
    }

    fun observeBoosted(flow: Flow<List<TokenBoost>>) = scope.launch {
        flow.sample(1_000).collect { list ->
            if (!_isActive.get()) return@collect
            list.filter { it.chainId == "solana" }.shuffled().take(5).forEach { b ->
                // Check whitelist first
                if (whitelistResolver != null && !whitelistResolver.isAllowed(b.tokenAddress)) {
                    logger.debug("Skip ${b.tokenAddress}: not in whitelist")
                    return@forEach
                }

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
        logger.info("🔥 BUY ATTEMPT: $mint")

        if (config.blockBuy) {
            logger.warn("❌ BUY BLOCKED: blockBuy=true for $mint")
            return false
        }

        // Check if we should allow buy without price
        if (!config.priceService.allowBuyWithoutPrice) {
            logger.info("🔍 Checking price for $mint (allowBuyWithoutPrice=false)")
            val price = getTokenUsdPrice(mint)
            if (price == null) {
                logger.warn("❌ BUY BLOCKED for $mint: no price available and allowBuyWithoutPrice=false")
                return false
            } else {
                logger.info("✅ Price found for $mint: $$price")
            }
        } else {
            logger.info("⚡ Skipping price check for $mint (allowBuyWithoutPrice=true)")
        }

        logger.info("🚀 EXECUTING BUY: mint=$mint, wallet=${walletConfig.publicKey}")
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
                    // Test Jito with immediate flush
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
            val price = priceService.getTokenPrice(mint)?.priceUsd
            if (price != null) {
                priceMissTracker.recordPriceSuccess(mint)
            } else {
                priceMissTracker.recordPriceMiss(mint)
            }
            price
        } catch (e: Exception) {
            logger.warn("Failed to get USD price for token $mint: ${e.message}")
            priceMissTracker.recordPriceMiss(mint)
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
                        try {
                            logger.info("BULK SELL: Creating and enqueueing transaction for Jito - ${token.address}")
                            val swapTx = createSwapTransaction(swap.swapTransaction)
                            jitoBundlerService.enqueue(swapTx)
                            stateMap[token.address]?.state = TokenState.Sold
                            logger.info("BULK SELL: Successfully enqueued to Jito for ${token.address}")
                        } catch (e: Exception) {
                            logger.error("BULK SELL: Failed to create/enqueue transaction for Jito - ${token.address}: ${e.message}", e)
                            stateMap[token.address]?.state = TokenState.SellFailed("Jito enqueue failed: ${e.message}")
                        }
                    } else {
                        try {
                            logger.info("BULK SELL: Executing transaction directly via RPC - ${token.address}")
                            val sold = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                            stateMap[token.address]?.state = if (sold) TokenState.Sold else TokenState.SellFailed("RPC execution failed")
                            logger.info("BULK SELL: ${if (sold) "Successfully executed" else "Failed to execute"} via RPC for ${token.address}")
                        } catch (e: Exception) {
                            logger.error("BULK SELL: Failed to execute via RPC - ${token.address}: ${e.message}", e)
                            stateMap[token.address]?.state = TokenState.SellFailed("RPC execution failed: ${e.message}")
                        }
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

    /**
     * Check for tokens with missing prices and force sell if needed
     */
    private suspend fun checkPriceMisses() {
        val swappedTokens = stateMap.filterValues { it.state == TokenState.Swapped }.keys

        for (mint in swappedTokens) {
            if (priceMissTracker.shouldForceSell(mint)) {
                logger.warn("EMERGENCY_SELL $mint reason=no-price-fallback")

                // Always use direct sell - no queue
                scope.launch(Dispatchers.IO) {
                    sell(mint)
                }
            }
        }
    }


    /**
     * Clean up wallet on startup by selling all existing tokens except SOL
     */
    private suspend fun cleanupWalletOnStartup() {
        logger.info("🧹 Starting wallet cleanup - selling all existing tokens...")

        try {
            val existingTokens = allTokens()
                .filterNot { it.address == config.swapMint.base58() } // Keep SOL
                .filter {
                    val amount = it.tokenAmount.uiAmount ?: 0.0
                    amount > 0.0 // Only tokens with positive balance
                }

            if (existingTokens.isEmpty()) {
                logger.info("✅ Wallet is already clean - no tokens to sell")
                return
            }

            logger.info("🎯 Found ${existingTokens.size} existing tokens to sell before starting discovery")

            existingTokens.forEach { token ->
                try {
                    logger.info("💰 Startup cleanup: Selling existing token ${token.address} (balance: ${token.tokenAmount.uiAmount})")

                    // Add to state map for tracking
                    stateMap[token.address] = TokenStatus(token.address, TokenState.Swapped)

                    // Execute sell directly (don't use queue during startup)
                    val success = sell(token.address)
                    if (success) {
                        logger.info("✅ Successfully queued ${token.address} for sale")
                    } else {
                        logger.warn("⚠️ Failed to sell ${token.address} during startup cleanup")
                    }

                    // Minimal delay between sells
                    delay(100)

                } catch (e: Exception) {
                    logger.error("❌ Error selling token ${token.address} during startup: ${e.message}", e)
                }
            }

            // Short wait for sales to process before allowing new discovery
            logger.info("⏱️ Waiting 3 seconds for startup sells to process...")
            delay(3_000)

            logger.info("🎉 Wallet cleanup completed - bot ready for new token discovery")

        } catch (e: Exception) {
            logger.error("❌ Critical error during wallet cleanup: ${e.message}", e)
            // Don't fail startup entirely, just log the error
        }
    }

    /**
     * Get diagnostic information about the bot state
     */
    suspend fun getDiagnostics(): Map<String, Any> {
        return mapOf(
            "isActive" to _isActive.get(),
            "activeTokensCount" to stateMap.size,
            "processingTokens" to processingTokens.get(),
            "whitelistEnabled" to (whitelistResolver != null),
            "whitelistSize" to (whitelistResolver?.getCacheSize() ?: 0),
            "sellQueueEnabled" to (config.sellQueue.enabled && ::sellQueue.isInitialized),
            "enhancedSellQueueEnabled" to true,
            "rpcUrl" to RPC_URL,
            "priceMissStats" to priceMissTracker.getStats(),
            "priceServiceStats" to priceService.getCacheStats()
        )
    }
}
