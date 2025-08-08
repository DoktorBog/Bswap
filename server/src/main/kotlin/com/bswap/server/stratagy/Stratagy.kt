package com.bswap.server.stratagy

import com.bswap.server.BatchAccumulateConfig
import com.bswap.server.DelayedEntryConfig
import com.bswap.server.ImmediateConfig
import com.bswap.server.PumpFunPriorityConfig
import com.bswap.server.RiskAwareConfig
import com.bswap.server.StrategyType
import com.bswap.server.TokenMeta
import com.bswap.server.TokenSource
import com.bswap.server.TokenState
import com.bswap.server.TradingRuntime
import com.bswap.server.TradingStrategySettings
import com.bswap.server.SmaCrossConfig
import com.bswap.server.RsiBasedConfig
import com.bswap.server.BreakoutConfig
import com.bswap.server.BollingerMeanReversionConfig
import com.bswap.server.MomentumConfig
import com.bswap.server.TechnicalAnalysisConfig
import com.bswap.server.validation.TokenValidator
import com.bswap.addon.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

interface TradingStrategy {
    val type: StrategyType
    suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime)
    suspend fun onTick(runtime: TradingRuntime)
}

object TradingStrategyFactory {
    fun create(settings: TradingStrategySettings, tokenValidator: TokenValidator): TradingStrategy {
        return when (settings.type) {
            StrategyType.IMMEDIATE -> ImmediateBuyTimedSellStrategy(settings.immediate)
            StrategyType.DELAYED_ENTRY -> DelayedEntryQuickExitStrategy(settings.delayed)
            StrategyType.RISK_AWARE -> RiskAwareEntryTimedExitStrategy(settings.riskAware, tokenValidator)
            StrategyType.BATCH_ACCUMULATE -> BatchAccumulateStrategy(settings.batch)
            StrategyType.PUMPFUN_PRIORITY -> PumpFunPriorityStrategy(settings.pumpFun)
            StrategyType.SMA_CROSS -> SmaCrossBasedStrategy(settings.smaCross)
            StrategyType.RSI_BASED -> RsiBasedTradingStrategy(settings.rsiBased)
            StrategyType.BREAKOUT -> BreakoutTradingStrategy(settings.breakout)
            StrategyType.BOLLINGER_MEAN_REVERSION -> BollingerMeanReversionTradingStrategy(settings.bollingerMeanReversion)
            StrategyType.MOMENTUM -> MomentumTradingStrategy(settings.momentum)
            StrategyType.TECHNICAL_ANALYSIS_COMBINED -> TechnicalAnalysisCombinedStrategy(settings.technicalAnalysis)
        }
    }
}

class ImmediateBuyTimedSellStrategy(
    private val cfg: ImmediateConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.IMMEDIATE
    private val plannedSells = ConcurrentHashMap<String, Long>()
    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        val bought = runtime.buy(meta.mint)
        if (bought) plannedSells[meta.mint] = runtime.now() + cfg.minHoldMs
    }
    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class DelayedEntryQuickExitStrategy(
    private val cfg: DelayedEntryConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.DELAYED_ENTRY
    private val plannedBuys = ConcurrentHashMap<String, Long>()
    private val plannedSells = ConcurrentHashMap<String, Long>()
    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        plannedBuys.putIfAbsent(meta.mint, runtime.now() + cfg.entryDelayMs)
    }
    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        plannedBuys.entries.filter { it.value <= now }.forEach { e ->
            if (runtime.isNew(e.key)) {
                val bought = runtime.buy(e.key)
                if (bought) plannedSells[e.key] = now + cfg.minHoldMs
            }
            plannedBuys.remove(e.key)
        }
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class RiskAwareEntryTimedExitStrategy(
    private val cfg: RiskAwareConfig,
    private val validator: TokenValidator
) : TradingStrategy {
    override val type: StrategyType = StrategyType.RISK_AWARE
    private val plannedBuys = ConcurrentHashMap<String, Long>()
    private val plannedSells = ConcurrentHashMap<String, Long>()
    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        val res = withContext(Dispatchers.IO) { validator.validateToken(meta.mint) }
        if (!res.isValid) return
        if (res.riskScore > cfg.maxRisk) return
        val delayMs = cfg.baseDelayMs + (res.riskScore * cfg.perRiskDelayMs).toLong()
        plannedBuys.putIfAbsent(meta.mint, runtime.now() + delayMs)
    }
    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        plannedBuys.entries.filter { it.value <= now }.forEach { e ->
            if (runtime.isNew(e.key)) {
                val bought = runtime.buy(e.key)
                if (bought) plannedSells[e.key] = now + cfg.minHoldMs
            }
            plannedBuys.remove(e.key)
        }
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class BatchAccumulateStrategy(
    private val cfg: BatchAccumulateConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.BATCH_ACCUMULATE
    private val queue = ConcurrentLinkedQueue<String>()
    private val lastBatchAt = AtomicLong(0)
    private val plannedSells = ConcurrentHashMap<String, Long>()
    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        if (!queue.contains(meta.mint)) queue.add(meta.mint)
    }
    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        val due = queue.size >= cfg.batchSize

        now - lastBatchAt.get() >= cfg.batchIntervalMs
        if (due) {
            var i = 0
            while (i < cfg.batchSize && queue.isNotEmpty()) {
                val mint = queue.poll() ?: break
                if (runtime.isNew(mint)) {
                    val bought = runtime.buy(mint)
                    if (bought) plannedSells[mint] = now + cfg.minHoldMs
                }
                i++
                delay(250)
            }
            lastBatchAt.set(now)
        }
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class PumpFunPriorityStrategy(
    private val cfg: PumpFunPriorityConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.PUMPFUN_PRIORITY
    private val plannedSells = ConcurrentHashMap<String, Long>()
    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (meta.source != TokenSource.PUMPFUN) return
        if (!runtime.isNew(meta.mint)) return
        val bought = runtime.buy(meta.mint)
        if (bought) plannedSells[meta.mint] = runtime.now() + cfg.minHoldMs
    }
    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

// Import technical analysis functions from addon package

class SmaCrossBasedStrategy(
    private val cfg: SmaCrossConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.SMA_CROSS
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        // Initialize price history tracking
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()

        // Update price history for all tokens
        runtime.allTokens().forEach { token ->
            val history = priceHistory.getOrPut(token.address) { mutableListOf() }
            // Add current price (mock implementation - integrate with real price feed)
            history.add(token.usdValue ?: 1.0)
            // Keep only last 50 prices for performance
            if (history.size > 50) history.removeAt(0)

            // Check SMA cross signals
            if (history.size >= cfg.slowPeriod) {
                val fastSma = sma(history, cfg.fastPeriod)
                val slowSma = sma(history, cfg.slowPeriod)

                if (fastSma != null && slowSma != null) {
                    val status = runtime.status(token.address)

                    // Buy signal: fast SMA crosses above slow SMA
                    if (fastSma > slowSma && status == null && runtime.isNew(token.address)) {
                        val bought = runtime.buy(token.address)
                        if (bought) plannedSells[token.address] = now + cfg.minHoldMs
                    }

                    // Sell signal: fast SMA crosses below slow SMA
                    if (fastSma < slowSma && status?.state == TokenState.Swapped) {
                        runtime.sell(token.address)
                        plannedSells.remove(token.address)
                    }
                }
            }
        }

        // Handle timed sells
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class RsiBasedTradingStrategy(
    private val cfg: RsiBasedConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.RSI_BASED
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()

        runtime.allTokens().forEach { token ->
            val history = priceHistory.getOrPut(token.address) { mutableListOf() }
            history.add(token.usdValue ?: 1.0)
            if (history.size > 50) history.removeAt(0)

            if (history.size >= cfg.period + 1) {
                val rsiValue = rsi(history, cfg.period)

                if (rsiValue != null) {
                    val status = runtime.status(token.address)

                    // Buy signal: RSI below buy threshold (oversold)
                    if (rsiValue <= cfg.buyBelow && status == null && runtime.isNew(token.address)) {
                        val bought = runtime.buy(token.address)
                        if (bought) plannedSells[token.address] = now + cfg.minHoldMs
                    }

                    // Sell signal: RSI above sell threshold (overbought)
                    if (rsiValue >= cfg.sellAbove && status?.state == TokenState.Swapped) {
                        runtime.sell(token.address)
                        plannedSells.remove(token.address)
                    }
                }
            }
        }

        // Handle timed sells
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class BreakoutTradingStrategy(
    private val cfg: BreakoutConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.BREAKOUT
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()

        runtime.allTokens().forEach { token ->
            val history = priceHistory.getOrPut(token.address) { mutableListOf() }
            val currentPrice = token.usdValue ?: 1.0
            history.add(currentPrice)
            if (history.size > 50) history.removeAt(0)

            if (history.size >= cfg.lookback) {
                val highestHigh = donchianHigh(history, cfg.lookback)
                val lowestLow = donchianLow(history, cfg.lookback)

                if (highestHigh != null && lowestLow != null) {
                    val breakoutHigh = highestHigh * (1.0 + cfg.bufferPct)
                    val breakoutLow = lowestLow * (1.0 - cfg.bufferPct)
                    val status = runtime.status(token.address)

                    // Buy signal: price breaks above highest high
                    if (currentPrice > breakoutHigh && status == null && runtime.isNew(token.address)) {
                        val bought = runtime.buy(token.address)
                        if (bought) plannedSells[token.address] = now + cfg.minHoldMs
                    }

                    // Sell signal: price breaks below lowest low
                    if (currentPrice < breakoutLow && status?.state == TokenState.Swapped) {
                        runtime.sell(token.address)
                        plannedSells.remove(token.address)
                    }
                }
            }
        }

        // Handle timed sells
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class BollingerMeanReversionTradingStrategy(
    private val cfg: BollingerMeanReversionConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.BOLLINGER_MEAN_REVERSION
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()

        runtime.allTokens().forEach { token ->
            val history = priceHistory.getOrPut(token.address) { mutableListOf() }
            val currentPrice = token.usdValue ?: 1.0
            history.add(currentPrice)
            if (history.size > 50) history.removeAt(0)

            if (history.size >= cfg.period) {
                val bb = bollinger(history, cfg.period, cfg.dev)

                if (bb != null) {
                    val status = runtime.status(token.address)

                    // Buy signal: price touches lower Bollinger Band
                    if (currentPrice <= bb.lower && status == null && runtime.isNew(token.address)) {
                        val bought = runtime.buy(token.address)
                        if (bought) plannedSells[token.address] = now + cfg.minHoldMs
                    }

                    // Sell signal: price touches upper Bollinger Band or returns to middle
                    if ((currentPrice >= bb.upper || currentPrice >= bb.middle) && status?.state == TokenState.Swapped) {
                        runtime.sell(token.address)
                        plannedSells.remove(token.address)
                    }
                }
            }
        }

        // Handle timed sells
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class MomentumTradingStrategy(
    private val cfg: MomentumConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.MOMENTUM
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()

        runtime.allTokens().forEach { token ->
            val history = priceHistory.getOrPut(token.address) { mutableListOf() }
            history.add(token.usdValue ?: 1.0)
            if (history.size > 50) history.removeAt(0)

            if (history.size > cfg.rocPeriod) {
                val rocValue = roc(history, cfg.rocPeriod)

                if (rocValue != null) {
                    val status = runtime.status(token.address)

                    // Buy signal: positive momentum above threshold
                    if (rocValue >= cfg.buyThreshold && status == null && runtime.isNew(token.address)) {
                        val bought = runtime.buy(token.address)
                        if (bought) plannedSells[token.address] = now + cfg.minHoldMs
                    }

                    // Sell signal: negative momentum below threshold
                    if (rocValue <= -cfg.sellThreshold && status?.state == TokenState.Swapped) {
                        runtime.sell(token.address)
                        plannedSells.remove(token.address)
                    }
                }
            }
        }

        // Handle timed sells
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) runtime.sell(e.key)
            plannedSells.remove(e.key)
        }
    }
}

class TechnicalAnalysisCombinedStrategy(
    private val cfg: TechnicalAnalysisConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.TECHNICAL_ANALYSIS_COMBINED
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val positions = ConcurrentHashMap<String, Double>() // entry prices
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()

        runtime.allTokens().forEach { token ->
            val history = priceHistory.getOrPut(token.address) { mutableListOf() }
            val currentPrice = token.usdValue ?: 1.0
            history.add(currentPrice)
            if (history.size > 50) history.removeAt(0)

            if (history.size >= 20) { // Minimum data required for all indicators
                val signals = calculateCombinedSignals(history, currentPrice)
                val status = runtime.status(token.address)
                val entryPrice = positions[token.address]

                // Risk management checks
                if (entryPrice != null && status?.state == TokenState.Swapped) {
                    val pnlPct = (currentPrice - entryPrice) / entryPrice

                    // Take profit
                    if (pnlPct >= cfg.takeProfitPct) {
                        runtime.sell(token.address)
                        positions.remove(token.address)
                        plannedSells.remove(token.address)
                        return@forEach
                    }

                    // Stop loss
                    if (pnlPct <= -cfg.stopLossPct) {
                        runtime.sell(token.address)
                        positions.remove(token.address)
                        plannedSells.remove(token.address)
                        return@forEach
                    }

                    // Trailing stop (simplified)
                    if (pnlPct <= -cfg.trailingStopPct) {
                        runtime.sell(token.address)
                        positions.remove(token.address)
                        plannedSells.remove(token.address)
                        return@forEach
                    }
                }

                // Combined signal decision
                val totalBuyScore = signals.buyScore
                val totalSellScore = signals.sellScore

                // Buy signal
                if (totalBuyScore >= cfg.decisionThreshold && status == null && runtime.isNew(token.address)) {
                    val bought = runtime.buy(token.address)
                    if (bought) {
                        positions[token.address] = currentPrice
                        plannedSells[token.address] = now + cfg.minHoldMs
                    }
                }

                // Sell signal
                if (totalSellScore >= cfg.decisionThreshold && status?.state == TokenState.Swapped) {
                    runtime.sell(token.address)
                    positions.remove(token.address)
                    plannedSells.remove(token.address)
                }
            }
        }

        // Handle timed sells
        plannedSells.entries.filter { it.value <= now }.forEach { e ->
            val st = runtime.status(e.key)
            if (st?.state == TokenState.Swapped) {
                runtime.sell(e.key)
                positions.remove(e.key)
            }
            plannedSells.remove(e.key)
        }
    }

    private data class CombinedSignals(val buyScore: Double, val sellScore: Double)

    private fun calculateCombinedSignals(history: List<Double>, currentPrice: Double): CombinedSignals {
        var buyScore = 0.0
        var sellScore = 0.0

        // SMA Cross
        val fastSma = sma(history, 5)
        val slowSma = sma(history, 20)
        if (fastSma != null && slowSma != null) {
            if (fastSma > slowSma) buyScore += cfg.smaWeight
            else sellScore += cfg.smaWeight
        }

        // RSI
        val rsiValue = rsi(history, 14)
        if (rsiValue != null) {
            if (rsiValue <= 30) buyScore += cfg.rsiWeight
            else if (rsiValue >= 70) sellScore += cfg.rsiWeight
        }

        // Breakout
        val highestHigh = donchianHigh(history, 20)
        val lowestLow = donchianLow(history, 20)
        if (highestHigh != null && lowestLow != null) {
            if (currentPrice > highestHigh * 1.002) buyScore += cfg.breakoutWeight
            else if (currentPrice < lowestLow * 0.998) sellScore += cfg.breakoutWeight
        }

        // Bollinger Bands
        val bb = bollinger(history, 20, 2.0)
        if (bb != null) {
            if (currentPrice <= bb.lower) buyScore += cfg.bollingerWeight
            else if (currentPrice >= bb.upper) sellScore += cfg.bollingerWeight
        }

        // Momentum
        val rocValue = roc(history, 6)
        if (rocValue != null) {
            if (rocValue >= 0.01) buyScore += cfg.momentumWeight
            else if (rocValue <= -0.01) sellScore += cfg.momentumWeight
        }

        return CombinedSignals(buyScore, sellScore)
    }
}
