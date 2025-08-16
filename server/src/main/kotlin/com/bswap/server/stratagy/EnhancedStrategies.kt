package com.bswap.server.stratagy

import com.bswap.addon.*
import com.bswap.server.*
import com.bswap.server.config.*
import com.bswap.server.protection.*
import com.bswap.server.service.JupiterLiquidityService
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Enhanced trading strategies with improved low-liquidity token handling
 * Preserves all original log message texts while adding advanced features
 */

// =================================================================================================
// ENHANCED SHITCOIN SCALPER STRATEGY
// =================================================================================================

class EnhancedShitcoinScalperStrategy(
    private val config: ShitcoinScalperConfig,
    private val enhancedConfig: EnhancedTradingConfig = EnhancedTradingConfig(),
    private val liquidityService: JupiterLiquidityService? = null
) : BaseStrategy("EnhancedShitcoinScalperStrategy"), TradingStrategy {
    
    override val type: StrategyType = StrategyType.SHITCOIN_SCALPER
    
    companion object {
        private val logger = LoggerFactory.getLogger(EnhancedShitcoinScalperStrategy::class.java)
    }

    // Protection systems
    private val positionManager = PositionManager(enhancedConfig.riskManagement)
    private val rugDetector = RugDetector(enhancedConfig.rugDetection)
    private val antiChopFilter = AntiChopFilter(enhancedConfig.antiChop)
    private val timeExitManager = TimeBasedExitManager(enhancedConfig.timeBasedExit)
    
    // Enhanced tracking
    private val liquidityAnalysis = ConcurrentHashMap<String, JupiterLiquidityService.LiquidityAnalysis>()
    private val priceImpactHistory = ConcurrentHashMap<String, MutableList<Double>>()
    private val volumeProfile = ConcurrentHashMap<String, VolumeProfile>()
    private val momentumScores = ConcurrentHashMap<String, Double>()
    
    data class VolumeProfile(
        val samples: MutableList<Double> = mutableListOf(),
        var avgVolume: Double = 0.0,
        var volumeSpike: Boolean = false,
        var lastUpdate: Long = System.currentTimeMillis()
    )

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (config.onlyNewTokens && !runtime.isNew(meta.mint)) {
            log.debug("SCALPER SKIP: ${meta.mint} - not a new token")
            return
        }
        
        if (!runtime.isNew(meta.mint)) {
            log.debug("SCALPER SKIP: ${meta.mint} - already processed")
            return
        }
        
        // Check position limits
        updateWalletUniverse(runtime)
        val currentPositions = positionManager.getPositionCount()
        
        if (currentPositions >= config.maxTokensHeld) {
            log.info("🚫 SCALPER LIMIT: ${meta.mint} - max positions reached ($currentPositions/${config.maxTokensHeld})")
            return
        }
        
        // Enhanced liquidity validation
        if (liquidityService != null && enhancedConfig.liquidityProtection.enablePreTradeValidation) {
            val liquidityAnalysis = liquidityService.analyzeLiquidity(meta.mint, runtime.config.solAmountToTrade.toDouble())
            if (liquidityAnalysis != null) {
                this.liquidityAnalysis[meta.mint] = liquidityAnalysis
                
                if (!liquidityAnalysis.isLiquid) {
                    log.info("💧 LIQUIDITY SKIP: ${meta.mint} - Insufficient liquidity: ${liquidityAnalysis.warnings.joinToString()}")
                    return
                }
                
                // Check price impact
                if (liquidityAnalysis.priceImpact > enhancedConfig.liquidityProtection.maxPriceImpactPercent) {
                    log.info("📊 PRICE IMPACT SKIP: ${meta.mint} - Impact too high: ${"%.2f".format(liquidityAnalysis.priceImpact)}%")
                    return
                }
                
                log.info("✅ LIQUIDITY VALIDATED: ${meta.mint} - Risk score: ${"%.2f".format(liquidityAnalysis.riskScore)}")
            }
        }
        
        // Pump token validation (if enabled)
        if (config.onlyPumpTokens && !isPumpToken(meta.mint)) {
            log.debug("SCALPER SKIP: ${meta.mint} - not a pump token")
            return
        }
        
        // Pool validation (if enabled)
        if (config.validatePools && isPumpToken(meta.mint)) {
            val poolValid = validatePumpFunPool(meta.mint, runtime)
            if (!poolValid) {
                log.info("SCALPER SKIP: ${meta.mint} - pump token pool validation failed")
                return
            }
        }
        
        // Get current price
        val currentPrice = try {
            runtime.getTokenUsdPrice(meta.mint) ?: 0.000001
        } catch (e: Exception) {
            log.warn("⚠️ PRICE ERROR: ${meta.mint} - ${e.message}")
            0.000001
        }
        
        // Enhanced entry confidence scoring
        val entryScore = calculateEntryConfidence(meta, currentPrice, runtime)
        if (entryScore < enhancedConfig.scalper.entryConfidenceThreshold) {
            log.info("🎯 CONFIDENCE SKIP: ${meta.mint} - Entry confidence too low: ${"%.2f".format(entryScore)}")
            return
        }
        
        log.info("🚀 SCALPER DISCOVERY: ${meta.mint} - New token buy (${currentPositions + 1}/${config.maxTokensHeld}) - Confidence: ${"%.2f".format(entryScore)}")
        
        // Execute buy
        val bought = runtime.buy(meta.mint)
        if (bought) {
            // Create position tracking
            val position = positionManager.addPosition(meta.mint, currentPrice, runtime.config.solAmountToTrade.toDouble())
            
            // Initialize volume profile
            volumeProfile[meta.mint] = VolumeProfile()
            
            log.info("✅ SCALPER BUY: ${meta.mint} - Price: ${"%.6f".format(currentPrice)} (Position ${currentPositions + 1}/${config.maxTokensHeld})")
        } else {
            log.error("❌ SCALPER BUY FAILED: ${meta.mint}")
        }
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        updateWalletUniverse(runtime)
        
        val walletTokens = runtime.allTokens().filter {
            it.tokenAmount.uiAmount != null && it.tokenAmount.uiAmount > 0.0
        }
        
        for (token in walletTokens) {
            val mint = token.address
            val position = positionManager.getPosition(mint) ?: continue
            val status = runtime.status(mint)
            
            if (status?.state != TokenState.Swapped) continue
            
            try {
                processPosition(position, token, runtime)
            } catch (e: Exception) {
                log.error("❌ POSITION PROCESSING ERROR: $mint - ${e.message}", e)
            }
        }
        
        // Cleanup old data
        rugDetector.cleanup()
        antiChopFilter.cleanup()
    }

    private suspend fun processPosition(position: Position, token: com.bswap.server.data.solana.transaction.TokenInfo, runtime: TradingRuntime) {
        val mint = position.mint
        val currentPrice = try {
            calculateTokenUsdPrice(token, runtime)
        } catch (e: Exception) {
            log.warn("⚠️ PRICE CALCULATION ERROR: $mint - ${e.message}")
            return
        }
        
        // Update position data
        positionManager.updatePosition(mint, currentPrice)
        
        // Update volume profile
        updateVolumeProfile(mint, currentPrice)
        
        // Rug detection
        val rugAnalysis = rugDetector.analyzeTick(mint, currentPrice, 1000.0) // Placeholder volume
        if (rugAnalysis.isRugPull && enhancedConfig.rugDetection.emergencyExitOnRug) {
            log.warn("🚨 RUG DETECTED: $mint - Emergency exit! Confidence: ${"%.1f".format(rugAnalysis.confidence * 100)}%")
            sellPosition(runtime, mint, "rug_detected_${rugAnalysis.urgency}")
            return
        }
        
        // Anti-chop filter
        val marketState = antiChopFilter.analyzeMarket(mint, position.priceHistory)
        if (!antiChopFilter.shouldAllowTrade(mint)) {
            log.debug("🌊 CHOP FILTER: $mint - Trading paused due to choppy conditions")
            return
        }
        
        // Enhanced exit analysis
        val exitDecision = analyzeEnhancedExit(position, currentPrice, runtime)
        if (exitDecision.shouldExit) {
            sellPosition(runtime, mint, exitDecision.reason)
            return
        }
        
        // Update momentum score
        updateMomentumScore(mint, position)
        
        log.debug("👀 SCALPER MONITOR: $mint - P&L: ${"%.1f".format(position.unrealizedPnLPercent * 100)}%, Vol: ${"%.2f".format(position.volatility)}, Hold: ${position.holdTimeMs}ms")
    }

    private fun calculateEntryConfidence(meta: TokenMeta, price: Double, runtime: TradingRuntime): Double {
        var confidence = 0.5 // Base confidence
        
        // Liquidity score
        val liquidityAnalysis = liquidityAnalysis[meta.mint]
        if (liquidityAnalysis != null) {
            confidence += (1.0 - liquidityAnalysis.riskScore) * 0.3
        }
        
        // Source confidence
        confidence += when (meta.source) {
            TokenSource.PUMPFUN -> 0.2
            TokenSource.PROFILE -> 0.1
            TokenSource.BOOST -> 0.15
        }
        
        // Price stability (prefer non-volatile entries)
        if (price > 0.0) {
            confidence += 0.1
        }
        
        return confidence.coerceIn(0.0, 1.0)
    }

    private fun updateVolumeProfile(mint: String, currentPrice: Double) {
        val profile = volumeProfile.getOrPut(mint) { VolumeProfile() }
        val now = System.currentTimeMillis()
        
        // Add current price as volume proxy
        profile.samples.add(currentPrice)
        if (profile.samples.size > 20) {
            profile.samples.removeAt(0)
        }
        
        // Calculate volume metrics
        if (profile.samples.size >= 3) {
            val recent = profile.samples.takeLast(3).average()
            val historical = profile.samples.dropLast(3).average()
            
            profile.volumeSpike = recent > historical * 1.5 // 50% increase
        }
        
        profile.lastUpdate = now
    }

    private fun updateMomentumScore(mint: String, position: Position) {
        if (position.priceHistory.size < enhancedConfig.scalper.momentumPeriods) return
        
        val recentPrices = position.priceHistory.takeLast(enhancedConfig.scalper.momentumPeriods)
        val momentum = (recentPrices.last() - recentPrices.first()) / recentPrices.first()
        
        momentumScores[mint] = momentum
    }

    private suspend fun analyzeEnhancedExit(position: Position, currentPrice: Double, runtime: TradingRuntime): ExitDecision {
        val mint = position.mint
        val pnlPercent = position.unrealizedPnLPercent
        val holdTime = position.holdTimeMs
        
        // 1. Hard stop loss (never override)
        if (pnlPercent <= -enhancedConfig.riskManagement.hardStopLossPercent / 100.0) {
            return ExitDecision(true, "hard_stop_${"%.1f".format(pnlPercent * 100)}%")
        }
        
        // 2. Emergency stop loss
        if (pnlPercent <= -enhancedConfig.riskManagement.emergencyStopLossPercent / 100.0) {
            return ExitDecision(true, "emergency_stop_${"%.1f".format(pnlPercent * 100)}%")
        }
        
        // 3. Time-based exit analysis
        val timeExitRecommendation = timeExitManager.analyzeTimeBasedExit(position)
        if (timeExitRecommendation.shouldExit) {
            return ExitDecision(true, timeExitRecommendation.reason)
        }
        
        // 4. Enhanced profit taking with volatility adjustment
        val volatilityAdjustedTarget = config.profitTakePercent * (1.0 + position.volatility * enhancedConfig.scalper.volatilityScalingFactor)
        if (pnlPercent >= volatilityAdjustedTarget) {
            return ExitDecision(true, "volatility_adjusted_profit_${"%.1f".format(pnlPercent * 100)}%")
        }
        
        // 5. Momentum-based exit
        val momentum = momentumScores[mint] ?: 0.0
        if (enhancedConfig.scalper.enableMomentumFiltering && momentum < -0.02 && pnlPercent > 0.005) {
            return ExitDecision(true, "momentum_reversal_${"%.1f".format(pnlPercent * 100)}%")
        }
        
        // 6. Volume-based exit
        val volumeProfile = volumeProfile[mint]
        if (enhancedConfig.scalper.enableVolumeConfirmation && volumeProfile?.volumeSpike == true && pnlPercent > 0.002) {
            return ExitDecision(true, "volume_spike_exit_${"%.1f".format(pnlPercent * 100)}%")
        }
        
        // 7. Trailing stop (if enabled)
        if (position.trailingStopPrice != null && currentPrice <= position.trailingStopPrice!!) {
            return ExitDecision(true, "trailing_stop_${"%.1f".format(pnlPercent * 100)}%")
        }
        
        // 8. Enable trailing stop if profitable enough
        if (!position.trailingStopEnabled && pnlPercent >= config.minProfitBeforeTrailing) {
            position.trailingStopEnabled = true
            position.trailingStopPrice = position.peak * (1.0 - config.trailingStopPercent)
            log.info("📈 TRAILING STOP ENABLED: $mint at ${"%.6f".format(position.trailingStopPrice)}")
        }
        
        // 9. Update trailing stop
        if (position.trailingStopEnabled && position.trailingStopPrice != null) {
            val newTrailingStop = position.peak * (1.0 - config.trailingStopPercent)
            if (newTrailingStop > position.trailingStopPrice!!) {
                position.trailingStopPrice = newTrailingStop
            }
        }
        
        return ExitDecision(false, "holding")
    }

    data class ExitDecision(
        val shouldExit: Boolean,
        val reason: String
    )

    private suspend fun sellPosition(runtime: TradingRuntime, mint: String, reason: String) {
        val position = positionManager.getPosition(mint)
        if (position == null) {
            log.warn("⚠️ SELL ATTEMPT: $mint position not found")
            return
        }
        
        log.info("💸 SCALPER SELL: $mint - Reason: $reason, P&L: ${"%.1f".format(position.unrealizedPnLPercent * 100)}%, Hold: ${position.holdTimeMs}ms")
        
        val success = runtime.sell(mint)
        if (success) {
            // Cleanup position data
            positionManager.removePosition(mint)
            liquidityAnalysis.remove(mint)
            priceImpactHistory.remove(mint)
            volumeProfile.remove(mint)
            momentumScores.remove(mint)
            
            log.info("✅ SCALPER SOLD: $mint - Final P&L: ${"%.1f".format(position.unrealizedPnLPercent * 100)}%")
        } else {
            log.warn("❌ SCALPER SELL FAILED: $mint")
        }
    }

    private fun isPumpToken(mint: String): Boolean {
        return mint.length in 32..44 &&
               mint.matches(Regex("[1-9A-HJ-NP-Za-km-z]+")) &&
               !mint.startsWith("So1111") &&
               !mint.startsWith("EPjFWdd") &&
               !mint.startsWith("Es9vMF")
    }

    private suspend fun validatePumpFunPool(mint: String, runtime: TradingRuntime): Boolean {
        return try {
            val tokenInfo = runtime.tokenInfo(mint)
            if (tokenInfo != null) {
                log.info("✅ POOL CHECK: $mint - token info available, allowing trade")
                true
            } else {
                val price = runtime.getTokenUsdPrice(mint)
                if (price != null && price > 0.0) {
                    log.info("✅ POOL CHECK: $mint - price available (${"%.6f".format(price)}), allowing trade")
                    true
                } else if (runtime.config.priceService.allowBuyWithoutPrice) {
                    log.info("✅ POOL CHECK: $mint - allowing new pump token (no price data yet)")
                    true
                } else {
                    log.info("❌ POOL CHECK: $mint - no token info or price data available")
                    false
                }
            }
        } catch (e: Exception) {
            log.warn("❌ POOL CHECK: $mint - validation error: ${e.message}")
            if (runtime.config.priceService.allowBuyWithoutPrice) {
                log.info("✅ POOL CHECK: $mint - allowing despite error (permissive mode)")
                true
            } else {
                false
            }
        }
    }
}

// =================================================================================================
// ENHANCED RSI STRATEGY
// =================================================================================================

class EnhancedRSIStrategy(
    private val config: RsiBasedConfig,
    private val enhancedConfig: EnhancedTradingConfig = EnhancedTradingConfig()
) : BaseStrategy("EnhancedRSIStrategy"), TradingStrategy {
    
    override val type: StrategyType = StrategyType.RSI_BASED
    
    companion object {
        private val logger = LoggerFactory.getLogger(EnhancedRSIStrategy::class.java)
    }

    private val positionManager = PositionManager(enhancedConfig.riskManagement)
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()
    private val rsiValues = ConcurrentHashMap<String, MutableList<Double>>()
    private val adaptivePeriods = ConcurrentHashMap<String, Int>()
    
    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        val isNew = runtime.isNew(meta.mint)
        log.info("🏢 RSI DISCOVER: ${meta.mint} (${meta.source}) - IsNew: $isNew")
        
        if (!isNew) {
            log.info("🔄 RSI SKIP: ${meta.mint} - already processed")
            return
        }
        
        // Initialize tracking
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
        rsiValues.putIfAbsent(meta.mint, mutableListOf())
        
        // Set initial adaptive period
        if (enhancedConfig.rsi.adaptivePeriods) {
            adaptivePeriods[meta.mint] = enhancedConfig.rsi.minPeriod
        }
        
        // Load initial price history
        loadInitialPriceHistory(meta.mint, runtime)
        
        // Enhanced RSI analysis
        val shouldBuy = analyzeEnhancedRSIEntry(meta.mint, runtime)
        
        if (shouldBuy) {
            log.info("🚀 RSI BUY: Attempting ${meta.mint} - Enhanced RSI Strategy triggered buy signal")
            val bought = runtime.buy(meta.mint)
            if (bought) {
                val currentPrice = runtime.getTokenUsdPrice(meta.mint) ?: 0.0
                positionManager.addPosition(meta.mint, currentPrice, runtime.config.solAmountToTrade.toDouble())
                log.info("✅ RSI BUY SUCCESS: ${meta.mint}")
            } else {
                log.error("❌ RSI BUY FAILED: ${meta.mint} - runtime.buy() returned false")
            }
        }
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        updateWalletUniverse(runtime)
        
        val walletTokens = runtime.allTokens().filter {
            it.tokenAmount.uiAmount != null && it.tokenAmount.uiAmount > 0.0
        }
        
        for (token in walletTokens) {
            val mint = token.address
            val position = positionManager.getPosition(mint) ?: continue
            val status = runtime.status(mint)
            
            if (status?.state != TokenState.Swapped) continue
            
            try {
                processRSIPosition(position, token, runtime)
            } catch (e: Exception) {
                log.error("❌ RSI POSITION ERROR: $mint - ${e.message}", e)
            }
        }
    }

    private suspend fun analyzeEnhancedRSIEntry(mint: String, runtime: TradingRuntime): Boolean {
        val history = priceHistory[mint] ?: return false
        val period = if (enhancedConfig.rsi.adaptivePeriods) {
            adaptivePeriods[mint] ?: enhancedConfig.rsi.minPeriod
        } else {
            config.period
        }
        
        if (history.size < period) {
            // Fallback for insufficient data
            val priceAllowed = shouldAllowRsiBuy(mint, runtime)
            log.info("🚀 RSI FALLBACK: $mint - immediate buy (insufficient history), Price OK: $priceAllowed")
            return priceAllowed
        }
        
        val rsiValue = rsi(history, period)
        if (rsiValue == null) {
            log.warn("⚠️ RSI CALCULATION: $mint - failed to calculate RSI")
            return false
        }
        
        // Store RSI value
        val rsiHistory = rsiValues.getOrPut(mint) { mutableListOf() }
        rsiHistory.add(rsiValue)
        if (rsiHistory.size > 50) rsiHistory.removeAt(0)
        
        // Enhanced RSI analysis
        val oversoldLevel = if (enhancedConfig.rsi.adaptivePeriods) {
            enhancedConfig.rsi.oversoldLevel
        } else {
            config.oversoldThreshold
        }
        
        val isOversold = rsiValue <= oversoldLevel
        val priceAllowed = shouldAllowRsiBuy(mint, runtime)
        
        // Divergence analysis (if enabled)
        var hasBullishDivergence = false
        if (enhancedConfig.rsi.enableRSIDivergence && history.size >= enhancedConfig.rsi.divergenceLookbackPeriods) {
            hasBullishDivergence = detectBullishDivergence(mint, history, rsiHistory)
        }
        
        val buyDecision = (isOversold || hasBullishDivergence) && priceAllowed
        
        log.info("🔍 ENHANCED RSI: $mint - RSI: ${"%.2f".format(rsiValue)}, Oversold: $isOversold, Divergence: $hasBullishDivergence, Decision: $buyDecision")
        
        return buyDecision
    }

    private suspend fun processRSIPosition(position: Position, token: com.bswap.server.data.solana.transaction.TokenInfo, runtime: TradingRuntime) {
        val mint = position.mint
        val currentPrice = try {
            calculateTokenUsdPrice(token, runtime)
        } catch (e: Exception) {
            log.warn("⚠️ RSI PRICE ERROR: $mint - ${e.message}")
            return
        }
        
        // Update position and price history
        positionManager.updatePosition(mint, currentPrice)
        val history = priceHistory.getOrPut(mint) { mutableListOf() }
        history.add(currentPrice)
        if (history.size > config.period * 3) history.removeAt(0)
        
        // Adaptive period adjustment
        if (enhancedConfig.rsi.adaptivePeriods) {
            adjustAdaptivePeriod(mint, history)
        }
        
        // Calculate current RSI
        val period = adaptivePeriods[mint] ?: config.period
        val currentRsi = if (history.size >= period) {
            rsi(history, period)
        } else null
        
        if (currentRsi == null) {
            log.debug("⚠️ RSI CALCULATION: $mint - insufficient data for RSI")
            return
        }
        
        // Store RSI value
        val rsiHistory = rsiValues.getOrPut(mint) { mutableListOf() }
        rsiHistory.add(currentRsi)
        if (rsiHistory.size > 50) rsiHistory.removeAt(0)
        
        // Enhanced exit analysis
        val exitDecision = analyzeEnhancedRSIExit(position, currentRsi, rsiHistory, currentPrice)
        
        if (exitDecision.shouldExit) {
            log.info("🔥 RSI SELL: $mint - ${exitDecision.reason}, P&L: ${"%.1f".format(position.unrealizedPnLPercent * 100)}%")
            val success = runtime.sell(mint)
            if (success) {
                positionManager.removePosition(mint)
                priceHistory.remove(mint)
                rsiValues.remove(mint)
                adaptivePeriods.remove(mint)
                log.info("✅ RSI SOLD: $mint - Final P&L: ${"%.1f".format(position.unrealizedPnLPercent * 100)}%")
            }
        }
    }

    private fun detectBullishDivergence(mint: String, priceHistory: List<Double>, rsiHistory: List<Double>): Boolean {
        if (priceHistory.size < enhancedConfig.rsi.divergenceLookbackPeriods || 
            rsiHistory.size < enhancedConfig.rsi.divergenceLookbackPeriods) {
            return false
        }
        
        val lookback = enhancedConfig.rsi.divergenceLookbackPeriods
        val recentPrices = priceHistory.takeLast(lookback)
        val recentRSI = rsiHistory.takeLast(lookback)
        
        // Find price and RSI lows
        val priceLowIndex = recentPrices.indices.minByOrNull { recentPrices[it] } ?: return false
        val rsiLowIndex = recentRSI.indices.minByOrNull { recentRSI[it] } ?: return false
        
        // Check for divergence: price making lower lows, RSI making higher lows
        if (priceLowIndex != rsiLowIndex) {
            val priceSlope = (recentPrices.last() - recentPrices[priceLowIndex]) / (recentPrices.size - priceLowIndex)
            val rsiSlope = (recentRSI.last() - recentRSI[rsiLowIndex]) / (recentRSI.size - rsiLowIndex)
            
            return priceSlope < 0 && rsiSlope > 0 // Price down, RSI up = bullish divergence
        }
        
        return false
    }

    private fun adjustAdaptivePeriod(mint: String, history: List<Double>) {
        if (history.size < enhancedConfig.rsi.maxPeriod) return
        
        // Calculate volatility to adjust period
        val recentVolatility = calculateVolatility(history.takeLast(enhancedConfig.rsi.maxPeriod))
        
        // Higher volatility = shorter period, lower volatility = longer period
        val newPeriod = when {
            recentVolatility > 0.05 -> enhancedConfig.rsi.minPeriod
            recentVolatility > 0.02 -> (enhancedConfig.rsi.minPeriod + enhancedConfig.rsi.maxPeriod) / 2
            else -> enhancedConfig.rsi.maxPeriod
        }
        
        adaptivePeriods[mint] = newPeriod
    }

    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        val returns = prices.zipWithNext { a, b -> ln(b / a) }
        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    private fun analyzeEnhancedRSIExit(position: Position, currentRsi: Double, rsiHistory: List<Double>, currentPrice: Double): ExitDecision {
        val pnlPercent = position.unrealizedPnLPercent
        
        // 1. RSI overbought exit
        val overboughtLevel = if (enhancedConfig.rsi.adaptivePeriods) {
            enhancedConfig.rsi.overboughtLevel
        } else {
            config.overboughtThreshold
        }
        
        if (currentRsi >= overboughtLevel) {
            return ExitDecision(true, "RSI_overbought_${"%.1f".format(currentRsi)}")
        }
        
        // 2. Extreme RSI levels
        if (currentRsi >= enhancedConfig.rsi.rsiExtremeThreshold + 70) { // Above 85
            return ExitDecision(true, "RSI_extreme_high_${"%.1f".format(currentRsi)}")
        }
        
        // 3. Bearish divergence
        if (enhancedConfig.rsi.enableRSIDivergence && rsiHistory.size >= enhancedConfig.rsi.divergenceLookbackPeriods) {
            val hasBearishDivergence = detectBearishDivergence(position.priceHistory, rsiHistory)
            if (hasBearishDivergence && pnlPercent > 0.01) {
                return ExitDecision(true, "RSI_bearish_divergence")
            }
        }
        
        // 4. RSI momentum reversal
        if (rsiHistory.size >= 3) {
            val rsiMomentum = currentRsi - rsiHistory[rsiHistory.size - 3]
            if (rsiMomentum < -10 && currentRsi > 60) { // Rapid RSI decline from high levels
                return ExitDecision(true, "RSI_momentum_reversal_${"%.1f".format(rsiMomentum)}")
            }
        }
        
        return ExitDecision(false, "RSI_holding")
    }

    private fun detectBearishDivergence(priceHistory: List<Double>, rsiHistory: List<Double>): Boolean {
        if (priceHistory.size < enhancedConfig.rsi.divergenceLookbackPeriods || 
            rsiHistory.size < enhancedConfig.rsi.divergenceLookbackPeriods) {
            return false
        }
        
        val lookback = enhancedConfig.rsi.divergenceLookbackPeriods
        val recentPrices = priceHistory.takeLast(lookback)
        val recentRSI = rsiHistory.takeLast(lookback)
        
        // Find price and RSI highs
        val priceHighIndex = recentPrices.indices.maxByOrNull { recentPrices[it] } ?: return false
        val rsiHighIndex = recentRSI.indices.maxByOrNull { recentRSI[it] } ?: return false
        
        // Check for divergence: price making higher highs, RSI making lower highs
        if (priceHighIndex != rsiHighIndex) {
            val priceSlope = (recentPrices.last() - recentPrices[priceHighIndex]) / (recentPrices.size - priceHighIndex)
            val rsiSlope = (recentRSI.last() - recentRSI[rsiHighIndex]) / (recentRSI.size - rsiHighIndex)
            
            return priceSlope > 0 && rsiSlope < 0 // Price up, RSI down = bearish divergence
        }
        
        return false
    }

    private suspend fun loadInitialPriceHistory(mint: String, runtime: TradingRuntime) {
        try {
            log.info("📊 RSI PRICE LOAD: Attempting to load history for $mint")
            
            val historicalPrices = runtime.getPriceHistory?.invoke(mint)
            if (historicalPrices != null && historicalPrices.isNotEmpty()) {
                val history = priceHistory.getOrPut(mint) { mutableListOf() }
                val pricesToAdd = historicalPrices.takeLast(config.period * 2)
                history.addAll(pricesToAdd)
                
                log.info("✅ RSI PRICE HISTORY LOADED: $mint - Total: ${historicalPrices.size}, Added: ${pricesToAdd.size}")
                
                // Calculate initial RSI
                if (history.size >= config.period) {
                    val initialRsi = rsi(history, config.period)
                    log.info("📊 RSI INITIAL CALCULATION: $mint - RSI: ${initialRsi?.let { "%.2f".format(it) } ?: "null"}")
                }
            } else {
                log.error("❌ RSI PRICE LOAD FAILED: $mint - no historical data available")
            }
        } catch (e: Exception) {
            log.error("❌ RSI PRICE LOAD ERROR: $mint - ${e.message}", e)
        }
    }

    data class ExitDecision(
        val shouldExit: Boolean,
        val reason: String
    )
}

// =================================================================================================
// HELPER FUNCTION (unchanged from original)
// =================================================================================================

private suspend fun calculateTokenUsdPrice(
    token: com.bswap.server.data.solana.transaction.TokenInfo,
    runtime: TradingRuntime
): Double {
    val usdPrice = runtime.getTokenUsdPrice(token.address)

    return when {
        usdPrice != null && usdPrice > 0.0 -> usdPrice
        token.tokenAmount.uiAmount != null && token.tokenAmount.uiAmount > 0.0 ->
            token.tokenAmount.uiAmount
        else -> 0.000001
    }
}