package com.bswap.server.protection

import com.bswap.server.config.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/**
 * Comprehensive trading protection system for low-liquidity tokens
 * Preserves all original log message texts and provides robust safeguards
 */

// =================================================================================================
// POSITION MANAGEMENT & RISK CONTROL
// =================================================================================================

data class Position(
    val mint: String,
    val entryPrice: Double,
    var entryTime: Long,
    val amountUsd: Double,
    var currentPrice: Double = entryPrice,
    var lastUpdate: Long = entryTime,
    var peak: Double = entryPrice,
    var trough: Double = entryPrice,
    var trailingStopPrice: Double? = null,
    var trailingStopEnabled: Boolean = false,
    var breakevenArmed: Boolean = false,
    val priceHistory: MutableList<Double> = mutableListOf(entryPrice),
    var riskScore: Double = 0.0,
    var volatility: Double = 0.0
) {
    val holdTimeMs: Long get() = System.currentTimeMillis() - entryTime
    val unrealizedPnLPercent: Double get() = (currentPrice - entryPrice) / entryPrice
    val isProfit: Boolean get() = unrealizedPnLPercent > 0.0
    val drawdownFromPeak: Double get() = (peak - currentPrice) / peak
    val quantity: Double get() = amountUsd / entryPrice
}

class PositionManager(private val config: RiskManagementConfig) {
    companion object {
        private val logger = LoggerFactory.getLogger(PositionManager::class.java)
    }

    private val positions = ConcurrentHashMap<String, Position>()
    private val totalPnL = AtomicLong(0)
    private val maxDrawdown = AtomicLong(0)

    fun addPosition(mint: String, entryPrice: Double, amountUsd: Double): Position {
        val position = Position(
            mint = mint,
            entryPrice = entryPrice,
            entryTime = System.currentTimeMillis(),
            amountUsd = amountUsd
        )
        positions[mint] = position
        logger.info("📊 POSITION ADDED: $mint at ${String.format("%.6f", entryPrice)}")
        return position
    }

    fun updatePosition(mint: String, currentPrice: Double): Position? {
        val position = positions[mint] ?: return null
        
        position.currentPrice = currentPrice
        position.lastUpdate = System.currentTimeMillis()
        position.priceHistory.add(currentPrice)
        
        // Update peak and trough
        if (currentPrice > position.peak) position.peak = currentPrice
        if (currentPrice < position.trough) position.trough = currentPrice
        
        // Calculate volatility
        if (position.priceHistory.size > 2) {
            position.volatility = calculateVolatility(position.priceHistory.takeLast(config.volatilityLookbackPeriods))
        }
        
        // Keep price history manageable
        if (position.priceHistory.size > config.volatilityLookbackPeriods * 2) {
            position.priceHistory.removeAt(0)
        }
        
        return position
    }

    fun removePosition(mint: String): Position? {
        val position = positions.remove(mint)
        if (position != null) {
            logger.info("📊 POSITION REMOVED: $mint with P&L: ${String.format("%.2f", position.unrealizedPnLPercent * 100)}%")
        }
        return position
    }

    fun getPosition(mint: String): Position? = positions[mint]
    
    fun getAllPositions(): List<Position> = positions.values.toList()
    
    fun getPositionCount(): Int = positions.size
    
    fun cleanup() {
        // Remove old positions that are no longer active
        val cutoff = System.currentTimeMillis() - 3600_000L // 1 hour
        val toRemove = positions.filter { it.value.entryTime < cutoff }.keys
        toRemove.forEach { positions.remove(it) }
        if (toRemove.isNotEmpty()) {
            logger.debug("🧹 CLEANUP: Removed ${toRemove.size} old positions")
        }
    }
    
    fun getTotalValue(): Double = positions.values.sumOf { it.quantity * it.currentPrice }
    
    fun getTotalPnL(): Double = positions.values.sumOf { 
        (it.currentPrice - it.entryPrice) * it.quantity 
    }

    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        val returns = prices.zipWithNext { a, b -> ln(b / a) }
        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }
}

// =================================================================================================
// RUG PULL DETECTION SYSTEM
// =================================================================================================

class RugDetector(private val config: RugDetectionConfig) {
    companion object {
        private val logger = LoggerFactory.getLogger(RugDetector::class.java)
    }

    private val tickAnalysis = ConcurrentHashMap<String, MutableList<TickData>>()
    private val liquidityHistory = ConcurrentHashMap<String, MutableList<LiquiditySnapshot>>()
    private val rugAlerts = ConcurrentHashMap<String, Long>()

    data class TickData(
        val price: Double,
        val volume: Double,
        val timestamp: Long,
        val priceChange: Double
    )

    data class LiquiditySnapshot(
        val reserves: Double,
        val timestamp: Long
    )

    data class RugAnalysis(
        val isRugPull: Boolean,
        val confidence: Double,
        val reasons: List<String>,
        val urgency: RugUrgency
    )

    enum class RugUrgency {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    fun analyzeTick(mint: String, price: Double, volume: Double): RugAnalysis {
        val ticks = tickAnalysis.getOrPut(mint) { mutableListOf() }
        val now = System.currentTimeMillis()
        
        // Calculate price change from previous tick
        val priceChange = if (ticks.isNotEmpty()) {
            val prevPrice = ticks.last().price
            (price - prevPrice) / prevPrice
        } else 0.0

        val tickData = TickData(price, volume, now, priceChange)
        ticks.add(tickData)

        // Keep only recent ticks
        val cutoff = now - (config.rugDetectionWindow * 1000L)
        ticks.removeIf { it.timestamp < cutoff }

        return if (ticks.size >= config.minTicksForRugDetection) {
            performRugAnalysis(mint, ticks)
        } else {
            RugAnalysis(false, 0.0, emptyList(), RugUrgency.LOW)
        }
    }

    private fun performRugAnalysis(mint: String, ticks: List<TickData>): RugAnalysis {
        val reasons = mutableListOf<String>()
        var rugScore = 0.0

        // 1. Rapid price drop detection
        val recentTicks = ticks.takeLast(config.rugDetectionWindow)
        val majorDrops = recentTicks.count { it.priceChange <= -config.tickDropThresholdPercent / 100.0 }
        
        if (majorDrops >= config.rugDetectionWindow / 2) {
            reasons.add("Rapid price drops detected: $majorDrops/${config.rugDetectionWindow} ticks")
            rugScore += 0.4
        }

        // 2. Volume analysis
        if (recentTicks.size >= 2) {
            val avgRecentVolume = recentTicks.takeLast(3).map { it.volume }.average()
            val avgHistoricalVolume = ticks.dropLast(3).map { it.volume }.average()
            
            if (avgHistoricalVolume > 0 && avgRecentVolume < avgHistoricalVolume * (1 - config.volumeDropThresholdPercent / 100.0)) {
                reasons.add("Volume drop: ${String.format("%.1f", (1 - avgRecentVolume / avgHistoricalVolume) * 100)}%")
                rugScore += 0.3
            }
        }

        // 3. Price velocity analysis
        if (recentTicks.size >= 2) {
            val timeSpan = (recentTicks.last().timestamp - recentTicks.first().timestamp) / 1000.0
            val priceVelocity = if (timeSpan > 0) {
                val priceChange = (recentTicks.last().price - recentTicks.first().price) / recentTicks.first().price
                abs(priceChange) / timeSpan * 100 // %/second
            } else 0.0

            if (priceVelocity > config.priceVelocityThreshold) {
                reasons.add("High price velocity: ${String.format("%.1f", priceVelocity)}%/sec")
                rugScore += 0.3
            }
        }

        val isRugPull = rugScore >= config.rugConfidenceThreshold
        val urgency = when {
            rugScore >= 0.9 -> RugUrgency.CRITICAL
            rugScore >= 0.7 -> RugUrgency.HIGH
            rugScore >= 0.5 -> RugUrgency.MEDIUM
            else -> RugUrgency.LOW
        }

        if (isRugPull) {
            rugAlerts[mint] = System.currentTimeMillis()
            logger.warn("🚨 RUG DETECTED: $mint - Confidence: ${String.format("%.1f", rugScore * 100)}%, Reasons: ${reasons.joinToString(", ")}")
        }

        return RugAnalysis(isRugPull, rugScore, reasons, urgency)
    }

    fun addLiquiditySnapshot(mint: String, reserves: Double) {
        if (!config.enableLiquidityRugDetection) return

        val snapshots = liquidityHistory.getOrPut(mint) { mutableListOf() }
        snapshots.add(LiquiditySnapshot(reserves, System.currentTimeMillis()))

        // Keep only recent snapshots
        val cutoff = System.currentTimeMillis() - 300_000L // 5 minutes
        snapshots.removeIf { it.timestamp < cutoff }

        // Check for liquidity rug
        if (snapshots.size >= 2) {
            val latest = snapshots.last()
            val previous = snapshots[snapshots.size - 2]
            val liquidityDrop = (previous.reserves - latest.reserves) / previous.reserves

            if (liquidityDrop > config.liquidityDropThresholdPercent / 100.0) {
                logger.warn("🏊 LIQUIDITY RUG: $mint - Drop: ${String.format("%.1f", liquidityDrop * 100)}%")
            }
        }
    }

    fun isRecentRugAlert(mint: String): Boolean {
        val alertTime = rugAlerts[mint] ?: return false
        return System.currentTimeMillis() - alertTime < 60_000L // 1 minute
    }

    fun cleanup() {
        val cutoff = System.currentTimeMillis() - 300_000L // 5 minutes
        tickAnalysis.values.forEach { ticks ->
            ticks.removeIf { it.timestamp < cutoff }
        }
        liquidityHistory.values.forEach { snapshots ->
            snapshots.removeIf { it.timestamp < cutoff }
        }
    }
}

// =================================================================================================
// ANTI-CHOP FILTER
// =================================================================================================

class AntiChopFilter(private val config: AntiChopConfig) {
    companion object {
        private val logger = LoggerFactory.getLogger(AntiChopFilter::class.java)
    }

    private val marketAnalysis = ConcurrentHashMap<String, MarketState>()
    private val chopDetectionHistory = ConcurrentHashMap<String, MutableList<Double>>()
    private var consecutiveChopTrades = 0
    private var lastChopDetection = 0L

    data class MarketState(
        val isChoppy: Boolean,
        val choppiness: Double,
        val pauseUntil: Long,
        val recommendation: TradingAction
    )

    enum class TradingAction {
        NORMAL, REDUCE_SIZE, PAUSE, TIGHTEN_STOPS, FILTER_SIGNALS
    }

    fun analyzeMarket(mint: String, prices: List<Double>): MarketState {
        if (!config.enableChopDetection || prices.size < config.choppyDetectionPeriods) {
            return MarketState(false, 0.0, 0L, TradingAction.NORMAL)
        }

        val recentPrices = prices.takeLast(config.choppyDetectionPeriods)
        val choppiness = calculateChoppiness(recentPrices)
        val isChoppy = choppiness > config.choppyMarketThreshold

        val now = System.currentTimeMillis()
        var pauseUntil = 0L
        var action = TradingAction.NORMAL

        if (isChoppy) {
            consecutiveChopTrades++
            lastChopDetection = now

            action = when (config.antiChopMode) {
                AntiChopMode.PAUSE_TRADING -> {
                    pauseUntil = now + config.choppyMarketPauseDurationMs
                    TradingAction.PAUSE
                }
                AntiChopMode.REDUCE_SIZE -> TradingAction.REDUCE_SIZE
                AntiChopMode.INCREASE_STOPS -> TradingAction.TIGHTEN_STOPS
                AntiChopMode.FILTER_SIGNALS -> TradingAction.FILTER_SIGNALS
            }

            if (consecutiveChopTrades >= config.maxConsecutiveChopTrades) {
                pauseUntil = now + config.chopRecoveryWaitMs
                action = TradingAction.PAUSE
                logger.warn("🌊 CHOP FILTER: $mint - Excessive chop detected, pausing trading")
            }

            logger.info("🌊 CHOP DETECTED: $mint - Choppiness: ${String.format("%.2f", choppiness)}, Action: $action")
        } else {
            if (now - lastChopDetection > config.chopRecoveryWaitMs) {
                consecutiveChopTrades = 0
            }
        }

        val state = MarketState(isChoppy, choppiness, pauseUntil, action)
        marketAnalysis[mint] = state
        return state
    }

    private fun calculateChoppiness(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0

        val high = prices.maxOrNull() ?: return 0.0
        val low = prices.minOrNull() ?: return 0.0
        val range = high - low

        if (range == 0.0) return 0.0

        // Calculate true range sum
        val trueRanges = prices.zipWithNext { prev, curr ->
            maxOf(
                abs(curr - prev),
                abs(high - prev),
                abs(low - prev)
            )
        }

        val trueRangeSum = trueRanges.sum()
        val rangeRatio = if (trueRangeSum > 0) range / trueRangeSum else 0.0

        // Choppiness index (normalized)
        return if (rangeRatio > 0) {
            100 * ln(trueRangeSum / range) / ln(prices.size.toDouble())
        } else 100.0
    }

    fun shouldAllowTrade(mint: String): Boolean {
        val state = marketAnalysis[mint] ?: return true
        val now = System.currentTimeMillis()

        return when {
            state.pauseUntil > now -> false
            state.recommendation == TradingAction.PAUSE -> false
            consecutiveChopTrades >= config.maxConsecutiveChopTrades -> false
            else -> true
        }
    }

    fun getPositionSizeMultiplier(mint: String): Double {
        val state = marketAnalysis[mint] ?: return 1.0
        
        return when (state.recommendation) {
            TradingAction.REDUCE_SIZE -> 0.5
            TradingAction.FILTER_SIGNALS -> 0.7
            TradingAction.PAUSE -> 0.0
            else -> 1.0
        }
    }

    fun getStopLossMultiplier(mint: String): Double {
        val state = marketAnalysis[mint] ?: return 1.0
        
        return when (state.recommendation) {
            TradingAction.TIGHTEN_STOPS -> 0.7
            TradingAction.FILTER_SIGNALS -> 0.8
            else -> 1.0
        }
    }
    
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - 300_000L // 5 minutes
        chopDetectionHistory.values.forEach { history ->
            if (history.size > 100) { // Keep only recent 100 entries
                history.subList(0, history.size - 100).clear()
            }
        }
        // Reset chop detection if old
        if (System.currentTimeMillis() - lastChopDetection > 300_000L) {
            consecutiveChopTrades = 0
        }
    }
}

// =================================================================================================
// TIME-BASED EXIT MANAGER
// =================================================================================================

class TimeBasedExitManager(private val config: TimeBasedExitConfig) {
    companion object {
        private val logger = LoggerFactory.getLogger(TimeBasedExitManager::class.java)
    }

    private val flatDetection = ConcurrentHashMap<String, FlatPeriod>()

    data class FlatPeriod(
        val startTime: Long,
        val startPrice: Double,
        var endTime: Long,
        var endPrice: Double,
        var isActive: Boolean = true
    )

    data class ExitRecommendation(
        val shouldExit: Boolean,
        val reason: String,
        val urgency: ExitUrgency,
        val timeToExit: Long
    )

    enum class ExitUrgency {
        LOW, MEDIUM, HIGH, IMMEDIATE
    }

    fun analyzeTimeBasedExit(position: Position): ExitRecommendation {
        if (!config.enableTimeBasedExit) {
            return ExitRecommendation(false, "Time-based exit disabled", ExitUrgency.LOW, 0L)
        }

        val now = System.currentTimeMillis()
        val holdTime = position.holdTimeMs
        val pnlPercent = position.unrealizedPnLPercent

        // Check for flat market conditions
        val flatPeriod = detectFlatPeriod(position)
        if (flatPeriod != null && flatPeriod.endTime - flatPeriod.startTime >= config.timeToFlatMs) {
            val reason = "Time-to-flat: ${flatPeriod.endTime - flatPeriod.startTime}ms in ${String.format("%.2f", config.flatRangeThresholdPercent)}% range"
            return ExitRecommendation(true, reason, ExitUrgency.MEDIUM, 0L)
        }

        // Determine effective hold time based on P&L
        val effectiveMaxHoldTime = when {
            pnlPercent > 0 -> (config.maxHoldTimeMs * config.profitTargetTimeReductionPercent).toLong()
            pnlPercent < -0.05 -> config.quickExitTimeMs
            else -> (config.maxHoldTimeMs * config.lossTimeExtensionPercent).toLong()
        }

        // Check time-based exit conditions
        return when (config.timeBasedExitMode) {
            TimeExitMode.HARD_LIMIT -> {
                if (holdTime >= effectiveMaxHoldTime) {
                    ExitRecommendation(true, "Hard time limit: ${holdTime}ms >= ${effectiveMaxHoldTime}ms", ExitUrgency.HIGH, 0L)
                } else {
                    ExitRecommendation(false, "Within time limit", ExitUrgency.LOW, effectiveMaxHoldTime - holdTime)
                }
            }
            TimeExitMode.CONDITIONAL -> {
                when {
                    holdTime >= effectiveMaxHoldTime && pnlPercent < 0 -> {
                        ExitRecommendation(true, "Conditional exit: losing position at time limit", ExitUrgency.HIGH, 0L)
                    }
                    holdTime >= config.extendedHoldTimeMs -> {
                        ExitRecommendation(true, "Extended time limit reached: ${holdTime}ms", ExitUrgency.MEDIUM, 0L)
                    }
                    else -> {
                        ExitRecommendation(false, "Conditional criteria not met", ExitUrgency.LOW, effectiveMaxHoldTime - holdTime)
                    }
                }
            }
            TimeExitMode.PROFIT_ONLY -> {
                if (holdTime >= effectiveMaxHoldTime && pnlPercent > 0) {
                    ExitRecommendation(true, "Profit-only time exit", ExitUrgency.MEDIUM, 0L)
                } else {
                    ExitRecommendation(false, "Not profitable or within time", ExitUrgency.LOW, effectiveMaxHoldTime - holdTime)
                }
            }
            TimeExitMode.LOSS_ONLY -> {
                if (holdTime >= config.quickExitTimeMs && pnlPercent < 0) {
                    ExitRecommendation(true, "Loss-only quick exit", ExitUrgency.HIGH, 0L)
                } else {
                    ExitRecommendation(false, "Not losing or within time", ExitUrgency.LOW, config.quickExitTimeMs - holdTime)
                }
            }
        }
    }

    private fun detectFlatPeriod(position: Position): FlatPeriod? {
        val prices = position.priceHistory
        if (prices.size < 5) return null

        val recentPrices = prices.takeLast(10)
        val high = recentPrices.maxOrNull() ?: return null
        val low = recentPrices.minOrNull() ?: return null
        val range = (high - low) / low

        return if (range <= config.flatRangeThresholdPercent / 100.0) {
            val existing = flatDetection[position.mint]
            if (existing?.isActive == true) {
                existing.endTime = System.currentTimeMillis()
                existing.endPrice = position.currentPrice
                existing
            } else {
                val newFlat = FlatPeriod(
                    startTime = System.currentTimeMillis() - 5000L, // Approximate start
                    startPrice = recentPrices.first(),
                    endTime = System.currentTimeMillis(),
                    endPrice = position.currentPrice
                )
                flatDetection[position.mint] = newFlat
                newFlat
            }
        } else {
            flatDetection[position.mint]?.isActive = false
            null
        }
    }
}