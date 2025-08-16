package com.bswap.server.backtest

import com.bswap.server.config.*
import com.bswap.server.stratagy.*
import com.bswap.server.*
import com.bswap.shared.wallet.WalletConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*
import kotlin.random.Random

/**
 * Generate a Gaussian (normal) distributed random number using Box-Muller transform
 */
private fun generateGaussian(random: Random): Double {
    val u1 = random.nextDouble()
    val u2 = random.nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}

/**
 * Comprehensive offline backtester with realistic market simulation
 * Simulates slippage, spread, partial fills, RPC/Jito delays, and order latency
 */

// =================================================================================================
// MARKET DATA & SIMULATION
// =================================================================================================

data class BacktestTick(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val trades: Int = 1
) {
    val typical: Double get() = (high + low + close) / 3.0
    val volatility: Double get() = (high - low) / low
}

data class BacktestToken(
    val mint: String,
    val symbol: String,
    val ticks: List<BacktestTick>,
    val initialLiquidity: Double = 10_000.0,
    val marketCap: Double = 100_000.0
) {
    fun getTickAt(timestamp: Long): BacktestTick? {
        return ticks.find { abs(it.timestamp - timestamp) <= 1000L } // 1 second tolerance
    }
    
    fun getTickRange(startTime: Long, endTime: Long): List<BacktestTick> {
        return ticks.filter { it.timestamp in startTime..endTime }
    }
}

class MarketSimulator(
    private val config: BacktestConfig,
    private val slippageConfig: SlippageConfig
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MarketSimulator::class.java)
    }

    private val random = Random(42) // Deterministic for reproducible results

    data class TradeExecution(
        val requestedAmount: Double,
        val executedAmount: Double,
        val executedPrice: Double,
        val slippage: Double,
        val latencyMs: Long,
        val success: Boolean,
        val fees: Double,
        val isPartialFill: Boolean
    )

    /**
     * Simulate realistic trade execution with slippage, latency, and partial fills
     */
    fun executeSimulatedTrade(
        token: BacktestToken,
        tick: BacktestTick,
        amountUsd: Double,
        isBuy: Boolean,
        currentTime: Long
    ): TradeExecution {
        // Simulate network latency
        val latency = simulateLatency()
        
        // Get market state at execution time (after latency)
        val executionTick = token.getTickAt(currentTime + latency) ?: tick
        
        // Calculate base price
        val basePrice = if (isBuy) executionTick.high else executionTick.low
        
        // Calculate market impact and slippage
        val marketImpact = calculateMarketImpact(amountUsd, token.initialLiquidity, executionTick.volume)
        val slippage = calculateSlippage(marketImpact, executionTick.volatility)
        
        // Apply slippage to price
        val slippageMultiplier = if (isBuy) 1.0 + slippage else 1.0 - slippage
        val executedPrice = basePrice * slippageMultiplier
        
        // Determine if partial fill occurs
        val isPartialFill = shouldPartialFill(amountUsd, executionTick.volume)
        val fillRatio = if (isPartialFill) 0.3 + random.nextDouble() * 0.5 else 1.0 // 30-80% fill
        
        // Calculate executed amount
        val executedAmount = amountUsd * fillRatio
        
        // Calculate fees
        val fees = executedAmount * config.commissionPercent / 100.0
        
        // Determine if trade succeeds
        val success = determineTradSuccess(marketImpact, slippage)
        
        logger.debug("📊 SIMULATED TRADE: ${if (isBuy) "BUY" else "SELL"} ${"%.2f".format(executedAmount)} @ ${"%.6f".format(executedPrice)} (slippage: ${"%.2f".format(slippage * 100)}%)")
        
        return TradeExecution(
            requestedAmount = amountUsd,
            executedAmount = if (success) executedAmount else 0.0,
            executedPrice = executedPrice,
            slippage = slippage,
            latencyMs = latency,
            success = success,
            fees = fees,
            isPartialFill = isPartialFill
        )
    }

    private fun simulateLatency(): Long {
        // Normal distribution around average latency
        val latency = generateGaussian(random) * config.latencyStdDevMs + config.avgLatencyMs
        return maxOf(50L, latency.toLong()) // Minimum 50ms latency
    }

    private fun calculateMarketImpact(tradeSize: Double, liquidity: Double, volume: Double): Double {
        // Square root model for market impact
        val liquidityRatio = tradeSize / liquidity
        val volumeRatio = tradeSize / (volume + 1.0) // Add 1 to avoid division by zero
        
        return config.marketImpactFactor * sqrt(liquidityRatio) * (1.0 + volumeRatio)
    }

    private fun calculateSlippage(marketImpact: Double, volatility: Double): Double {
        val baseSlippage = slippageConfig.targetSlippagePercent / 100.0
        val impactSlippage = marketImpact * slippageConfig.liquiditySlippageMultiplier
        val volatilitySlippage = volatility * slippageConfig.volatilitySlippageMultiplier
        
        val totalSlippage = baseSlippage + impactSlippage + volatilitySlippage
        return minOf(totalSlippage, slippageConfig.maxSlippagePercent / 100.0)
    }

    private fun shouldPartialFill(tradeSize: Double, volume: Double): Boolean {
        if (!config.enablePartialFills) return false
        
        // Higher chance of partial fill for larger trades relative to volume
        val sizeRatio = tradeSize / (volume + 1.0)
        val partialFillProb = config.partialFillProbability * (1.0 + sizeRatio * 5.0)
        
        return random.nextDouble() < partialFillProb
    }

    private fun determineTradSuccess(marketImpact: Double, slippage: Double): Boolean {
        // Higher market impact and slippage reduce success probability
        val failureProb = marketImpact * 10.0 + slippage * 2.0
        return random.nextDouble() > failureProb
    }
}

// =================================================================================================
// BACKTESTER ENGINE
// =================================================================================================

class OfflineBacktester(
    private val config: BacktestConfig,
    private val enhancedConfig: EnhancedTradingConfig = EnhancedTradingConfig()
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OfflineBacktester::class.java)
    }

    private val simulator = MarketSimulator(config, enhancedConfig.slippage)
    private val results = mutableListOf<BacktestResult>()

    data class BacktestResult(
        val strategyName: String,
        val parameters: Map<String, Any>,
        val totalReturn: Double,
        val totalReturnPercent: Double,
        val sharpeRatio: Double,
        val maxDrawdown: Double,
        val winRate: Double,
        val profitFactor: Double,
        val totalTrades: Int,
        val avgSlippage: Double,
        val avgTimeInPosition: Long,
        val volatility: Double,
        val calmarRatio: Double,
        val valueAtRisk: Double,
        val trades: List<BacktestTrade>,
        val equity: List<EquityPoint>,
        val startDate: String,
        val endDate: String,
        val duration: String
    )

    data class BacktestTrade(
        val mint: String,
        val entryTime: Long,
        val exitTime: Long,
        val entryPrice: Double,
        val exitPrice: Double,
        val quantity: Double,
        val pnl: Double,
        val pnlPercent: Double,
        val slippage: Double,
        val fees: Double,
        val reason: String
    )

    data class EquityPoint(
        val timestamp: Long,
        val equity: Double,
        val drawdown: Double
    )

    /**
     * Run comprehensive backtest for a strategy
     */
    suspend fun runBacktest(
        strategy: TradingStrategy,
        parameters: Map<String, Any>,
        tokens: List<BacktestToken>
    ): BacktestResult {
        logger.info("🚀 BACKTEST START: ${strategy.type} with ${tokens.size} tokens")
        
        val startTime = System.currentTimeMillis()
        val portfolio = BacktestPortfolio(config.initialCapitalUsd)
        val trades = mutableListOf<BacktestTrade>()
        val equity = mutableListOf<EquityPoint>()
        
        // Create mock trading runtime
        val runtime = MockTradingRuntime(portfolio, simulator, tokens)
        
        // Get all unique timestamps and sort them
        val allTimestamps = tokens.flatMap { it.ticks.map { tick -> tick.timestamp } }
            .distinct()
            .sorted()
        
        var peakEquity = config.initialCapitalUsd
        val random = Random(42)
        
        // Simulate trading over time
        for (timestamp in allTimestamps) {
            runtime.currentTime = timestamp
            
            // Update portfolio with current prices
            portfolio.updateValues(tokens, timestamp)
            
            // Record equity
            val currentEquity = portfolio.getTotalValue()
            if (currentEquity > peakEquity) peakEquity = currentEquity
            val drawdown = (peakEquity - currentEquity) / peakEquity
            equity.add(EquityPoint(timestamp, currentEquity, drawdown))
            
            // Discover tokens (simulate new token discovery)
            tokens.forEach { token ->
                val tick = token.getTickAt(timestamp)
                if (tick != null && random.nextDouble() < 0.001) { // 0.1% chance per tick
                    val meta = TokenMeta(token.mint, TokenSource.PUMPFUN)
                    strategy.onDiscovered(meta, runtime)
                }
            }
            
            // Strategy tick
            strategy.onTick(runtime)
            
            // Process completed trades
            portfolio.getCompletedTrades().forEach { trade ->
                trades.add(trade)
                logger.debug("📊 TRADE COMPLETED: ${trade.mint} P&L: ${"%.2f".format(trade.pnlPercent)}%")
            }
            portfolio.clearCompletedTrades()
            
            // Simulate realistic delay between ticks
            if (config.enableRealisticLatency) {
                delay(1) // 1ms delay to simulate processing time
            }
        }
        
        val endTime = System.currentTimeMillis()
        
        // Calculate final metrics
        val finalEquity = portfolio.getTotalValue()
        val totalReturn = finalEquity - config.initialCapitalUsd
        val totalReturnPercent = totalReturn / config.initialCapitalUsd
        
        val result = calculateBacktestMetrics(
            strategy.type.toString(),
            parameters,
            trades,
            equity,
            totalReturn,
            totalReturnPercent,
            config.startDate,
            config.endDate,
            endTime - startTime
        )
        
        results.add(result)
        logger.info("✅ BACKTEST COMPLETE: ${strategy.type} - Return: ${"%.2f".format(totalReturnPercent * 100)}%, Sharpe: ${"%.2f".format(result.sharpeRatio)}")
        
        return result
    }

    private fun calculateBacktestMetrics(
        strategyName: String,
        parameters: Map<String, Any>,
        trades: List<BacktestTrade>,
        equity: List<EquityPoint>,
        totalReturn: Double,
        totalReturnPercent: Double,
        startDate: String,
        endDate: String,
        durationMs: Long
    ): BacktestResult {
        
        if (trades.isEmpty()) {
            return BacktestResult(
                strategyName = strategyName,
                parameters = parameters,
                totalReturn = totalReturn,
                totalReturnPercent = totalReturnPercent,
                sharpeRatio = 0.0,
                maxDrawdown = 0.0,
                winRate = 0.0,
                profitFactor = 0.0,
                totalTrades = 0,
                avgSlippage = 0.0,
                avgTimeInPosition = 0L,
                volatility = 0.0,
                calmarRatio = 0.0,
                valueAtRisk = 0.0,
                trades = trades,
                equity = equity,
                startDate = startDate,
                endDate = endDate,
                duration = formatDuration(durationMs)
            )
        }
        
        // Win rate
        val winningTrades = trades.count { it.pnl > 0 }
        val winRate = winningTrades.toDouble() / trades.size
        
        // Profit factor
        val grossProfit = trades.filter { it.pnl > 0 }.sumOf { it.pnl }
        val grossLoss = abs(trades.filter { it.pnl < 0 }.sumOf { it.pnl })
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else Double.POSITIVE_INFINITY
        
        // Average slippage
        val avgSlippage = trades.map { it.slippage }.average()
        
        // Average time in position
        val avgTimeInPosition = trades.map { it.exitTime - it.entryTime }.average().toLong()
        
        // Sharpe ratio (assuming daily returns)
        val returns = equity.zipWithNext { a, b -> (b.equity - a.equity) / a.equity }
        val avgReturn = returns.average()
        val returnStdDev = if (returns.size > 1) {
            sqrt(returns.map { (it - avgReturn).pow(2) }.average())
        } else 0.0
        val sharpeRatio = if (returnStdDev > 0) avgReturn / returnStdDev * sqrt(252.0) else 0.0
        
        // Maximum drawdown
        val maxDrawdown = equity.maxOfOrNull { it.drawdown } ?: 0.0
        
        // Volatility (annualized)
        val volatility = returnStdDev * sqrt(252.0)
        
        // Calmar ratio
        val calmarRatio = if (maxDrawdown > 0) totalReturnPercent / maxDrawdown else 0.0
        
        // Value at Risk (5% VaR)
        val sortedReturns = returns.sorted()
        val varIndex = (sortedReturns.size * 0.05).toInt()
        val valueAtRisk = if (varIndex < sortedReturns.size) abs(sortedReturns[varIndex]) else 0.0
        
        return BacktestResult(
            strategyName = strategyName,
            parameters = parameters,
            totalReturn = totalReturn,
            totalReturnPercent = totalReturnPercent,
            sharpeRatio = sharpeRatio,
            maxDrawdown = maxDrawdown,
            winRate = winRate,
            profitFactor = profitFactor,
            totalTrades = trades.size,
            avgSlippage = avgSlippage,
            avgTimeInPosition = avgTimeInPosition,
            volatility = volatility,
            calmarRatio = calmarRatio,
            valueAtRisk = valueAtRisk,
            trades = trades,
            equity = equity,
            startDate = startDate,
            endDate = endDate,
            duration = formatDuration(durationMs)
        )
    }
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return "${hours}h ${minutes % 60}m ${seconds % 60}s"
    }

    fun getAllResults(): List<BacktestResult> = results.toList()
    
    fun getBestResult(): BacktestResult? = results.maxByOrNull { it.sharpeRatio }
    
    fun clearResults() = results.clear()
}

// =================================================================================================
// PORTFOLIO SIMULATION
// =================================================================================================

class BacktestPortfolio(initialCash: Double) {
    private var cash = initialCash
    private val positions = mutableMapOf<String, BacktestPosition>()
    private val completedTrades = mutableListOf<OfflineBacktester.BacktestTrade>()
    
    data class BacktestPosition(
        val mint: String,
        var quantity: Double,
        val entryPrice: Double,
        val entryTime: Long,
        var currentPrice: Double = entryPrice
    )
    
    fun buy(mint: String, amountUsd: Double, price: Double): Boolean {
        if (cash < amountUsd) return false
        
        val quantity = amountUsd / price
        cash -= amountUsd
        
        positions[mint] = BacktestPosition(
            mint = mint,
            quantity = quantity,
            entryPrice = price,
            entryTime = System.currentTimeMillis(),
            currentPrice = price
        )
        
        return true
    }
    
    fun sell(mint: String, price: Double, reason: String = "Manual"): Boolean {
        val position = positions.remove(mint) ?: return false
        
        val proceeds = position.quantity * price
        cash += proceeds
        
        val pnl = proceeds - (position.quantity * position.entryPrice)
        val pnlPercent = pnl / (position.quantity * position.entryPrice)
        
        completedTrades.add(
            OfflineBacktester.BacktestTrade(
                mint = mint,
                entryTime = position.entryTime,
                exitTime = System.currentTimeMillis(),
                entryPrice = position.entryPrice,
                exitPrice = price,
                quantity = position.quantity,
                pnl = pnl,
                pnlPercent = pnlPercent,
                slippage = 0.01, // Placeholder
                fees = proceeds * 0.001, // 0.1% fee
                reason = reason
            )
        )
        
        return true
    }
    
    fun updateValues(tokens: List<BacktestToken>, timestamp: Long) {
        positions.values.forEach { position ->
            val token = tokens.find { it.mint == position.mint }
            val tick = token?.getTickAt(timestamp)
            if (tick != null) {
                position.currentPrice = tick.close
            }
        }
    }
    
    fun getTotalValue(): Double {
        val positionValue = positions.values.sumOf { it.quantity * it.currentPrice }
        return cash + positionValue
    }
    
    fun getCash(): Double = cash
    
    fun getPositions(): Map<String, BacktestPosition> = positions.toMap()
    
    fun getCompletedTrades(): List<OfflineBacktester.BacktestTrade> = completedTrades.toList()
    
    fun clearCompletedTrades() = completedTrades.clear()
}

// =================================================================================================
// MOCK TRADING RUNTIME
// =================================================================================================

class MockTradingRuntime(
    private val portfolio: BacktestPortfolio,
    private val simulator: MarketSimulator,
    private val tokens: List<BacktestToken>
) : TradingRuntime {
    
    var currentTime: Long = System.currentTimeMillis()
    
    override val walletConfig: WalletConfig
        get() = WalletConfig.current()
    
    override val config: SolanaSwapBotConfig
        get() = SolanaSwapBotConfig()
    
    override val getPriceHistory: (suspend (String) -> List<Double>?)?
        get() = { mint ->
            val token = tokens.find { it.mint == mint }
            token?.ticks?.map { it.close }
        }
    
    override fun now(): Long = currentTime
    
    override fun isNew(mint: String): Boolean = !portfolio.getPositions().containsKey(mint)
    
    override fun status(mint: String): TokenStatus? {
        return if (portfolio.getPositions().containsKey(mint)) {
            TokenStatus(mint, TokenState.Swapped)
        } else null
    }
    
    override suspend fun buy(mint: String): Boolean {
        val token = tokens.find { it.mint == mint } ?: return false
        val tick = token.getTickAt(currentTime) ?: return false
        
        val execution = simulator.executeSimulatedTrade(
            token = token,
            tick = tick,
            amountUsd = 1000.0, // Default trade size
            isBuy = true,
            currentTime = currentTime
        )
        
        return if (execution.success) {
            portfolio.buy(mint, execution.executedAmount, execution.executedPrice)
        } else false
    }
    
    override suspend fun sell(mint: String): Boolean {
        val token = tokens.find { it.mint == mint } ?: return false
        val tick = token.getTickAt(currentTime) ?: return false
        
        val execution = simulator.executeSimulatedTrade(
            token = token,
            tick = tick,
            amountUsd = 1000.0, // This will be ignored for sells
            isBuy = false,
            currentTime = currentTime
        )
        
        return if (execution.success) {
            portfolio.sell(mint, execution.executedPrice, "Strategy")
        } else false
    }
    
    override suspend fun tokenInfo(mint: String): com.bswap.server.data.solana.transaction.TokenInfo? {
        // Mock implementation - return null as we don't need full token info for backtesting
        return null
    }
    
    override suspend fun allTokens(): List<com.bswap.server.data.solana.transaction.TokenInfo> {
        // Mock implementation - return empty list as we track positions differently
        return emptyList()
    }
    
    override suspend fun getTokenUsdPrice(mint: String): Double? {
        val token = tokens.find { it.mint == mint } ?: return null
        val tick = token.getTickAt(currentTime) ?: return null
        return tick.close
    }
}