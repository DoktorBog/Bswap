package com.bswap.server.service

import com.bswap.server.data.dexscreener.DexScreenerClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Price history data point
 */
data class PricePoint(
    val timestamp: Long,
    val price: Double,
    val volume: Double? = null,
    val source: String
)

/**
 * Configuration for price history loading
 */
data class PriceHistoryConfig(
    val maxDataPoints: Int = 50,          // Maximum price points to keep for RSI
    val cacheExpiryMs: Long = 300_000L,   // 5 minutes cache
    val includeSources: Set<String> = setOf("dexscreener", "birdeye", "pump.fun", "jupiter"),
    val fetchIntervalMs: Long = 60_000L   // Minimum interval between fetches
)

/**
 * Service for loading historical price data for RSI calculations
 */
class PriceHistoryLoader(
    private val httpClient: HttpClient,
    private val dexScreenerClient: DexScreenerClient,
    private val config: PriceHistoryConfig = PriceHistoryConfig()
) {
    private val log = LoggerFactory.getLogger("PriceHistoryLoader")
    private val json = Json { ignoreUnknownKeys = true }
    
    // Cache for price histories
    private val historyCache = ConcurrentHashMap<String, CachedPriceHistory>()
    private val lastFetchTime = ConcurrentHashMap<String, Long>()
    
    /**
     * Load price history for a token from multiple sources
     */
    suspend fun loadPriceHistory(mint: String): List<Double>? = coroutineScope {
        // Check cache first
        getCachedHistory(mint)?.let { return@coroutineScope it }
        
        // Rate limit fetches
        val lastFetch = lastFetchTime[mint] ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastFetch < config.fetchIntervalMs) {
            log.debug("Skipping price history fetch for $mint - too soon since last fetch")
            return@coroutineScope null
        }
        lastFetchTime[mint] = now
        
        log.info("📊 Loading price history for token: $mint")
        
        val allPricePoints = mutableListOf<PricePoint>()
        
        // Fetch from multiple sources in parallel
        val jobs = listOf(
            async { if (config.includeSources.contains("dexscreener")) fetchFromDexScreener(mint) else emptyList() },
            async { if (config.includeSources.contains("birdeye")) fetchFromBirdeye(mint) else emptyList() },
            async { if (config.includeSources.contains("pump.fun")) fetchFromPumpFun(mint) else emptyList() },
            async { if (config.includeSources.contains("jupiter")) fetchFromJupiterAggregator(mint) else emptyList() }
        )
        
        // Collect all results
        jobs.forEach { job ->
            try {
                allPricePoints.addAll(job.await())
            } catch (e: Exception) {
                log.debug("Error fetching price history: ${e.message}")
            }
        }
        
        if (allPricePoints.isEmpty()) {
            log.warn("No price history found for $mint from any source")
            return@coroutineScope null
        }
        
        // Sort by timestamp and deduplicate
        val sortedPoints = allPricePoints
            .sortedBy { it.timestamp }
            .distinctBy { it.timestamp / 60000 } // Group by minute
            .takeLast(config.maxDataPoints)
        
        // Extract just prices for RSI calculation
        val prices = sortedPoints.map { it.price }
        
        // Cache the result
        cacheHistory(mint, prices)
        
        log.info("✅ Loaded ${prices.size} price points for $mint from ${sortedPoints.map { it.source }.distinct()}")
        prices
    }
    
    /**
     * Load price history with OHLCV data
     */
    suspend fun loadOHLCVHistory(mint: String, intervalMinutes: Int = 5): List<OHLCV>? = coroutineScope {
        log.info("📊 Loading OHLCV history for token: $mint")
        
        // Try to get from DexScreener first (best OHLCV support)
        val ohlcv = fetchOHLCVFromDexScreener(mint, intervalMinutes)
        
        if (ohlcv.isNullOrEmpty()) {
            log.warn("No OHLCV data found for $mint")
            return@coroutineScope null
        }
        
        log.info("✅ Loaded ${ohlcv.size} OHLCV candles for $mint")
        ohlcv
    }
    
    private suspend fun fetchFromDexScreener(mint: String): List<PricePoint> {
        return try {
            val response = dexScreenerClient.getPairsByToken(mint)
            val pairs = response.pairs ?: return emptyList()
            
            val pricePoints = mutableListOf<PricePoint>()
            
            // Get the most liquid pair
            val bestPair = pairs.maxByOrNull { it.liquidity?.usd ?: 0.0 } ?: return emptyList()
            
            // Current price
            bestPair.priceUsd?.toDoubleOrNull()?.let { price ->
                pricePoints.add(PricePoint(
                    timestamp = System.currentTimeMillis(),
                    price = price,
                    volume = 0.0, // Volume not available in basic API
                    source = "dexscreener"
                ))
            }
            
            // DexScreener doesn't provide detailed historical data in this API
            // We'll just use the current price point
            // TODO: Use DexScreener's chart API if available for more historical data
            
            pricePoints
        } catch (e: Exception) {
            log.debug("DexScreener fetch failed for $mint: ${e.message}")
            emptyList()
        }
    }
    
    private suspend fun fetchFromBirdeye(mint: String): List<PricePoint> {
        return try {
            // Birdeye API endpoint for price history
            val url = "https://public-api.birdeye.so/public/price_history?address=$mint&type=1H"
            // Birdeye API (simplified for now)
            emptyList<PricePoint>()
        } catch (e: Exception) {
            log.debug("Birdeye fetch failed for $mint: ${e.message}")
            emptyList()
        }
    }
    
    private suspend fun fetchFromPumpFun(mint: String): List<PricePoint> {
        return try {
            // Pump.fun API for pump coins
            val url = "https://api.pump.fun/trades/$mint?limit=100"
            val response: PumpFunTradesResponse = httpClient.get(url).body()
            
            // Group trades by minute and calculate average price
            response.trades
                ?.groupBy { it.timestamp / 60000 } // Group by minute
                ?.map { (minuteTimestamp, trades) ->
                    val avgPrice = trades.map { it.price }.average()
                    val totalVolume = trades.sumOf { it.amount }
                    PricePoint(
                        timestamp = minuteTimestamp * 60000L,
                        price = avgPrice,
                        volume = totalVolume,
                        source = "pump.fun"
                    )
                }
                ?.sortedBy { it.timestamp }
                ?.takeLast(config.maxDataPoints)
                ?: emptyList()
        } catch (e: Exception) {
            log.debug("Pump.fun fetch failed for $mint: ${e.message}")
            emptyList()
        }
    }
    
    private suspend fun fetchFromJupiterAggregator(mint: String): List<PricePoint> {
        return try {
            // Jupiter price API - usually only current price
            val url = "https://price.jup.ag/v6/price?ids=$mint"
            val response: JupiterV6PriceResponse = httpClient.get(url).body()
            
            response.data[mint]?.let { tokenData ->
                listOf(PricePoint(
                    timestamp = System.currentTimeMillis(),
                    price = tokenData.price,
                    source = "jupiter"
                ))
            } ?: emptyList()
        } catch (e: Exception) {
            log.debug("Jupiter fetch failed for $mint: ${e.message}")
            emptyList()
        }
    }
    
    private suspend fun fetchOHLCVFromDexScreener(mint: String, intervalMinutes: Int): List<OHLCV>? {
        return try {
            val response = dexScreenerClient.getPairsByToken(mint)
            val pair = response.pairs?.maxByOrNull { it.liquidity?.usd ?: 0.0 }
            
            // DexScreener doesn't provide historical OHLCV in the basic API
            // We can only construct limited data from price changes
            val currentPrice = pair?.priceUsd?.toDoubleOrNull() ?: return null
            val now = System.currentTimeMillis()
            
            // Create synthetic OHLCV from available data
            listOf(
                OHLCV(
                    timestamp = now,
                    open = currentPrice,
                    high = currentPrice * 1.01, // Estimate
                    low = currentPrice * 0.99,  // Estimate
                    close = currentPrice,
                    volume = 0.0 // Simplified - no volume data in basic response
                )
            )
        } catch (e: Exception) {
            log.debug("OHLCV fetch failed for $mint: ${e.message}")
            null
        }
    }
    
    private fun getCachedHistory(mint: String): List<Double>? {
        val cached = historyCache[mint] ?: return null
        val now = System.currentTimeMillis()
        
        return if (now - cached.timestamp < config.cacheExpiryMs) {
            cached.prices
        } else {
            historyCache.remove(mint)
            null
        }
    }
    
    private fun cacheHistory(mint: String, prices: List<Double>) {
        historyCache[mint] = CachedPriceHistory(
            mint = mint,
            prices = prices,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Clear expired cache entries
     */
    fun cleanupCache() {
        val now = System.currentTimeMillis()
        val expired = historyCache.entries.filter { (_, cached) ->
            now - cached.timestamp > config.cacheExpiryMs
        }.map { it.key }
        
        expired.forEach { historyCache.remove(it) }
        
        if (expired.isNotEmpty()) {
            log.debug("Cleaned ${expired.size} expired price history entries")
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "cacheSize" to historyCache.size,
            "cachedTokens" to historyCache.keys.toList(),
            "oldestEntry" to (historyCache.values.minByOrNull { it.timestamp }?.let {
                System.currentTimeMillis() - it.timestamp
            } ?: 0L)
        )
    }
}

/**
 * Cached price history
 */
private data class CachedPriceHistory(
    val mint: String,
    val prices: List<Double>,
    val timestamp: Long
)

/**
 * OHLCV data
 */
data class OHLCV(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

// API Response models (simplified)

@Serializable
private data class PumpFunTradesResponse(
    val trades: List<PumpFunTrade>? = null
)

@Serializable
private data class PumpFunTrade(
    val timestamp: Long,
    val price: Double,
    val amount: Double
)

@Serializable
private data class JupiterV6PriceResponse(
    val data: Map<String, JupiterV6TokenData> = emptyMap()
)

@Serializable
private data class JupiterV6TokenData(
    val id: String,
    val price: Double
)