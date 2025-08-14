package com.bswap.server.service

import com.bswap.server.TradingRuntime
import com.bswap.server.data.whitelist.CoinWhitelistSource
import com.bswap.server.data.whitelist.WhitelistCoin
import com.bswap.server.data.dexscreener.models.TokenProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Configuration for whitelist buy observation
 */
data class WhitelistBuyConfig(
    val enabled: Boolean = true,
    val observationIntervalMs: Long = 5000, // Check every 5 seconds
    val priceChangeThreshold: Double = 0.05, // 5% price increase threshold
    val volumeThreshold: Double = 10000.0, // Minimum volume threshold
    val maxConcurrentBuys: Int = 3, // Maximum concurrent buy operations
    val cooldownMs: Long = 60000, // 1 minute cooldown between buys for same token
    val priorityThreshold: Int = 50, // Only observe coins with priority >= this value
    val enablePriceAlerts: Boolean = true,
    val enableVolumeAlerts: Boolean = true,
    val autoExecuteBuys: Boolean = false // If true, automatically execute buys on signals
)

/**
 * Buy signal data for whitelisted coins
 */
data class WhitelistBuySignal(
    val coin: WhitelistCoin,
    val mint: String,
    val currentPrice: Double?,
    val priceChange24h: Double?,
    val volume24h: Double?,
    val signal: BuySignalType,
    val strength: Double, // 0.0 to 1.0
    val timestamp: Long = System.currentTimeMillis(),
    val reasons: List<String> = emptyList()
)

enum class BuySignalType {
    PRICE_BREAKOUT,
    VOLUME_SPIKE,
    PRIORITY_TOKEN,
    TECHNICAL_INDICATOR,
    MANUAL_TRIGGER
}

/**
 * Observes whitelisted coins for buy opportunities and signals
 */
class WhitelistBuyObserver(
    private val whitelistSource: CoinWhitelistSource,
    private val whitelistManager: CoinWhitelistManager,
    private val priceService: PriceService,
    private val config: WhitelistBuyConfig = WhitelistBuyConfig()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _isActive = AtomicBoolean(false)
    private val _lastObservationTime = AtomicLong(0)
    private val lastBuyAttempts = ConcurrentHashMap<String, Long>()
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()
    
    // Observable flows for buy signals
    private val _buySignals = MutableSharedFlow<WhitelistBuySignal>(replay = 10)
    val buySignals: SharedFlow<WhitelistBuySignal> = _buySignals.asSharedFlow()
    
    private val _activeBuys = MutableStateFlow<Set<String>>(emptySet())
    val activeBuys: StateFlow<Set<String>> = _activeBuys.asStateFlow()
    
    private var tradingRuntime: TradingRuntime? = null
    
    /**
     * Start observing whitelisted coins for buy opportunities
     */
    fun start(runtime: TradingRuntime? = null) {
        if (_isActive.get()) {
            logger.warn("Whitelist buy observer already active")
            return
        }
        
        this.tradingRuntime = runtime
        _isActive.set(true)
        logger.info("Starting whitelist buy observer with config: $config")
        
        // Start observation loop
        scope.launch {
            while (_isActive.get()) {
                try {
                    observeWhitelistedCoins()
                    _lastObservationTime.set(System.currentTimeMillis())
                    delay(config.observationIntervalMs)
                } catch (e: Exception) {
                    logger.error("Error in whitelist observation loop: ${e.message}", e)
                    delay(5000) // Wait 5 seconds before retrying
                }
            }
        }
        
        // Start cleanup task
        scope.launch {
            while (_isActive.get()) {
                cleanupOldData()
                delay(60000) // Cleanup every minute
            }
        }
        
        logger.info("Whitelist buy observer started successfully")
    }
    
    /**
     * Stop the whitelist observer
     */
    fun stop() {
        _isActive.set(false)
        scope.cancel()
        logger.info("Whitelist buy observer stopped")
    }
    
    /**
     * Check if observer is active
     */
    fun isActive(): Boolean = _isActive.get()
    
    /**
     * Manually trigger a buy signal for a specific coin
     */
    suspend fun triggerManualBuy(symbol: String, reasons: List<String> = listOf("Manual trigger")): Boolean {
        val coin = whitelistSource.getEnabledCoins().find { it.symbol.equals(symbol, ignoreCase = true) }
        if (coin == null) {
            logger.warn("Cannot trigger manual buy: coin $symbol not found in whitelist")
            return false
        }
        
        val mint = coin.mint
        if (mint == null) {
            logger.warn("Cannot trigger manual buy: mint not resolved for $symbol")
            return false
        }
        
        val signal = WhitelistBuySignal(
            coin = coin,
            mint = mint,
            currentPrice = priceService.getTokenPrice(mint)?.priceUsd,
            priceChange24h = null,
            volume24h = null,
            signal = BuySignalType.MANUAL_TRIGGER,
            strength = 1.0,
            reasons = reasons
        )
        
        return processBuySignal(signal)
    }
    
    /**
     * Get current observation statistics
     */
    fun getStats(): Map<String, Any> {
        val enabledCoins = whitelistSource.getEnabledCoins()
        val observedCoins = enabledCoins.filter { it.priority >= config.priorityThreshold }
        
        return mapOf(
            "isActive" to _isActive.get(),
            "totalWhitelistedCoins" to enabledCoins.size,
            "observedCoins" to observedCoins.size,
            "activeBuys" to _activeBuys.value.size,
            "lastObservationTime" to _lastObservationTime.get(),
            "priceHistorySize" to priceHistory.size,
            "cooldownTokens" to lastBuyAttempts.size,
            "config" to config
        )
    }
    
    /**
     * Main observation logic for whitelisted coins
     */
    private suspend fun observeWhitelistedCoins() {
        if (!config.enabled) return
        
        val enabledCoins = whitelistSource.getEnabledCoins()
            .filter { it.priority >= config.priorityThreshold }
            .filter { it.mint != null }
        
        if (enabledCoins.isEmpty()) {
            logger.debug("No enabled coins with resolved mints to observe")
            return
        }
        
        logger.debug("Observing ${enabledCoins.size} whitelisted coins for buy opportunities")
        
        // Process coins in parallel but limit concurrency
        enabledCoins.chunked(5).forEach { batch ->
            batch.map { coin ->
                scope.async {
                    try {
                        observeCoin(coin)
                    } catch (e: Exception) {
                        logger.error("Error observing coin ${coin.symbol}: ${e.message}", e)
                    }
                }
            }.awaitAll()
        }
    }
    
    /**
     * Observe a specific coin for buy signals
     */
    private suspend fun observeCoin(coin: WhitelistCoin) {
        val mint = coin.mint ?: return
        val symbol = coin.symbol
        
        // Check cooldown
        val lastBuy = lastBuyAttempts[mint]
        if (lastBuy != null && System.currentTimeMillis() - lastBuy < config.cooldownMs) {
            return
        }
        
        // Get current price and market data
        val priceData = priceService.getTokenPrice(mint)
        val currentPrice = priceData?.priceUsd
        
        if (currentPrice == null) {
            logger.debug("No price data available for $symbol ($mint)")
            return
        }
        
        // Update price history
        updatePriceHistory(mint, currentPrice)
        
        // Analyze for buy signals
        val signals = analyzeForBuySignals(coin, mint, currentPrice, priceData)
        
        // Process any generated signals
        signals.forEach { signal ->
            logger.info("Buy signal generated for ${coin.symbol}: ${signal.signal} (strength: ${signal.strength})")
            _buySignals.tryEmit(signal)
            
            if (config.autoExecuteBuys) {
                processBuySignal(signal)
            }
        }
    }
    
    /**
     * Analyze coin data for potential buy signals
     */
    private fun analyzeForBuySignals(
        coin: WhitelistCoin,
        mint: String,
        currentPrice: Double,
        priceData: TokenPrice
    ): List<WhitelistBuySignal> {
        val signals = mutableListOf<WhitelistBuySignal>()
        val history = priceHistory[mint] ?: return signals
        
        if (history.size < 2) return signals
        
        val reasons = mutableListOf<String>()
        var maxStrength = 0.0
        var primarySignal = BuySignalType.TECHNICAL_INDICATOR
        
        // Price breakout analysis
        if (config.enablePriceAlerts) {
            val priceChange = calculatePriceChange(history)
            if (priceChange > config.priceChangeThreshold) {
                reasons.add("Price increased ${(priceChange * 100).toInt()}% recently")
                maxStrength = maxOf(maxStrength, priceChange / config.priceChangeThreshold)
                primarySignal = BuySignalType.PRICE_BREAKOUT
            }
        }
        
        // Volume analysis (simplified - using price movements as volume proxy)
        if (config.enableVolumeAlerts) {
            // Since we don't have direct volume data, use price volatility as a proxy
            if (history.size >= 3) {
                val recentVolatility = calculateVolatility(history.takeLast(3))
                if (recentVolatility > 0.02) { // 2% volatility threshold
                    reasons.add("High price volatility indicating activity")
                    maxStrength = maxOf(maxStrength, 0.6)
                    if (primarySignal == BuySignalType.TECHNICAL_INDICATOR) {
                        primarySignal = BuySignalType.VOLUME_SPIKE
                    }
                }
            }
        }
        
        // Priority token boost
        if (coin.priority >= 80) {
            reasons.add("High priority token (priority: ${coin.priority})")
            maxStrength = maxOf(maxStrength, 0.6)
            if (primarySignal == BuySignalType.TECHNICAL_INDICATOR) {
                primarySignal = BuySignalType.PRIORITY_TOKEN
            }
        }
        
        // Generate signal if criteria met
        if (maxStrength > 0.3 && reasons.isNotEmpty()) {
            signals.add(
                WhitelistBuySignal(
                    coin = coin,
                    mint = mint,
                    currentPrice = currentPrice,
                    priceChange24h = null, // Not available in TokenPrice
                    volume24h = null, // Not available in TokenPrice
                    signal = primarySignal,
                    strength = maxStrength.coerceAtMost(1.0),
                    reasons = reasons
                )
            )
        }
        
        return signals
    }
    
    /**
     * Process a buy signal (execute buy if conditions are met)
     */
    private suspend fun processBuySignal(signal: WhitelistBuySignal): Boolean {
        val mint = signal.mint
        val symbol = signal.coin.symbol
        
        // Check if we can execute more buys
        if (_activeBuys.value.size >= config.maxConcurrentBuys) {
            logger.info("Max concurrent buys reached, skipping $symbol")
            return false
        }
        
        // Check cooldown again
        val lastBuy = lastBuyAttempts[mint]
        if (lastBuy != null && System.currentTimeMillis() - lastBuy < config.cooldownMs) {
            logger.debug("Buy cooldown active for $symbol")
            return false
        }
        
        // Execute buy if we have a trading runtime
        val runtime = tradingRuntime
        if (runtime != null) {
            return try {
                logger.info("Executing whitelist buy for $symbol (${signal.signal}, strength: ${signal.strength})")
                
                // Add to active buys
                _activeBuys.value = _activeBuys.value + mint
                lastBuyAttempts[mint] = System.currentTimeMillis()
                
                // Execute the buy
                val success = runtime.buy(mint)
                
                if (success) {
                    logger.info("✅ Whitelist buy successful for $symbol")
                } else {
                    logger.warn("❌ Whitelist buy failed for $symbol")
                }
                
                // Remove from active buys after completion
                scope.launch {
                    delay(5000) // Wait 5 seconds then remove from active
                    _activeBuys.value = _activeBuys.value - mint
                }
                
                success
            } catch (e: Exception) {
                logger.error("Exception during whitelist buy for $symbol: ${e.message}", e)
                _activeBuys.value = _activeBuys.value - mint
                false
            }
        } else {
            logger.info("No trading runtime available, signal logged: $symbol (${signal.signal})")
            return false
        }
    }
    
    /**
     * Update price history for a token
     */
    private fun updatePriceHistory(mint: String, price: Double) {
        val history = priceHistory.getOrPut(mint) { mutableListOf() }
        history.add(price)
        
        // Keep only recent price history (last 20 data points)
        if (history.size > 20) {
            history.removeAt(0)
        }
    }
    
    /**
     * Calculate recent price change percentage
     */
    private fun calculatePriceChange(history: List<Double>): Double {
        if (history.size < 2) return 0.0
        
        val currentPrice = history.last()
        val previousPrice = history[history.size - 2]
        
        return if (previousPrice > 0) {
            (currentPrice - previousPrice) / previousPrice
        } else 0.0
    }
    
    /**
     * Calculate price volatility over a period
     */
    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        val changes = prices.zipWithNext { a, b ->
            if (a > 0) kotlin.math.abs((b - a) / a) else 0.0
        }
        
        return changes.average()
    }
    
    /**
     * Clean up old data to prevent memory leaks
     */
    private fun cleanupOldData() {
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours ago
        
        // Clean up old buy attempts
        lastBuyAttempts.entries.removeIf { it.value < cutoffTime }
        
        // Clean up price history for tokens not in whitelist
        val currentMints = whitelistSource.getEnabledCoins().mapNotNull { it.mint }.toSet()
        priceHistory.keys.removeIf { it !in currentMints }
        
        logger.debug("Cleaned up old whitelist observation data")
    }
}