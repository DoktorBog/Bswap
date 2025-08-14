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
import com.bswap.server.WalletSellOnlyConfig
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

/**
 * Enhanced base class for strategies with wallet awareness and improved price handling
 */
abstract class BaseStrategy(
    protected val strategyName: String
) {
    protected val log = LoggerFactory.getLogger(strategyName)
    protected val walletTokens = ConcurrentHashMap<String, WalletTokenInfo>()
    protected val trackedMints = ConcurrentHashMap<String, Long>() // mint -> discovery time

    /**
     * Update wallet token universe - ensures all wallet-held tokens are managed
     */
    protected suspend fun updateWalletUniverse(runtime: TradingRuntime) {
        val currentWalletTokens = runtime.allTokens()

        currentWalletTokens.forEach { tokenInfo ->
            val mint = tokenInfo.address
            val balance = tokenInfo.tokenAmount.uiAmount ?: 0.0

            if (balance > 0) {
                // Add to wallet tokens if not already tracked
                if (!walletTokens.containsKey(mint)) {
                    walletTokens[mint] = WalletTokenInfo(
                        mint = mint,
                        balance = balance,
                        firstSeen = System.currentTimeMillis(),
                        tokenInfo = tokenInfo
                    )
                    log.info("Added wallet-held token to strategy universe: $mint (balance: $balance)")
                } else {
                    // Update existing wallet token info
                    walletTokens[mint]?.let { info ->
                        info.balance = balance
                        info.tokenInfo = tokenInfo
                    }
                }

                // Ensure it's tracked for strategy logic
                trackedMints.putIfAbsent(mint, System.currentTimeMillis())
            }
        }

        // Remove tokens no longer in wallet
        val currentMints = currentWalletTokens.map { it.address }.toSet()
        walletTokens.keys.filter { it !in currentMints }.forEach { mint ->
            walletTokens.remove(mint)
            log.debug("Removed token no longer in wallet: $mint")
        }
    }

    /**
     * Check if a buy should proceed based on price availability and configuration
     */
    protected suspend fun shouldAllowBuy(mint: String, runtime: TradingRuntime): Boolean {
        // Always check allowBuyWithoutPrice configuration
        if (!runtime.config.priceService.allowBuyWithoutPrice) {
            val price = runtime.getTokenUsdPrice(mint)
            if (price == null) {
                log.debug("Skipping buy for $mint: no price available and allowBuyWithoutPrice=false")
                return false
            }
        }
        return true
    }

    /**
     * Get all tokens in strategy universe (discovered + wallet-held)
     */
    protected fun getStrategyUniverse(): Set<String> {
        return trackedMints.keys + walletTokens.keys
    }

    /**
     * Check if token is wallet-held
     */
    protected fun isWalletHeld(mint: String): Boolean {
        return walletTokens.containsKey(mint)
    }
}

data class WalletTokenInfo(
    val mint: String,
    var balance: Double,
    val firstSeen: Long,
    var tokenInfo: com.bswap.server.data.solana.transaction.TokenInfo
)

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
            StrategyType.WALLET_SELL_ONLY -> WalletSellOnlyStrategy(settings.walletSellOnly)
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
) : BaseStrategy("SmaCrossStrategy"), TradingStrategy {
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
) : BaseStrategy("RsiBasedStrategy"), TradingStrategy {
    override val type: StrategyType = StrategyType.RSI_BASED
    private val plannedSells = ConcurrentHashMap<String, Long>()
    private val priceHistory = ConcurrentHashMap<String, MutableList<Double>>()
    private val rsiValues = ConcurrentHashMap<String, MutableList<Double>>()
    
    companion object {
        @Volatile
        private var lastSellTime = 0L
        private const val SELL_DELAY_MS = 3000L // 3 seconds between sells
        
        private suspend fun canSellNow(): Boolean {
            val now = System.currentTimeMillis()
            return if (now - lastSellTime >= SELL_DELAY_MS) {
                lastSellTime = now
                true
            } else {
                false
            }
        }
    }

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        if (!runtime.isNew(meta.mint)) return
        
        log.info("📈 RSI Strategy: New token discovered ${meta.mint} from ${meta.source}")
        priceHistory.putIfAbsent(meta.mint, mutableListOf())
        rsiValues.putIfAbsent(meta.mint, mutableListOf())
        
        // Load initial price history for RSI calculation
        loadInitialPriceHistory(meta.mint, runtime)
        
        // Check RSI-based buy condition
        val history = priceHistory[meta.mint] ?: mutableListOf()
        val shouldBuy = if (history.size >= cfg.period) {
            val rsiValue = rsi(history, cfg.period)
            log.info("📊 RSI for ${meta.mint}: $rsiValue")
            // Buy when RSI is oversold (below 30)
            rsiValue != null && rsiValue <= cfg.oversoldThreshold && shouldAllowBuy(meta.mint, runtime)
        } else {
            // Not enough data for RSI, use immediate buy for new tokens
            shouldAllowBuy(meta.mint, runtime)
        }
        
        if (shouldBuy) {
            log.info("🚀 RSI Strategy: Attempting buy for ${meta.mint}")
            val bought = runtime.buy(meta.mint)
            if (bought) {
                plannedSells[meta.mint] = runtime.now() + cfg.minHoldMs
                log.info("✅ RSI Strategy: Successfully bought ${meta.mint}")
            } else {
                log.warn("❌ RSI Strategy: Failed to buy ${meta.mint}")
            }
        } else {
            log.debug("⏭️ RSI Strategy: Skipping buy for ${meta.mint} - RSI conditions not met")
        }
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        
        // Update wallet universe for comprehensive tracking
        updateWalletUniverse(runtime)

        // Get all tokens in wallet - these are the ones we can sell
        val walletTokens = runtime.allTokens().filter { 
            it.tokenAmount.uiAmount != null && it.tokenAmount.uiAmount > 0.0 
        }
        val walletMints = walletTokens.map { it.address }.toSet()
        
        // Include discovered tokens for price tracking
        val universe = priceHistory.keys + walletMints

        log.info("📊 RSI Strategy: Checking ${universe.size} total tokens (${walletMints.size} wallet tokens)")

        // Process all tokens for price history
        for (mint in universe) {
            val history = priceHistory.getOrPut(mint) { mutableListOf() }
            val tokenInfo = walletTokens.firstOrNull { it.address == mint }
            val status = runtime.status(mint)
            
            // Try to get current price, but don't fail if unavailable
            val currentPrice = try {
                if (tokenInfo != null) {
                    calculateTokenUsdPrice(tokenInfo, runtime)
                } else {
                    runtime.getTokenUsdPrice(mint) ?: 0.0
                }
            } catch (e: Exception) {
                log.debug("Could not get price for $mint: ${e.message}")
                0.0
            }

            // Only add price to history if we got a valid price
            if (currentPrice > 0.0) {
                history.add(currentPrice)
                if (history.size > cfg.period * 2) history.removeAt(0) // Keep enough for RSI calculation
                
                // Calculate and store RSI
                if (history.size >= cfg.period) {
                    val rsiValue = rsi(history, cfg.period)
                    if (rsiValue != null) {
                        val rsiHistory = rsiValues.getOrPut(mint) { mutableListOf() }
                        rsiHistory.add(rsiValue)
                        if (rsiHistory.size > 50) rsiHistory.removeAt(0)
                    }
                }
            }

            // Check if this is a token we hold and can sell
            if (tokenInfo != null && status?.state == TokenState.Swapped) {
                log.info("💎 Checking RSI sell conditions for held token $mint (balance: ${tokenInfo.tokenAmount.uiAmount})")
                
                val tokenAge = now - (status.createdAt ?: 0)
                val minHoldTimeMs = cfg.minHoldMs
                
                var shouldSell = false
                var sellReason = ""
                
                // Calculate current RSI
                val currentRsi = if (history.size >= cfg.period) {
                    rsi(history, cfg.period)
                } else null
                
                if (currentRsi != null) {
                    log.debug("Current RSI for $mint: $currentRsi")
                    
                    // Condition 1: Sell when RSI is overbought (above 70)
                    if (currentRsi >= cfg.overboughtThreshold) {
                        shouldSell = true
                        sellReason = "RSI overbought ($currentRsi >= ${cfg.overboughtThreshold})"
                    }
                    
                    // Condition 2: Sell on RSI divergence (price up but RSI down)
                    else if (history.size >= 2 && currentPrice > 0.0) {
                        val rsiHistory = rsiValues[mint]
                        if (rsiHistory != null && rsiHistory.size >= 2) {
                            val previousRsi = rsiHistory[rsiHistory.size - 2]
                            val previousPrice = history[history.size - 2]
                            val priceChange = (currentPrice - previousPrice) / previousPrice
                            val rsiChange = currentRsi - previousRsi
                            
                            // Bearish divergence: price up but RSI down
                            if (priceChange > 0.01 && rsiChange < -2) {
                                shouldSell = true
                                sellReason = "RSI bearish divergence (price up ${(priceChange * 100).toInt()}%, RSI down ${rsiChange.toInt()} points)"
                            }
                        }
                    }
                    
                    // Condition 3: Take profit when RSI crosses back above 50 from below
                    else if (currentRsi > 50) {
                        val rsiHistory = rsiValues[mint]
                        if (rsiHistory != null && rsiHistory.size >= 2) {
                            val previousRsi = rsiHistory[rsiHistory.size - 2]
                            if (previousRsi <= 50) {
                                shouldSell = true
                                sellReason = "RSI crossed above neutral (${previousRsi.toInt()} -> ${currentRsi.toInt()})"
                            }
                        }
                    }
                }
                
                // Fallback: Sell after extended hold time if no RSI signal
                if (!shouldSell && tokenAge >= cfg.minHoldMs * 10) { // 10x min hold time
                    shouldSell = true
                    sellReason = "Extended hold time reached (${tokenAge}ms)"
                }
                
                // Execute sell if triggered (with delay check)
                if (shouldSell) {
                    if (canSellNow()) {
                        log.info("🔥 SELL NOW - RSI Strategy: $mint - $sellReason")
                        runtime.sell(mint)
                        plannedSells.remove(mint)
                        rsiValues.remove(mint) // Clear RSI history after sell
                    } else {
                        log.info("⏸️ SELL DELAYED - Waiting for sell cooldown: $mint - $sellReason")
                        // Keep the sell planned for next tick
                    }
                } else {
                    log.debug("💎 Holding $mint (RSI: ${currentRsi?.toInt()}, age: ${tokenAge}ms)")
                }
            }
        }

        // Handle timed sells (backup mechanism) - use configured minimum time
        val expiredSells = plannedSells.entries.filter { 
            val age = now - (runtime.status(it.key)?.createdAt ?: 0)
            it.value <= now && age >= cfg.minHoldMs
        }
        if (expiredSells.isNotEmpty()) {
            log.info("⏰ RSI Strategy: Processing ${expiredSells.size} timed sells")
            for (e in expiredSells) {
                val st = runtime.status(e.key)
                if (st?.state == TokenState.Swapped) {
                    if (canSellNow()) {
                        log.info("⏰ SELL NOW - RSI Strategy: Timed sell for ${e.key}")
                        runtime.sell(e.key)
                        plannedSells.remove(e.key)
                        rsiValues.remove(e.key)
                    } else {
                        log.info("⏸️ TIMED SELL DELAYED - Waiting for sell cooldown: ${e.key}")
                        // Keep in planned sells for next tick
                        break // Stop processing more sells this tick
                    }
                } else {
                    plannedSells.remove(e.key)
                }
            }
        }
        
        // Sell ALL wallet tokens that have been held for configured minimum time (with delay)
        for (mint in walletMints) {
            val status = runtime.status(mint)
            if (status?.state == TokenState.Swapped) {
                val tokenAge = now - (status.createdAt ?: 0)
                if (tokenAge >= cfg.minHoldMs * 10 && !plannedSells.containsKey(mint)) { // Extended hold time for RSI strategy
                    if (canSellNow()) {
                        log.info("⏰ SELL NOW - RSI Strategy: Force selling wallet token $mint (held ${tokenAge}ms)")
                        runtime.sell(mint)
                        rsiValues.remove(mint)
                    } else {
                        log.info("⏸️ FORCE SELL DELAYED - Waiting for sell cooldown: $mint")
                        // Will try again next tick
                        break // Stop processing more force sells this tick
                    }
                }
            }
        }
        
        log.info("📋 RSI Strategy: ${plannedSells.size} planned sells, ${walletMints.size} wallet tokens, ${rsiValues.size} RSI tracked")
    }
    
    /**
     * Load initial price history for better RSI calculation
     */
    private suspend fun loadInitialPriceHistory(mint: String, runtime: TradingRuntime) {
        try {
            // Try to get historical prices from runtime or external source
            val historicalPrices = runtime.getPriceHistory?.invoke(mint)
            if (historicalPrices != null && historicalPrices.isNotEmpty()) {
                val history = priceHistory.getOrPut(mint) { mutableListOf() }
                history.addAll(historicalPrices.takeLast(cfg.period * 2))
                log.info("📊 Loaded ${historicalPrices.size} historical prices for $mint")
            }
        } catch (e: Exception) {
            log.debug("Could not load historical prices for $mint: ${e.message}")
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
            if (history.size > 200) history.removeAt(0)

            if (history.size >= cfg.lookback) {
                val highestHigh = donchianHigh(history, cfg.lookback)
                val lowestLow = donchianLow(history, cfg.lookback)

                if (highestHigh != null && lowestLow != null) {
                    val breakoutHigh = highestHigh * (1.0 + cfg.bufferPct)
                    val breakoutLow = lowestLow * (1.0 - cfg.bufferPct)
                    val status = runtime.status(token.address)

                    // Buy signal: price breaks above highest high
                    if (currentPrice > breakoutHigh && status == null && runtime.isNew(token.address)) {
                        val shouldBuy = runtime.config.priceService.allowBuyWithoutPrice || currentPrice > 0.0
                        if (shouldBuy) {
                            val bought = runtime.buy(token.address)
                            if (bought) plannedSells[token.address] = now + cfg.minHoldMs
                        }
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
            if (history.size > 200) history.removeAt(0)

            if (history.size >= cfg.period) {
                val bb = bollinger(history, cfg.period, cfg.dev)

                if (bb != null) {
                    val status = runtime.status(token.address)

                    // Buy signal: price touches lower Bollinger Band
                    if (currentPrice <= bb.lower && status == null && runtime.isNew(token.address)) {
                        val shouldBuy = runtime.config.priceService.allowBuyWithoutPrice || currentPrice > 0.0
                        if (shouldBuy) {
                            val bought = runtime.buy(token.address)
                            if (bought) plannedSells[token.address] = now + cfg.minHoldMs
                        }
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
            if (history.size > 200) history.removeAt(0)

            if (history.size > cfg.rocPeriod) {
                val rocValue = roc(history, cfg.rocPeriod)

                if (rocValue != null) {
                    val status = runtime.status(token.address)

                    // Buy signal: positive momentum above threshold
                    if (rocValue >= cfg.buyThreshold && status == null && runtime.isNew(token.address)) {
                        val shouldBuy = runtime.config.priceService.allowBuyWithoutPrice || currentPrice > 0.0
                        if (shouldBuy) {
                            val bought = runtime.buy(token.address)
                            if (bought) plannedSells[token.address] = now + cfg.minHoldMs
                        }
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
            if (history.size > 200) history.removeAt(0)

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
                    val shouldBuy = runtime.config.priceService.allowBuyWithoutPrice || currentPrice > 0.0
                    if (shouldBuy) {
                        val bought = runtime.buy(token.address)
                        if (bought) {
                            positions[token.address] = currentPrice
                            plannedSells[token.address] = now + cfg.minHoldMs
                        }
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

class WalletSellOnlyStrategy(
    private val cfg: WalletSellOnlyConfig
) : BaseStrategy("WalletSellOnlyStrategy"), TradingStrategy {
    override val type: StrategyType = StrategyType.WALLET_SELL_ONLY
    private var lastSellTime = 0L
    private val processedTokens = ConcurrentHashMap<String, Long>() // mint -> last processed time

    override suspend fun onDiscovered(meta: TokenMeta, runtime: TradingRuntime) {
        // This strategy never buys new tokens - only sells existing wallet tokens
        log.debug("WalletSellOnly: Ignoring discovered token ${meta.mint} - this strategy only sells existing wallet tokens")
    }

    override suspend fun onTick(runtime: TradingRuntime) {
        val now = runtime.now()
        
        // Update wallet universe to get all tokens currently in wallet
        updateWalletUniverse(runtime)
        
        // Get all wallet tokens with positive balance
        val walletTokens = runtime.allTokens().filter { 
            it.tokenAmount.uiAmount != null && it.tokenAmount.uiAmount > 0.0 &&
            !cfg.ignoreTokens.contains(it.address) // Skip ignored tokens like SOL, USDC, USDT
        }
        
        log.info("💰 WalletSellOnly: Found ${walletTokens.size} wallet tokens to potentially sell")
        
        // Collect all tokens that should be sold
        val tokensToSell = mutableListOf<Pair<String, String>>() // mint to reason
        
        for (token in walletTokens) {
            val mint = token.address
            val balance = token.tokenAmount.uiAmount ?: 0.0
            val status = runtime.status(mint)
            val lastProcessed = processedTokens[mint] ?: 0L
            
            // Skip if we just processed this token recently (within sellIntervalMs)
            if (now - lastProcessed < cfg.sellIntervalMs) {
                continue
            }
            
            log.info("🔍 Checking sell conditions for wallet token: $mint (balance: $balance)")
            
            var shouldSell = false
            var sellReason = ""
            
            // Condition 1: Token has been held for minimum time and we have status info
            if (status?.state == TokenState.Swapped) {
                val tokenAge = now - (status.createdAt ?: 0)
                
                // Sell if held for minimum time
                if (tokenAge >= cfg.minHoldTimeMs) {
                    shouldSell = true
                    sellReason = "Held for minimum time (${tokenAge}ms >= ${cfg.minHoldTimeMs}ms)"
                }
                
                // Force sell if held too long
                if (tokenAge >= cfg.maxHoldTimeMs) {
                    shouldSell = true
                    sellReason = "Force sell - held too long (${tokenAge}ms >= ${cfg.maxHoldTimeMs}ms)"
                }
            }
            // Condition 2: Token in wallet but no status (possibly bought outside the bot)
            else if (status == null) {
                shouldSell = true
                sellReason = "Unknown token in wallet - selling immediately"
            }
            
            if (shouldSell) {
                tokensToSell.add(mint to sellReason)
            } else {
                // Mark as processed even if not selling to avoid checking too frequently
                processedTokens[mint] = now
                
                val statusInfo = if (status != null) {
                    val age = now - (status.createdAt ?: 0)
                    "status: ${status.state}, age: ${age}ms"
                } else {
                    "no status"
                }
                log.debug("💎 Holding wallet token: $mint ($statusInfo)")
            }
        }
        
        // Sell all tokens at once if any need to be sold
        if (tokensToSell.isNotEmpty()) {
            // Check global sell delay
            if (now - lastSellTime >= cfg.sellDelayBetweenTokensMs) {
                log.info("🚀 SELL ALL AT ONCE - WalletSellOnly: Selling ${tokensToSell.size} tokens simultaneously")
                
                var successCount = 0
                var failCount = 0
                
                // Execute all sells concurrently
                tokensToSell.forEach { (mint, reason) ->
                    log.info("💸 SELL NOW - WalletSellOnly: $mint - $reason")
                    val success = runtime.sell(mint)
                    if (success) {
                        successCount++
                        processedTokens[mint] = now
                        log.info("✅ Successfully sold wallet token: $mint")
                    } else {
                        failCount++
                        log.warn("❌ Failed to sell wallet token: $mint")
                        // Mark as processed anyway to avoid spam
                        processedTokens[mint] = now
                    }
                }
                
                lastSellTime = now
                log.info("📈 SELL ALL COMPLETE - WalletSellOnly: ${successCount} succeeded, ${failCount} failed out of ${tokensToSell.size} total")
            } else {
                val timeUntilNext = cfg.sellDelayBetweenTokensMs - (now - lastSellTime)
                log.info("⏸️ SELL ALL DELAYED - WalletSellOnly: ${tokensToSell.size} tokens waiting ${timeUntilNext}ms before batch sell")
                // Don't mark as processed so we try again next tick
            }
        }
        
        // Clean up old processed tokens entries (older than 1 hour)
        val oneHourAgo = now - 3600_000L
        processedTokens.entries.removeIf { it.value < oneHourAgo }
        
        log.info("📊 WalletSellOnly: Processed ${walletTokens.size} wallet tokens, ${processedTokens.size} in tracking")
    }
}

/**
 * Helper function to calculate USD price for a token using real market data.
 * This replaces the old token.tokenAmount.uiAmount ?: 1.0 approach
 * with proper USD pricing from market APIs for accurate strategy decisions.
 */
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
