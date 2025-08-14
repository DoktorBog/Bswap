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
import com.bswap.server.service.PriceHistoryLoader
import com.bswap.server.service.WhitelistResolver
import com.bswap.server.service.RsiWhitelistSource
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
    
    // Initialize price history loader for RSI strategy
    private val priceHistoryLoader = PriceHistoryLoader(
        httpClient = client,
        dexScreenerClient = com.bswap.server.data.dexscreener.DexScreenerClientImpl(client)
    )
    
    override val getPriceHistory: (suspend (String) -> List<Double>?) = { mint ->
        priceHistoryLoader.loadPriceHistory(mint)
    }
    
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
    
    // Simple whitelist for token trading
    private val rsiWhitelistSource = RsiWhitelistSource()

    // Removed multi-RPC pool - using single RPC from config via Constants.RPC_URL

    private val priceMissTracker = PriceMissTracker(config.priceService)

    private lateinit var sellQueue: SellQueue


    fun start() {
        if (_isActive.get()) return
        _isActive.set(true)
        logger.info("🤖 Bot started: Wallet=${walletConfig.publicKey}, Strategy=${strategy.type}")

        // First, clean up existing wallet tokens
        scope.launch {
            try {
                cleanupWalletOnStartup()
            } catch (e: Exception) {
                logger.error("❌ Wallet cleanup failed: ${e.message}", e)
            }
        }

        // Initialize whitelist
        whitelistResolver?.let { resolver ->
            scope.launch {
                try {
                    resolver.refresh()
                } catch (e: Exception) {
                    logger.error("❌ Whitelist init failed: ${e.message}", e)
                }
            }
        }
        
        // Start whitelist token observation
        observeWhitelistTokens(rsiWhitelistSource)
        logger.info("✅ Whitelist token observation started for ${strategy.type} strategy")

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

        // Start price miss monitoring and cache cleanup
        scope.launch {
            while (_isActive.get()) {
                checkPriceMisses()
                priceMissTracker.cleanup()
                priceHistoryLoader.cleanupCache() // Clean up expired price history cache
                delay(10_000) // Check every 10 seconds
            }
        }

    }

    fun stop() {
        _isActive.set(false)

        // Stop sell queue
        if (::sellQueue.isInitialized && config.sellQueue.enabled) {
            sellQueue.stop()
        }


        // Persistent sell queue removed - no stop needed

    }

    fun isActive(): Boolean = _isActive.get()

    fun getCurrentState(): Map<String, TokenStatus> = stateMap.toMap()

    fun getActiveTokensCount(): Int = stateMap.size

    fun singleTrade(mint: String) = scope.launch(Dispatchers.IO) { buy(mint) }

    fun sellOneToken(mint: String) = scope.launch(Dispatchers.IO) { sell(mint) }

    fun observeProfiles(flow: Flow<List<TokenProfile>>) = scope.launch {
        flow.collect { list ->
            if (!_isActive.get()) return@collect

            val solanaProfiles = list.filter { it.chainId == "solana" }

            solanaProfiles.forEach { p ->

                // Check whitelist first
                if (whitelistResolver != null && !whitelistResolver.isAllowed(p.tokenAddress)) {
                    return@forEach
                }

                // Validate token before passing to strategy
                val validationResult = withContext(Dispatchers.IO) {
                    tokenValidator.validateToken(p.tokenAddress)
                }
                if (validationResult.isValid) {
                    logger.info("✅ PROFILE validation passed: ${p.tokenAddress}")
                    strategy.onDiscovered(
                        TokenMeta(p.tokenAddress, TokenSource.PROFILE, profile = p),
                        this@SolanaTokenSwapBot
                    )
                } else {
                    logger.info("❌ PROFILE validation failed: ${p.tokenAddress} - ${validationResult.reasons}")
                }
            }
        }
    }

    fun observePumpFun(flow: Flow<TokenTradeResponse>) = scope.launch {
        flow.debounce(2000).collect { t ->
            if (!_isActive.get()) return@collect

            // Check whitelist first
            if (whitelistResolver != null && !whitelistResolver.isAllowed(t.mint)) {
                return@collect
            }

            // Validate token before passing to strategy
            val validationResult = withContext(Dispatchers.IO) {
                tokenValidator.validateToken(t.mint)
            }
            if (validationResult.isValid) {
                logger.info("✅ PUMPFUN validation passed: ${t.mint}")
                strategy.onDiscovered(TokenMeta(t.mint, TokenSource.PUMPFUN, pump = t), this@SolanaTokenSwapBot)
            } else {
                logger.info("❌ PUMPFUN validation failed: ${t.mint} - ${validationResult.reasons}")
            }
        }
    }

    fun observeBoosted(flow: Flow<List<TokenBoost>>) = scope.launch {
        flow.sample(1_000).collect { list ->
            if (!_isActive.get()) return@collect
            list.filter { it.chainId == "solana" }.shuffled().take(5).forEach { b ->
                // Check whitelist first
                if (whitelistResolver != null && !whitelistResolver.isAllowed(b.tokenAddress)) {
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
                }
            }
        }
    }

    /**
     * Observe whitelist tokens and pass them to current strategy for trading
     * This allows any strategy (RSI, etc) to trade only whitelisted tokens
     */
    fun observeWhitelistTokens(whitelistSource: RsiWhitelistSource) = scope.launch {
        logger.info("🎯 Starting whitelist token observation for strategy: ${strategy.type}")
        
        // Monitor whitelist tokens periodically
        while (_isActive.get()) {
            try {
                val whitelistedTokens = whitelistSource.getWhitelistedTokens()
                logger.info("📊 Checking ${whitelistedTokens.size} whitelisted tokens")
                
                // For each whitelisted token, create a TokenMeta and pass to strategy
                whitelistedTokens.forEach { mint ->
                    if (!_isActive.get()) return@forEach
                    
                    // Skip if already processing this token
                    if (stateMap.containsKey(mint)) {
                        return@forEach
                    }
                    
                    // Create TokenMeta for whitelisted token
                    val tokenMeta = TokenMeta(
                        mint = mint,
                        source = TokenSource.PROFILE // Use PROFILE as default source
                    )
                    
                    logger.info("🔍 Processing whitelisted token: $mint with ${strategy.type} strategy")
                    
                    // Pass to current strategy (RSI or any other)
                    strategy.onDiscovered(tokenMeta, this@SolanaTokenSwapBot)
                }
                
                // Wait before next check (30 seconds)
                delay(30_000)
                
            } catch (e: Exception) {
                logger.error("Error in whitelist observation: ${e.message}", e)
                delay(5_000) // Wait 5 seconds on error
            }
        }
    }

    override fun now(): Long = System.currentTimeMillis()

    override fun isNew(mint: String): Boolean = !stateMap.containsKey(mint)

    override fun status(mint: String): TokenStatus? = stateMap[mint]

    override suspend fun buy(mint: String): Boolean {
        if (config.blockBuy) {
            logger.error("❌ BUY BLOCKED: blockBuy=true for $mint")
            return false
        }

        // Check if we should allow buy without price
        if (!config.priceService.allowBuyWithoutPrice) {
            val price = getTokenUsdPrice(mint)
            if (price == null) {
                logger.error("❌ BUY BLOCKED: no price for $mint")
                return false
            }
        }

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
                                val swapTx = createSwapTransaction(swap.swapTransaction)
                                jitoBundlerService.enqueue(swapTx)
                                stateMap[mint]?.state = TokenState.Swapped
                                managementService?.incrementSuccessfulTrades()
                                logger.info("✅ BUY queued: $mint")
                                true
                            } catch (e: Exception) {
                                logger.error("❌ BUY failed: $mint - ${e.message}", e)
                                stateMap[mint]?.state = TokenState.SellFailed("Enqueue failed: ${e.message}")
                                managementService?.incrementFailedTrades()
                                false
                            }
                        } else {
                            val success = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                            if (success) {
                                stateMap[mint]?.state = TokenState.Swapped
                                managementService?.incrementSuccessfulTrades()
                                logger.info("✅ RPC BUY success: $mint")
                                true
                            } else {
                                stateMap[mint]?.state = TokenState.SellFailed("Swap failed")
                                managementService?.incrementFailedTrades()
                                logger.error("❌ RPC BUY failed: $mint")
                                false
                            }
                        }
                    }
                },
                onFailure = { ex ->
                    logger.error("❌ BUY exception: $mint - ${ex.message}", ex)
                    stateMap[mint]?.state = TokenState.SellFailed("Swap exception: ${ex.message}")
                    managementService?.incrementFailedTrades()
                    false
                }
            )
        }
    }

    override suspend fun sell(mint: String): Boolean {
        val status = stateMap[mint]

        // Check if token exists in wallet first
        val tokenInfo = tokenInfo(mint)
        if (tokenInfo == null) {
            logger.error("❌ SELL: Token $mint not found in wallet")
            return false
        }

        if (tokenInfo.tokenAmount.amount == "0") {
            logger.error("❌ SELL: Token $mint has zero balance")
            return false
        }

        // If token not in state map, add it (for manual sells of existing tokens)
        if (status == null) {
            stateMap[mint] = TokenStatus(mint, TokenState.Swapped)
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
                            val swapTx = createSwapTransaction(swap.swapTransaction)
                            jitoBundlerService.enqueue(swapTx)
                            stateMap[mint]?.state = TokenState.Sold
                            managementService?.incrementSuccessfulTrades()
                            logger.info("✅ SELL queued: $mint")
                            true
                        } catch (e: Exception) {
                            logger.error("❌ SELL failed: $mint - ${e.message}", e)
                            stateMap[mint]?.state = TokenState.SellFailed("Enqueue failed: ${e.message}")
                            managementService?.incrementFailedTrades()
                            false
                        }
                    } else {
                        val sold = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                        if (sold) {
                            stateMap[mint]?.state = TokenState.Sold
                            managementService?.incrementSuccessfulTrades()
                            logger.info("✅ RPC SELL success: $mint")
                            true
                        } else {
                            stateMap[mint]?.state = TokenState.SellFailed("Sell failed")
                            managementService?.incrementFailedTrades()
                            logger.error("❌ RPC SELL failed: $mint")
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
                            val swapTx = createSwapTransaction(swap.swapTransaction)
                            jitoBundlerService.enqueue(swapTx)
                            stateMap[token.address]?.state = TokenState.Sold
                            logger.info("✅ BULK SELL: ${token.address}")
                        } catch (e: Exception) {
                            logger.error("❌ BULK SELL failed: ${token.address} - ${e.message}", e)
                            stateMap[token.address]?.state = TokenState.SellFailed("Enqueue failed: ${e.message}")
                        }
                    } else {
                        try {
                            val sold = executeSwapTransaction(rpc, swap.swapTransaction, executor)
                            stateMap[token.address]?.state = if (sold) TokenState.Sold else TokenState.SellFailed("RPC execution failed")
                            if (sold) {
                                logger.info("✅ RPC BULK SELL: ${token.address}")
                            } else {
                                logger.error("❌ RPC BULK SELL failed: ${token.address}")
                            }
                        } catch (e: Exception) {
                            logger.error("❌ RPC BULK SELL error: ${token.address} - ${e.message}", e)
                            stateMap[token.address]?.state = TokenState.SellFailed("RPC execution failed: ${e.message}")
                        }
                    }
                }.onFailure {
                    logger.error("❌ BULK SELL error: ${token.address} - ${it.message}")
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
                logger.error("⚠️ EMERGENCY SELL: $mint - no price data")

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
        try {
            val existingTokens = allTokens()
                .filterNot { it.address == config.swapMint.base58() } // Keep SOL
                .filter {
                    val amount = it.tokenAmount.uiAmount ?: 0.0
                    amount > 0.0 // Only tokens with positive balance
                }

            if (existingTokens.isEmpty()) {
                return
            }

            logger.info("🧹 CLEANUP: Found ${existingTokens.size} wallet tokens to sell")

            existingTokens.forEach { token ->
                try {
                    // Add to state map for tracking
                    stateMap[token.address] = TokenStatus(token.address, TokenState.Swapped)

                    // Execute sell directly (don't use queue during startup)
                    val success = sell(token.address)
                    if (success) {
                        logger.info("✅ CLEANUP: Queued ${token.address} for sale")
                    } else {
                        logger.error("❌ CLEANUP: Failed to sell ${token.address}")
                    }

                    // Minimal delay between sells
                    delay(100)

                } catch (e: Exception) {
                    logger.error("❌ CLEANUP ERROR: ${token.address} - ${e.message}", e)
                }
            }

            // Short wait for sales to process before allowing new discovery
            delay(3_000)
            logger.info("✅ CLEANUP: Completed wallet cleanup")

        } catch (e: Exception) {
            logger.error("❌ CLEANUP FAILED: ${e.message}", e)
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
            "whitelistEnabled" to true,
            "whitelistSize" to rsiWhitelistSource.getWhitelistSize(),
            "whitelistedTokens" to rsiWhitelistSource.getWhitelistedTokens().take(5), // Show first 5
            "currentStrategy" to strategy.type.toString(),
            "sellQueueEnabled" to (config.sellQueue.enabled && ::sellQueue.isInitialized),
            "rpcUrl" to RPC_URL,
            "priceMissStats" to priceMissTracker.getStats(),
            "priceServiceStats" to priceService.getCacheStats(),
            "priceHistoryStats" to priceHistoryLoader.getCacheStats()
        )
    }
    
    /**
     * Get whitelist source for external access
     */
    fun getWhitelistSource(): RsiWhitelistSource = rsiWhitelistSource
    
    /**
     * Add token to whitelist
     */
    fun addToWhitelist(mint: String) {
        rsiWhitelistSource.addToken(mint)
        logger.info("Added $mint to whitelist")
    }
    
    /**
     * Remove token from whitelist
     */
    fun removeFromWhitelist(mint: String) {
        rsiWhitelistSource.removeToken(mint)
        logger.info("Removed $mint from whitelist")
    }
    
    /**
     * Update entire whitelist
     */
    fun updateWhitelist(tokens: Set<String>) {
        rsiWhitelistSource.updateWhitelist(tokens)
        logger.info("Updated whitelist with ${tokens.size} tokens")
    }
}
