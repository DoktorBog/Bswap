package com.bswap.server.ai

import com.bswap.server.*
import com.bswap.server.stratagy.TradingStrategy
import com.bswap.server.stratagy.calculateTokenUsdPrice
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class OpenAITradingStrategy(
    private val cfg: AIStrategyConfig,
    private val openaiApiKey: String
) : TradingStrategy {
    
    override val type: StrategyType = StrategyType.AI_STRATEGY
    private val logger = LoggerFactory.getLogger(OpenAITradingStrategy::class.java)
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }
    
    private val openAIService = OpenAIService(openaiApiKey, httpClient)
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val positions = ConcurrentHashMap<String, Double>() // entry prices
    private val priceHistory = ConcurrentHashMap<String, MutableList<Pair<Double, Long>>>()
    private val analysisCache = ConcurrentHashMap<String, Pair<TokenAnalysis, Long>>() // token -> (analysis, timestamp)
    private val validatedTokens = ConcurrentHashMap<String, Boolean>() // token -> isValid
    
    private val CACHE_DURATION_MS = 30_000L // 30 seconds cache for analysis
    private val VALIDATION_CACHE_DURATION_MS = 300_000L // 5 minutes cache for validation
    
    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        
        logger.info("🤖 AI Strategy: Discovered new token ${meta.mint} from ${meta.source} - performing autonomous validation")
        
        // Initialize price history
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
        
        coroutineScope {
            try {
                // AI strategy performs its own independent validation and analysis
                logger.info("🤖 AI Strategy: Bypassing basic validation for ${meta.mint} - using AI-powered analysis")
                
                // Parallel validation and analysis
                val validationJob = async { validateToken(meta.mint, runtime) }
                val analysisJob = async { 
                    if (shouldAnalyze(meta.mint)) {
                        analyzeToken(meta.mint, runtime) 
                    } else null
                }
                
                val isValid = validationJob.await()
                val analysis = analysisJob.await()
                
                if (!isValid) {
                    logger.info("🤖 AI Strategy: Token ${meta.mint} failed AI validation - SKIPPING")
                    return@coroutineScope
                }
                
                logger.info("🤖 AI Strategy: Token ${meta.mint} passed AI validation - proceeding with analysis")
                
                if (analysis?.shouldBuy == true && analysis.confidence > cfg.confidenceThreshold) {
                    logger.info("🤖 AI Strategy: BUYING ${meta.mint} - Confidence: ${analysis.confidence * 100}%, Risk: ${analysis.riskAssessment}")
                    val bought = runtime.buy(meta.mint)
                    if (bought) {
                        val tokenInfo = runtime.tokenInfo(meta.mint)
                        if (tokenInfo != null) {
                            positions[meta.mint] = calculateTokenUsdPrice(tokenInfo, runtime)
                            plannedSells[meta.mint] = runtime.now() + cfg.minHoldMs
                            logger.info("🤖 AI Strategy: Successfully bought ${meta.mint} - Reason: ${analysis.reasoning}")
                        }
                    } else {
                        logger.warn("🤖 AI Strategy: Failed to execute buy for ${meta.mint}")
                    }
                } else if (analysis?.shouldSkip == true) {
                    logger.info("🤖 AI Strategy: SKIPPING ${meta.mint} - Reason: ${analysis.reasoning}")
                } else {
                    logger.info("🤖 AI Strategy: Insufficient confidence for ${meta.mint} - Confidence: ${(analysis?.confidence ?: 0.0) * 100}%")
                }
                
            } catch (e: Exception) {
                logger.error("🤖 AI Strategy: Error processing discovered token ${meta.mint}", e)
            }
        }
    }
    
    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        
        try {
            // Update price history for all tokens
            updatePriceHistory(runtime)
            
            // Process existing positions with AI analysis
            processExistingPositions(runtime, now)
            
            // Handle timed sells as fallback
            handleTimedSells(runtime, now)
            
            // Clean up old cache entries
            cleanupCache(now)
            
        } catch (e: Exception) {
            logger.error("Error in OpenAI trading strategy tick", e)
        }
    }
    
    private suspend fun validateToken(tokenAddress: String, runtime: TradingRuntime): Boolean {
        // Check cache first
        val cachedValidation = validatedTokens[tokenAddress]
        if (cachedValidation != null) {
            return cachedValidation
        }
        
        return try {
            val tokenInfo = runtime.tokenInfo(tokenAddress)
            val metadata = buildMap {
                tokenInfo?.let {
                    put("decimals", it.tokenAmount.decimals)
                    put("amount", it.tokenAmount.amount)
                    put("uiAmount", it.tokenAmount.uiAmount ?: 0.0)
                }
            }
            
            val isValid = openAIService.validateTokenSafety(tokenAddress, metadata)
            
            // Cache validation result for 5 minutes
            validatedTokens[tokenAddress] = isValid
            
            // Schedule cache cleanup
            scheduleValidationCacheCleanup(tokenAddress)
            
            isValid
        } catch (e: Exception) {
            logger.error("Error validating token $tokenAddress", e)
            false // Default to invalid if validation fails
        }
    }
    
    private suspend fun analyzeToken(tokenAddress: String, runtime: TradingRuntime): TokenAnalysis? {
        // Check cache first
        val cached = analysisCache[tokenAddress]
        if (cached != null && System.currentTimeMillis() - cached.second < CACHE_DURATION_MS) {
            return cached.first
        }
        
        return try {
            val tokenInfo = runtime.tokenInfo(tokenAddress) ?: return null
            val currentPrice = calculateTokenUsdPrice(tokenInfo, runtime)
            val history = priceHistory[tokenAddress] ?: return null
            val priceList = history.map { it.first }
            
            if (priceList.size < 3) return null // Need some price history
            
            // Calculate volume and volatility metrics
            val volume = calculateVolume(history)
            val volatility = calculateVolatility(priceList)
            val marketContext = buildMarketContext(runtime)
            
            val analysis = openAIService.analyzeToken(
                tokenAddress, currentPrice, priceList, volume, volatility, marketContext
            )
            
            if (analysis != null) {
                // Cache the analysis
                analysisCache[tokenAddress] = Pair(analysis, System.currentTimeMillis())
            }
            
            analysis
        } catch (e: Exception) {
            logger.error("Error analyzing token $tokenAddress", e)
            null
        }
    }
    
    private fun shouldAnalyze(tokenAddress: String): Boolean {
        val cached = analysisCache[tokenAddress]
        return cached == null || System.currentTimeMillis() - cached.second > CACHE_DURATION_MS
    }
    
    private fun updatePriceHistory(runtime: TradingRuntime) {
        runtime.allTokens().forEach { token ->
            val history = priceHistory.getOrPut(token.address) { mutableListOf() }
            val currentPrice = calculateTokenUsdPrice(token, runtime)
            val timestamp = System.currentTimeMillis()
            
            history.add(Pair(currentPrice, timestamp))
            
            // Keep only recent history (last hour)
            val cutoff = timestamp - 3600_000L // 1 hour
            history.removeAll { it.second < cutoff }
            
            // Limit max history size
            if (history.size > cfg.lookbackPeriod) {
                history.removeAt(0)
            }
        }
    }
    
    private suspend fun processExistingPositions(runtime: TradingRuntime, now: Long) {
        val positionsToProcess = positions.keys.toList()
        
        positionsToProcess.forEach { tokenAddress ->
            val status = runtime.status(tokenAddress)
            val entryPrice = positions[tokenAddress] ?: return@forEach
            
            if (status?.state == TokenState.Swapped) {
                val tokenInfo = runtime.tokenInfo(tokenAddress)
                if (tokenInfo != null) {
                    val currentPrice = calculateTokenUsdPrice(tokenInfo, runtime)
                    val pnlPct = (currentPrice - entryPrice) / entryPrice
                    
                    // Check stop loss and take profit first
                    if (pnlPct <= -cfg.stopLossPct) {
                        sellPosition(tokenAddress, runtime, "🤖 AI Stop Loss: ${(pnlPct * 100).toInt()}%")
                        return@forEach
                    }
                    
                    if (pnlPct >= cfg.takeProfitPct) {
                        sellPosition(tokenAddress, runtime, "🤖 AI Take Profit: ${(pnlPct * 100).toInt()}%")
                        return@forEach
                    }
                    
                    // Get AI analysis for exit decision
                    try {
                        val analysis = analyzeToken(tokenAddress, runtime)
                        if (analysis != null) {
                            if (analysis.shouldSell && analysis.confidence > cfg.confidenceThreshold) {
                                sellPosition(tokenAddress, runtime, "🤖 AI Exit Signal: ${analysis.reasoning} (Confidence: ${(analysis.confidence * 100).toInt()}%)")
                                return@forEach
                            }
                            
                            // Risk-based exit
                            if (analysis.riskAssessment.equals("HIGH", ignoreCase = true) && 
                                analysis.confidence > 0.8) {
                                sellPosition(tokenAddress, runtime, "🤖 AI High Risk Exit: ${analysis.riskAssessment}")
                                return@forEach
                            }
                            
                            logger.debug("🤖 AI Strategy: Holding ${tokenAddress} - P&L: ${(pnlPct * 100).toInt()}%, AI Decision: ${if (analysis.shouldBuy) "HOLD/BUY" else if (analysis.shouldSell) "SELL" else "NEUTRAL"}")
                        }
                    } catch (e: Exception) {
                        logger.error("Error getting exit analysis for $tokenAddress", e)
                    }
                }
            }
        }
    }
    
    private suspend fun handleTimedSells(runtime: TradingRuntime, now: Long) {
        val expiredSells = plannedSells.entries.filter { it.value <= now }.toList()
        
        expiredSells.forEach { entry ->
            val status = runtime.status(entry.key)
            if (status?.state == TokenState.Swapped) {
                // Before timed sell, do a quick AI analysis
                try {
                    val analysis = analyzeToken(entry.key, runtime)
                    if (analysis?.shouldBuy == true && analysis.confidence > 0.7) {
                        // Extend hold time if AI is still bullish
                        plannedSells[entry.key] = now + (cfg.minHoldMs / 2)
                        logger.info("Extended hold time for ${entry.key} based on AI analysis")
                        return@forEach
                    }
                } catch (e: Exception) {
                    logger.error("Error in final analysis for ${entry.key}", e)
                }
                
                sellPosition(entry.key, runtime, "Timed Sell")
            } else {
                plannedSells.remove(entry.key)
            }
        }
    }
    
    private suspend fun sellPosition(tokenAddress: String, runtime: TradingRuntime, reason: String) {
        try {
            runtime.sell(tokenAddress)
            positions.remove(tokenAddress)
            plannedSells.remove(tokenAddress)
            logger.info("AI sold $tokenAddress: $reason")
        } catch (e: Exception) {
            logger.error("Error selling position $tokenAddress", e)
        }
    }
    
    private fun calculateVolume(history: List<Pair<Double, Long>>): Double {
        // Simplified volume calculation based on price movements
        if (history.size < 2) return 0.0
        
        var totalVolume = 0.0
        for (i in 1 until history.size) {
            val priceChange = kotlin.math.abs(history[i].first - history[i-1].first)
            totalVolume += priceChange * history[i].first // Price * change as volume proxy
        }
        
        return totalVolume / history.size
    }
    
    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        val returns = prices.zipWithNext { a, b -> (b - a) / a }
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        
        return kotlin.math.sqrt(variance)
    }
    
    private suspend fun buildMarketContext(runtime: TradingRuntime): String {
        return try {
            val allTokens = runtime.allTokens()
            val avgChange = allTokens.mapNotNull { token ->
                val history = priceHistory[token.address]
                if (history != null && history.size > 1) {
                    val first = history.first().first
                    val last = history.last().first
                    (last - first) / first
                } else null
            }.average()
            
            when {
                avgChange > 0.05 -> "Strong bullish market sentiment"
                avgChange > 0.01 -> "Moderate bullish sentiment" 
                avgChange < -0.05 -> "Strong bearish market sentiment"
                avgChange < -0.01 -> "Moderate bearish sentiment"
                else -> "Neutral market conditions"
            }
        } catch (e: Exception) {
            "Unknown market conditions"
        }
    }
    
    private fun cleanupCache(now: Long) {
        // Clean analysis cache
        analysisCache.entries.removeAll { (_, value) ->
            now - value.second > CACHE_DURATION_MS * 2
        }
    }
    
    private fun scheduleValidationCacheCleanup(tokenAddress: String) {
        // This is simplified - in production you might want a proper scheduler
        Thread {
            Thread.sleep(VALIDATION_CACHE_DURATION_MS)
            validatedTokens.remove(tokenAddress)
        }.start()
    }
}