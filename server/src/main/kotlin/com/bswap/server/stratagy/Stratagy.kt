package com.bswap.server.stratagy

import com.bswap.addon.bollinger
import com.bswap.addon.donchianHigh
import com.bswap.addon.donchianLow
import com.bswap.addon.roc
import com.bswap.addon.rsi
import com.bswap.addon.sma
import com.bswap.server.BatchAccumulateConfig
import com.bswap.server.BollingerMeanReversionConfig
import com.bswap.server.BreakoutConfig
import com.bswap.server.DelayedEntryConfig
import com.bswap.server.ImmediateConfig
import com.bswap.server.MomentumConfig
import com.bswap.server.PumpFunPriorityConfig
import com.bswap.server.RsiBasedConfig
import com.bswap.server.SmaCrossConfig
import com.bswap.server.StrategyType
import com.bswap.server.TechnicalAnalysisConfig
import com.bswap.server.TokenMeta
import com.bswap.server.TokenSource
import com.bswap.server.TokenState
import com.bswap.server.TradingRuntime
import com.bswap.server.TradingStrategySettings
import com.bswap.server.ai.*
import com.bswap.server.ConfigLoader
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

interface TradingStrategy {
    val type: StrategyType
    suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime)
    suspend fun onTick(runtime: TradingRuntime)
}

object TradingStrategyFactory {
    fun create(settings: TradingStrategySettings): TradingStrategy {
        return when (settings.type) {
            StrategyType.IMMEDIATE -> ImmediateBuyTimedSellStrategy(settings.immediate)
            StrategyType.DELAYED_ENTRY -> DelayedEntryQuickExitStrategy(settings.delayed)
            StrategyType.BATCH_ACCUMULATE -> BatchAccumulateStrategy(settings.batch)
            StrategyType.PUMPFUN_PRIORITY -> PumpFunPriorityStrategy(settings.pumpFun)
            StrategyType.SMA_CROSS -> SmaCrossBasedStrategy(settings.smaCross)
            StrategyType.RSI_BASED -> RsiBasedTradingStrategy(settings.rsiBased)
            StrategyType.BREAKOUT -> BreakoutTradingStrategy(settings.breakout)
            StrategyType.BOLLINGER_MEAN_REVERSION -> BollingerMeanReversionTradingStrategy(settings.bollingerMeanReversion)
            StrategyType.MOMENTUM -> MomentumTradingStrategy(settings.momentum)
            StrategyType.TECHNICAL_ANALYSIS_COMBINED -> TechnicalAnalysisCombinedStrategy(settings.technicalAnalysis)
            StrategyType.AI_STRATEGY -> {
                val openaiKey = ConfigLoader.loadOpenAIKey()
                if (openaiKey != null) {
                    OpenAITradingStrategy(settings.aiStrategy, openaiKey)
                } else {
                    AITradingStrategy(settings.aiStrategy)
                }
            }
        }
    }

    fun getAllStrategyTypes(): List<StrategyType> = StrategyType.entries.toList()
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
            // Use USD-based pricing for more accurate technical analysis
            val currentPrice = calculateTokenUsdPrice(token, runtime)
            history.add(currentPrice)
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
            val currentPrice = calculateTokenUsdPrice(token, runtime)
            history.add(currentPrice)
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
            val currentPrice = calculateTokenUsdPrice(token, runtime)
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
            val currentPrice = calculateTokenUsdPrice(token, runtime)
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
            val currentPrice = calculateTokenUsdPrice(token, runtime)
            history.add(currentPrice)
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
            val currentPrice = calculateTokenUsdPrice(token, runtime)
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

/**
 * Helper function to calculate USD price for a token using real market data.
 * This replaces the old token.tokenAmount.uiAmount ?: 1.0 approach
 * with proper USD pricing from market APIs for accurate strategy decisions.
 */
class AITradingStrategy(
    private val cfg: com.bswap.server.AIStrategyConfig
) : TradingStrategy {
    override val type: StrategyType = StrategyType.AI_STRATEGY
    private val logger = LoggerFactory.getLogger(AITradingStrategy::class.java)
    
    private val aiModel: AIModel = AIModelFactory.createModel(cfg)
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val positions = ConcurrentHashMap<String, Double>() // entry prices
    private val priceHistory = ConcurrentHashMap<String, MutableList<Pair<Double, Long>>>()
    private val trainingSamples = ConcurrentLinkedQueue<TrainingSample>()
    private var lastRetrainTime = 0L
    private var isModelTrained = false
    
    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        
        // Initialize price history tracking
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
        
        // If model is trained, make immediate prediction
        if (isModelTrained) {
            try {
                val features = extractFeatures(meta.mint, runtime)
                if (features != null) {
                    val prediction = aiModel.predict(features)
                    
                    // Apply AI decision logic
                    if (shouldBuy(prediction, features)) {
                        val bought = runtime.buy(meta.mint)
                        if (bought) {
                            positions[meta.mint] = calculateTokenUsdPrice(
                                runtime.tokenInfo(meta.mint) ?: return, runtime
                            )
                            plannedSells[meta.mint] = runtime.now() + cfg.minHoldMs
                            logger.info("AI Strategy bought ${meta.mint} with confidence ${prediction.confidence}")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in AI prediction for token ${meta.mint}", e)
            }
        }
    }
    
    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        
        try {
            // Update price history and collect training data
            updatePriceHistoryAndCollectData(runtime)
            
            // Retrain model periodically
            if (now - lastRetrainTime > cfg.retrainIntervalMs && trainingSamples.size >= 100) {
                retrain()
            }
            
            // Process existing positions
            processExistingPositions(runtime, now)
            
            // Make new trading decisions for all monitored tokens
            makeNewTradingDecisions(runtime, now)
            
            // Handle timed sells
            handleTimedSells(runtime, now)
            
        } catch (e: Exception) {
            logger.error("Error in AI strategy tick", e)
        }
    }
    
    private suspend fun updatePriceHistoryAndCollectData(runtime: TradingRuntime) {
        runtime.allTokens().forEach { token ->
            val history = priceHistory.getOrPut(token.address) { mutableListOf() }
            val currentPrice = calculateTokenUsdPrice(token, runtime)
            val timestamp = System.currentTimeMillis()
            
            history.add(Pair(currentPrice, timestamp))
            
            // Keep only recent history
            if (history.size > cfg.lookbackPeriod * 2) {
                history.removeAt(0)
            }
            
            // Collect training data from past decisions
            if (history.size >= cfg.predictionHorizon + 1) {
                val oldIndex = history.size - cfg.predictionHorizon - 1
                val oldPrice = history[oldIndex].first
                val actualReturn = (currentPrice - oldPrice) / oldPrice
                
                val features = extractFeaturesFromHistory(history.take(oldIndex + 1))
                if (features != null && trainingSamples.size < cfg.maxTrainingSamples) {
                    trainingSamples.offer(TrainingSample(features, actualReturn, timestamp))
                } else if (trainingSamples.size >= cfg.maxTrainingSamples) {
                    trainingSamples.poll() // Remove oldest
                    if (features != null) {
                        trainingSamples.offer(TrainingSample(features, actualReturn, timestamp))
                    }
                }
            }
        }
    }
    
    private suspend fun processExistingPositions(runtime: TradingRuntime, now: Long) {
        positions.keys.forEach { tokenAddress ->
            val status = runtime.status(tokenAddress)
            val entryPrice = positions[tokenAddress] ?: return@forEach
            
            if (status?.state == TokenState.Swapped) {
                val currentPrice = runtime.tokenInfo(tokenAddress)?.let { 
                    calculateTokenUsdPrice(it, runtime) 
                } ?: return@forEach
                
                val pnlPct = (currentPrice - entryPrice) / entryPrice
                
                // AI-driven risk management
                val features = extractFeatures(tokenAddress, runtime)
                if (features != null) {
                    val prediction = aiModel.predict(features)
                    
                    // Take profit
                    if (pnlPct >= cfg.takeProfitPct) {
                        sellPosition(tokenAddress, runtime, "AI Take Profit")
                        return@forEach
                    }
                    
                    // Stop loss
                    if (pnlPct <= -cfg.stopLossPct) {
                        sellPosition(tokenAddress, runtime, "AI Stop Loss")
                        return@forEach
                    }
                    
                    // AI-based exit signal
                    if (prediction.sellProbability > prediction.buyProbability && 
                        prediction.confidence > cfg.confidenceThreshold) {
                        sellPosition(tokenAddress, runtime, "AI Exit Signal")
                        return@forEach
                    }
                    
                    // Risk-based exit
                    if (prediction.riskScore > 0.8) {
                        sellPosition(tokenAddress, runtime, "High Risk Exit")
                        return@forEach
                    }
                }
            }
        }
    }
    
    private suspend fun makeNewTradingDecisions(runtime: TradingRuntime, now: Long) {
        if (!isModelTrained) return
        
        runtime.allTokens().forEach { token ->
            val status = runtime.status(token.address)
            if (status == null && runtime.isNew(token.address)) {
                val features = extractFeatures(token.address, runtime)
                if (features != null) {
                    val prediction = aiModel.predict(features)
                    
                    if (shouldBuy(prediction, features)) {
                        val bought = runtime.buy(token.address)
                        if (bought) {
                            positions[token.address] = calculateTokenUsdPrice(token, runtime)
                            plannedSells[token.address] = now + cfg.minHoldMs
                            logger.info("AI bought ${token.address}, confidence: ${prediction.confidence}")
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun handleTimedSells(runtime: TradingRuntime, now: Long) {
        plannedSells.entries.filter { it.value <= now }.forEach { entry ->
            val status = runtime.status(entry.key)
            if (status?.state == TokenState.Swapped) {
                sellPosition(entry.key, runtime, "Timed Sell")
            } else {
                plannedSells.remove(entry.key)
            }
        }
    }
    
    private suspend fun sellPosition(tokenAddress: String, runtime: TradingRuntime, reason: String) {
        runtime.sell(tokenAddress)
        positions.remove(tokenAddress)
        plannedSells.remove(tokenAddress)
        logger.info("AI sold $tokenAddress: $reason")
    }
    
    private suspend fun retrain() {
        try {
            logger.info("Retraining AI model with ${trainingSamples.size} samples...")
            val samples = trainingSamples.toList()
            val success = aiModel.train(samples)
            
            if (success) {
                isModelTrained = true
                lastRetrainTime = System.currentTimeMillis()
                logger.info("AI model retrained successfully. Accuracy: ${aiModel.getModelAccuracy() * 100:.2f}%")
            } else {
                logger.warn("AI model retraining failed")
            }
        } catch (e: Exception) {
            logger.error("Error during model retraining", e)
        }
    }
    
    private fun shouldBuy(prediction: PredictionResult, features: MarketFeatures): Boolean {
        if (!isModelTrained) return false
        
        return prediction.buyProbability > prediction.sellProbability &&
               prediction.confidence > cfg.confidenceThreshold &&
               prediction.expectedReturn > 0.02 && // Minimum expected return
               prediction.riskScore < 0.7 && // Risk threshold
               features.sentiment > 0.3 // Minimum sentiment
    }
    
    private suspend fun extractFeatures(tokenAddress: String, runtime: TradingRuntime): MarketFeatures? {
        val history = priceHistory[tokenAddress] ?: return null
        return extractFeaturesFromHistory(history)
    }
    
    private fun extractFeaturesFromHistory(history: List<Pair<Double, Long>>): MarketFeatures? {
        if (history.size < 10) return null
        
        val prices = history.map { it.first }
        val volumes = history.map { it.first } // Simplified - using price as volume proxy
        
        // Price action: normalized price change
        val priceAction = if (prices.size > 1) {
            (prices.last() - prices.first()) / prices.first()
        } else 0.0
        
        // Volume: average volume over period (simplified)
        val volume = volumes.average() / volumes.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        
        // Momentum: rate of change
        val momentum = if (prices.size >= 5) {
            val recent = prices.takeLast(5).average()
            val older = prices.dropLast(5).takeLast(5).average()
            if (older > 0) (recent - older) / older else 0.0
        } else 0.0
        
        // Volatility: standard deviation of returns
        val returns = prices.zipWithNext { a, b -> (b - a) / a }
        val volatility = if (returns.isNotEmpty()) {
            val mean = returns.average()
            sqrt(returns.map { (it - mean).pow(2) }.average())
        } else 0.0
        
        // Sentiment: simplified sentiment based on recent price movement
        val sentiment = when {
            priceAction > 0.05 -> 0.8
            priceAction > 0 -> 0.6
            priceAction > -0.05 -> 0.4
            else -> 0.2
        }
        
        return MarketFeatures(
            priceAction = priceAction.coerceIn(-1.0, 1.0),
            volume = volume.coerceIn(0.0, 1.0),
            momentum = momentum.coerceIn(-1.0, 1.0),
            volatility = volatility.coerceIn(0.0, 1.0),
            sentiment = sentiment.coerceIn(0.0, 1.0)
        )
    }
}

private suspend fun calculateTokenUsdPrice(
    token: com.bswap.server.data.solana.transaction.TokenInfo,
    runtime: TradingRuntime
): Double {
    // First, try to get the real USD price from the market
    val usdPrice = runtime.getTokenUsdPrice(token.address)

    return when {
        // If we have a real USD price from the market, use it
        usdPrice != null && usdPrice > 0.0 -> usdPrice

        // Fallback to token UI amount for tokens without market data
        // This is better than using amount in lamports/smallest units
        token.tokenAmount.uiAmount != null && token.tokenAmount.uiAmount > 0.0 ->
            token.tokenAmount.uiAmount

        // Final fallback to ensure algorithms never divide by zero
        else -> 0.000001
    }
}
