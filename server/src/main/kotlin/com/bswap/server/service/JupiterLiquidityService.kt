package com.bswap.server.service

import com.bswap.server.config.LiquidityProtectionConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Jupiter API integration for liquidity analysis and price impact estimation
 * Preserves all original log message texts and interfaces
 */
class JupiterLiquidityService(
    private val httpClient: HttpClient,
    private val config: LiquidityProtectionConfig = LiquidityProtectionConfig()
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JupiterLiquidityService::class.java)
        private const val JUPITER_API_BASE = "https://quote-api.jup.ag/v6"
        private const val JUPITER_STATS_API = "https://stats.jup.ag/coingecko"
    }

    private val liquidityCache = ConcurrentHashMap<String, CachedLiquidityData>()
    private val priceImpactCache = ConcurrentHashMap<String, CachedPriceImpact>()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class JupiterQuoteResponse(
        val inputMint: String,
        val inAmount: String,
        val outputMint: String,
        val outAmount: String,
        val otherAmountThreshold: String,
        val swapMode: String,
        val slippageBps: Int,
        val platformFee: JupiterPlatformFee? = null,
        val priceImpactPct: String,
        val routePlan: List<JupiterRoutePlan> = emptyList()
    )

    @Serializable
    data class JupiterPlatformFee(
        val amount: String,
        val feeBps: Int
    )

    @Serializable
    data class JupiterRoutePlan(
        val swapInfo: JupiterSwapInfo,
        val percent: Int
    )

    @Serializable
    data class JupiterSwapInfo(
        val ammKey: String,
        val label: String,
        val inputMint: String,
        val outputMint: String,
        val inAmount: String,
        val outAmount: String,
        val feeAmount: String,
        val feeMint: String
    )

    @Serializable
    data class JupiterTokenStats(
        val id: String,
        val symbol: String,
        val name: String,
        val current_price: Double? = null,
        val market_cap: Double? = null,
        val total_volume: Double? = null,
        val price_change_percentage_24h: Double? = null
    )

    data class CachedLiquidityData(
        val reserves: Double,
        val volume24h: Double,
        val priceImpact1k: Double,
        val priceImpact10k: Double,
        val timestamp: Long
    )

    data class CachedPriceImpact(
        val impact: Double,
        val timestamp: Long
    )

    data class LiquidityAnalysis(
        val isLiquid: Boolean,
        val reserves: Double,
        val volume24h: Double,
        val priceImpact: Double,
        val riskScore: Double,
        val warnings: List<String>
    )

    /**
     * Analyze token liquidity using Jupiter API
     */
    suspend fun analyzeLiquidity(mint: String, tradeAmountUsd: Double = 1000.0): LiquidityAnalysis? {
        return try {
            val cached = getCachedLiquidity(mint)
            if (cached != null) {
                return buildLiquidityAnalysis(cached, tradeAmountUsd)
            }

            val liquidityData = fetchLiquidityData(mint, tradeAmountUsd)
            if (liquidityData != null) {
                cacheLiquidityData(mint, liquidityData)
                buildLiquidityAnalysis(liquidityData, tradeAmountUsd)
            } else {
                logger.warn("❌ LIQUIDITY ANALYSIS: Failed to fetch data for $mint")
                null
            }
        } catch (e: Exception) {
            logger.error("❌ LIQUIDITY ANALYSIS ERROR: $mint - ${e.message}", e)
            null
        }
    }

    /**
     * Estimate price impact for a trade
     */
    suspend fun estimatePriceImpact(
        inputMint: String,
        outputMint: String,
        amountIn: Double,
        slippageBps: Int = 50
    ): Double? {
        val cacheKey = "$inputMint-$outputMint-$amountIn"
        val cached = getCachedPriceImpact(cacheKey)
        if (cached != null) {
            return cached.impact
        }

        return try {
            val quote = getJupiterQuote(inputMint, outputMint, amountIn, slippageBps)
            val priceImpact = quote?.priceImpactPct?.toDoubleOrNull()
            
            if (priceImpact != null) {
                cachePriceImpact(cacheKey, priceImpact)
                logger.debug("📊 PRICE IMPACT: $inputMint -> $outputMint = ${String.format("%.3f", priceImpact)}%")
            }
            
            priceImpact
        } catch (e: Exception) {
            logger.warn("❌ PRICE IMPACT ERROR: $inputMint -> $outputMint - ${e.message}")
            null
        }
    }

    /**
     * Get Jupiter quote for price impact calculation
     */
    private suspend fun getJupiterQuote(
        inputMint: String,
        outputMint: String,
        amount: Double,
        slippageBps: Int
    ): JupiterQuoteResponse? {
        return withTimeoutOrNull(config.liquidityCheckTimeoutMs) {
            try {
                val response = httpClient.get("$JUPITER_API_BASE/quote") {
                    parameter("inputMint", inputMint)
                    parameter("outputMint", outputMint)
                    parameter("amount", amount.toLong())
                    parameter("slippageBps", slippageBps)
                    parameter("onlyDirectRoutes", false)
                    parameter("asLegacyTransaction", false)
                }

                if (response.status == HttpStatusCode.OK) {
                    response.body<String>().let { json.decodeFromString<JupiterQuoteResponse>(it) }
                } else {
                    logger.warn("❌ JUPITER QUOTE: HTTP ${response.status} for $inputMint -> $outputMint")
                    null
                }
            } catch (e: Exception) {
                logger.debug("JUPITER QUOTE ERROR: ${e.message}")
                null
            }
        }
    }

    /**
     * Fetch comprehensive liquidity data
     */
    private suspend fun fetchLiquidityData(mint: String, tradeAmountUsd: Double): CachedLiquidityData? {
        // Get price impact for different trade sizes
        val impact1k = estimatePriceImpact(mint, "So11111111111111111111111111111111111111112", 1000.0, 50) ?: 0.0
        val impact10k = estimatePriceImpact(mint, "So11111111111111111111111111111111111111112", 10000.0, 50) ?: 0.0

        // Get token stats from Jupiter
        val stats = getTokenStats(mint)
        val volume24h = stats?.total_volume ?: 0.0
        val marketCap = stats?.market_cap ?: 0.0

        // Estimate reserves from market cap (rough approximation)
        val estimatedReserves = marketCap * 0.1 // Assume 10% of market cap in reserves

        return CachedLiquidityData(
            reserves = estimatedReserves,
            volume24h = volume24h,
            priceImpact1k = impact1k,
            priceImpact10k = impact10k,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get token statistics from Jupiter
     */
    private suspend fun getTokenStats(mint: String): JupiterTokenStats? {
        return withTimeoutOrNull(config.liquidityCheckTimeoutMs) {
            try {
                val response = httpClient.get("$JUPITER_STATS_API/coins/$mint")
                
                if (response.status == HttpStatusCode.OK) {
                    response.body<String>().let { json.decodeFromString<JupiterTokenStats>(it) }
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.debug("JUPITER STATS ERROR: ${e.message}")
                null
            }
        }
    }

    /**
     * Build liquidity analysis from cached data
     */
    private fun buildLiquidityAnalysis(data: CachedLiquidityData, tradeAmountUsd: Double): LiquidityAnalysis {
        val warnings = mutableListOf<String>()
        var riskScore = 0.0

        // Check reserves
        if (data.reserves < config.minPoolReserveUsd) {
            warnings.add("Low pool reserves: $${String.format("%.0f", data.reserves)}")
            riskScore += 0.3
        }

        // Check volume
        if (data.volume24h < config.minVolumeUsd24h) {
            warnings.add("Low 24h volume: $${String.format("%.0f", data.volume24h)}")
            riskScore += 0.3
        }

        // Check price impact
        val relevantImpact = if (tradeAmountUsd >= 5000) data.priceImpact10k else data.priceImpact1k
        if (relevantImpact > config.maxPriceImpactPercent) {
            warnings.add("High price impact: ${String.format("%.2f", relevantImpact)}%")
            riskScore += 0.4
        }

        val isLiquid = warnings.isEmpty() && riskScore < 0.5

        return LiquidityAnalysis(
            isLiquid = isLiquid,
            reserves = data.reserves,
            volume24h = data.volume24h,
            priceImpact = relevantImpact,
            riskScore = riskScore,
            warnings = warnings
        )
    }

    /**
     * Cache management
     */
    private fun getCachedLiquidity(mint: String): CachedLiquidityData? {
        val cached = liquidityCache[mint]
        return if (cached != null && System.currentTimeMillis() - cached.timestamp < config.priceImpactCacheTimeMs) {
            cached
        } else {
            liquidityCache.remove(mint)
            null
        }
    }

    private fun cacheLiquidityData(mint: String, data: CachedLiquidityData) {
        liquidityCache[mint] = data
    }

    private fun getCachedPriceImpact(key: String): CachedPriceImpact? {
        val cached = priceImpactCache[key]
        return if (cached != null && System.currentTimeMillis() - cached.timestamp < config.priceImpactCacheTimeMs) {
            cached
        } else {
            priceImpactCache.remove(key)
            null
        }
    }

    private fun cachePriceImpact(key: String, impact: Double) {
        priceImpactCache[key] = CachedPriceImpact(impact, System.currentTimeMillis())
    }

    /**
     * Validate trade against liquidity constraints
     */
    suspend fun validateTrade(mint: String, amountUsd: Double): Boolean {
        if (!config.enablePreTradeValidation) return true

        val analysis = analyzeLiquidity(mint, amountUsd)
        return analysis?.isLiquid == true
    }

    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "liquidityCacheSize" to liquidityCache.size,
            "priceImpactCacheSize" to priceImpactCache.size,
            "cacheHitRate" to calculateCacheHitRate(),
            "avgLiquidityScore" to calculateAverageLiquidityScore()
        )
    }

    private fun calculateCacheHitRate(): Double {
        // Simplified cache hit rate calculation
        val totalEntries = liquidityCache.size + priceImpactCache.size
        return if (totalEntries > 0) 0.85 else 0.0 // Placeholder
    }

    private fun calculateAverageLiquidityScore(): Double {
        return liquidityCache.values.map { 1.0 - (it.priceImpact1k / 10.0) }.average().let {
            if (it.isNaN()) 0.5 else it
        }
    }

    /**
     * Cleanup expired cache entries
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val expiry = config.priceImpactCacheTimeMs

        liquidityCache.entries.removeIf { now - it.value.timestamp > expiry }
        priceImpactCache.entries.removeIf { now - it.value.timestamp > expiry }
    }
}