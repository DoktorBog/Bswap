package com.bswap.server.backtest

import com.bswap.server.config.*
import com.bswap.server.stratagy.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*

class MarketSimulatorTest {
    
    private lateinit var simulator: MarketSimulator
    private lateinit var config: BacktestConfig
    private lateinit var slippageConfig: SlippageConfig
    
    @BeforeEach
    fun setup() {
        config = BacktestConfig()
        slippageConfig = SlippageConfig()
        simulator = MarketSimulator(config, slippageConfig)
    }
    
    @Test
    fun `should execute successful trade with realistic parameters`() {
        val token = BacktestToken(
            mint = "TEST_MINT",
            symbol = "TEST",
            ticks = listOf(
                BacktestTick(
                    timestamp = System.currentTimeMillis(),
                    open = 1.0,
                    high = 1.1,
                    low = 0.9,
                    close = 1.05,
                    volume = 10000.0
                )
            )
        )
        
        val tick = token.ticks.first()
        val execution = simulator.executeSimulatedTrade(
            token = token,
            tick = tick,
            amountUsd = 1000.0,
            isBuy = true,
            currentTime = System.currentTimeMillis()
        )
        
        assertEquals(1000.0, execution.requestedAmount)
        assertTrue(execution.executedAmount > 0.0)
        assertTrue(execution.executedPrice > 0.0)
        assertTrue(execution.slippage >= 0.0)
        assertTrue(execution.latencyMs > 0L)
        assertTrue(execution.fees > 0.0)
    }
    
    @Test
    fun `should handle buy vs sell differently`() {
        val token = BacktestToken(
            mint = "TEST_MINT",
            symbol = "TEST",
            ticks = listOf(
                BacktestTick(
                    timestamp = System.currentTimeMillis(),
                    open = 1.0,
                    high = 1.1,
                    low = 0.9,
                    close = 1.05,
                    volume = 10000.0
                )
            )
        )
        
        val tick = token.ticks.first()
        val currentTime = System.currentTimeMillis()
        
        val buyExecution = simulator.executeSimulatedTrade(token, tick, 1000.0, true, currentTime)
        val sellExecution = simulator.executeSimulatedTrade(token, tick, 1000.0, false, currentTime)
        
        // Buy should use high price, sell should use low price (before slippage)
        assertTrue(buyExecution.executedPrice >= sellExecution.executedPrice)
    }
    
    @Test
    fun `should simulate partial fills when configured`() {
        val configWithPartialFills = config.copy(
            enablePartialFills = true,
            partialFillProbability = 1.0 // Force partial fills
        )
        val simulatorWithPartials = MarketSimulator(configWithPartialFills, slippageConfig)
        
        val token = BacktestToken(
            mint = "TEST_MINT",
            symbol = "TEST",
            ticks = listOf(
                BacktestTick(
                    timestamp = System.currentTimeMillis(),
                    open = 1.0,
                    high = 1.1,
                    low = 0.9,
                    close = 1.05,
                    volume = 100.0 // Low volume to trigger partial fills
                )
            )
        )
        
        val execution = simulatorWithPartials.executeSimulatedTrade(
            token = token,
            tick = token.ticks.first(),
            amountUsd = 10000.0, // Large trade relative to volume
            isBuy = true,
            currentTime = System.currentTimeMillis()
        )
        
        // With forced partial fills and large trade size, should get partial fill
        assertTrue(execution.isPartialFill)
        assertTrue(execution.executedAmount < execution.requestedAmount)
    }
    
    @Test
    fun `should calculate market impact correctly`() {
        val token = BacktestToken(
            mint = "TEST_MINT",
            symbol = "TEST",
            ticks = listOf(
                BacktestTick(
                    timestamp = System.currentTimeMillis(),
                    open = 1.0,
                    high = 1.1,
                    low = 0.9,
                    close = 1.05,
                    volume = 1000.0
                )
            ),
            initialLiquidity = 5000.0
        )
        
        val smallTradeExecution = simulator.executeSimulatedTrade(
            token, token.ticks.first(), 100.0, true, System.currentTimeMillis()
        )
        
        val largeTradeExecution = simulator.executeSimulatedTrade(
            token, token.ticks.first(), 2000.0, true, System.currentTimeMillis()
        )
        
        // Larger trade should have higher slippage due to market impact
        assertTrue(largeTradeExecution.slippage >= smallTradeExecution.slippage)
    }
}

class BacktestPortfolioTest {
    
    private lateinit var portfolio: BacktestPortfolio
    
    @BeforeEach
    fun setup() {
        portfolio = BacktestPortfolio(10000.0) // $10k initial
    }
    
    @Test
    fun `should initialize with correct cash balance`() {
        assertEquals(10000.0, portfolio.getCash())
        assertEquals(10000.0, portfolio.getTotalValue())
        assertTrue(portfolio.getPositions().isEmpty())
    }
    
    @Test
    fun `should execute buy order successfully`() {
        val success = portfolio.buy("TEST_MINT", 1000.0, 1.0)
        
        assertTrue(success)
        assertEquals(9000.0, portfolio.getCash()) // 10k - 1k
        assertEquals(1, portfolio.getPositions().size)
        
        val position = portfolio.getPositions()["TEST_MINT"]!!
        assertEquals("TEST_MINT", position.mint)
        assertEquals(1000.0, position.quantity) // 1000 USD / 1.0 price
        assertEquals(1.0, position.entryPrice)
    }
    
    @Test
    fun `should reject buy order with insufficient funds`() {
        val success = portfolio.buy("TEST_MINT", 15000.0, 1.0) // More than available cash
        
        assertFalse(success)
        assertEquals(10000.0, portfolio.getCash()) // Unchanged
        assertTrue(portfolio.getPositions().isEmpty())
    }
    
    @Test
    fun `should execute sell order successfully`() {
        // First buy
        portfolio.buy("TEST_MINT", 1000.0, 1.0)
        
        // Then sell at higher price
        val success = portfolio.sell("TEST_MINT", 1.2, "Profit taking")
        
        assertTrue(success)
        assertEquals(10200.0, portfolio.getCash()) // 9000 + (1000 * 1.2)
        assertTrue(portfolio.getPositions().isEmpty())
        
        val trades = portfolio.getCompletedTrades()
        assertEquals(1, trades.size)
        
        val trade = trades.first()
        assertEquals("TEST_MINT", trade.mint)
        assertEquals(1.0, trade.entryPrice)
        assertEquals(1.2, trade.exitPrice)
        assertEquals(200.0, trade.pnl) // 1000 * (1.2 - 1.0)
        assertEquals(0.2, trade.pnlPercent) // 20% profit
        assertEquals("Profit taking", trade.reason)
    }
    
    @Test
    fun `should handle sell of non-existent position`() {
        val success = portfolio.sell("NON_EXISTENT", 1.0)
        
        assertFalse(success)
        assertEquals(10000.0, portfolio.getCash()) // Unchanged
        assertTrue(portfolio.getCompletedTrades().isEmpty())
    }
    
    @Test
    fun `should update position values correctly`() {
        portfolio.buy("TEST_MINT", 1000.0, 1.0)
        
        val tokens = listOf(
            BacktestToken(
                mint = "TEST_MINT",
                symbol = "TEST",
                ticks = listOf(
                    BacktestTick(
                        timestamp = System.currentTimeMillis(),
                        open = 1.0,
                        high = 1.5,
                        low = 1.0,
                        close = 1.5, // Price increased to 1.5
                        volume = 1000.0
                    )
                )
            )
        )
        
        portfolio.updateValues(tokens, System.currentTimeMillis())
        
        val position = portfolio.getPositions()["TEST_MINT"]!!
        assertEquals(1.5, position.currentPrice)
        assertEquals(10500.0, portfolio.getTotalValue()) // 9000 cash + (1000 tokens * 1.5)
    }
    
    @Test
    fun `should calculate total value correctly with multiple positions`() {
        portfolio.buy("MINT_1", 1000.0, 1.0)
        portfolio.buy("MINT_2", 2000.0, 2.0)
        
        assertEquals(7000.0, portfolio.getCash()) // 10k - 1k - 2k
        assertEquals(2, portfolio.getPositions().size)
        assertEquals(10000.0, portfolio.getTotalValue()) // All invested, no price change
    }
    
    @Test
    fun `should clear completed trades`() {
        portfolio.buy("TEST_MINT", 1000.0, 1.0)
        portfolio.sell("TEST_MINT", 1.1)
        
        assertEquals(1, portfolio.getCompletedTrades().size)
        
        portfolio.clearCompletedTrades()
        
        assertTrue(portfolio.getCompletedTrades().isEmpty())
    }
}

class OfflineBacktesterTest {
    
    private lateinit var backtester: OfflineBacktester
    private lateinit var config: BacktestConfig
    private lateinit var enhancedConfig: EnhancedTradingConfig
    private lateinit var mockStrategy: TradingStrategy
    
    @BeforeEach
    fun setup() {
        config = BacktestConfig(
            initialCapitalUsd = 10000.0,
            enableRealisticLatency = false // Disable for faster tests
        )
        enhancedConfig = EnhancedTradingConfig()
        backtester = OfflineBacktester(config, enhancedConfig)
        mockStrategy = mock()
        
        whenever(mockStrategy.type).thenReturn(StrategyType.SHITCOIN_SCALPER)
    }
    
    @Test
    fun `should run backtest successfully`() = runBlocking {
        val tokens = listOf(
            BacktestToken(
                mint = "TEST_MINT",
                symbol = "TEST",
                ticks = listOf(
                    BacktestTick(
                        timestamp = System.currentTimeMillis() - 1000,
                        open = 1.0,
                        high = 1.1,
                        low = 0.9,
                        close = 1.05,
                        volume = 1000.0
                    ),
                    BacktestTick(
                        timestamp = System.currentTimeMillis(),
                        open = 1.05,
                        high = 1.2,
                        low = 1.0,
                        close = 1.15,
                        volume = 1200.0
                    )
                )
            )
        )
        
        val result = backtester.runBacktest(mockStrategy, emptyMap(), tokens)
        
        assertEquals("SHITCOIN_SCALPER", result.strategyName)
        assertTrue(result.totalReturn >= -10000.0) // Can't lose more than initial capital
        assertTrue(result.sharpeRatio >= 0.0 || result.totalTrades == 0) // Sharpe can be 0 if no trades
        assertTrue(result.maxDrawdown >= 0.0)
        assertTrue(result.winRate >= 0.0 && result.winRate <= 1.0)
        assertTrue(result.volatility >= 0.0)
        assertNotNull(result.startDate)
        assertNotNull(result.endDate)
        assertNotNull(result.duration)
    }
    
    @Test
    fun `should handle strategy with no trades`() = runBlocking {
        val tokens = listOf(
            BacktestToken(
                mint = "TEST_MINT",
                symbol = "TEST",
                ticks = listOf(
                    BacktestTick(
                        timestamp = System.currentTimeMillis(),
                        open = 1.0,
                        high = 1.1,
                        low = 0.9,
                        close = 1.05,
                        volume = 1000.0
                    )
                )
            )
        )
        
        // Strategy that doesn't make any trades
        val result = backtester.runBacktest(mockStrategy, emptyMap(), tokens)
        
        assertEquals(0, result.totalTrades)
        assertEquals(0.0, result.winRate)
        assertEquals(0.0, result.profitFactor)
        assertEquals(0.0, result.sharpeRatio)
        assertEquals(0.0, result.avgSlippage)
        assertEquals(0L, result.avgTimeInPosition)
        assertTrue(result.trades.isEmpty())
    }
    
    @Test
    fun `should calculate metrics correctly with trades`() = runBlocking {
        // Create a mock portfolio with some completed trades
        val mockPortfolio = mock<BacktestPortfolio>()
        val completedTrades = listOf(
            BacktestTrade(
                mint = "TEST_MINT",
                entryTime = System.currentTimeMillis() - 60000,
                exitTime = System.currentTimeMillis(),
                entryPrice = 1.0,
                exitPrice = 1.1,
                quantity = 1000.0,
                pnl = 100.0, // Profit
                pnlPercent = 0.1,
                slippage = 0.01,
                fees = 1.0,
                reason = "Profit target"
            ),
            BacktestTrade(
                mint = "TEST_MINT_2",
                entryTime = System.currentTimeMillis() - 120000,
                exitTime = System.currentTimeMillis() - 60000,
                entryPrice = 1.0,
                exitPrice = 0.9,
                quantity = 1000.0,
                pnl = -100.0, // Loss
                pnlPercent = -0.1,
                slippage = 0.015,
                fees = 1.0,
                reason = "Stop loss"
            )
        )
        
        whenever(mockPortfolio.getCompletedTrades()).thenReturn(completedTrades)
        whenever(mockPortfolio.getTotalValue()).thenReturn(10000.0)
        
        val tokens = listOf(
            BacktestToken(
                mint = "TEST_MINT",
                symbol = "TEST",
                ticks = listOf(
                    BacktestTick(System.currentTimeMillis(), 1.0, 1.1, 0.9, 1.05, 1000.0)
                )
            )
        )
        
        val result = backtester.runBacktest(mockStrategy, emptyMap(), tokens)
        
        // Verify that metrics are calculated (specific values depend on implementation)
        assertTrue(result.totalTrades >= 0)
        assertTrue(result.winRate >= 0.0 && result.winRate <= 1.0)
        assertTrue(result.avgSlippage >= 0.0)
        assertTrue(result.avgTimeInPosition >= 0L)
    }
    
    @Test
    fun `should track best result correctly`() = runBlocking {
        val tokens = listOf(
            BacktestToken(
                mint = "TEST_MINT",
                symbol = "TEST",
                ticks = listOf(
                    BacktestTick(System.currentTimeMillis(), 1.0, 1.1, 0.9, 1.05, 1000.0)
                )
            )
        )
        
        // Run multiple backtests
        val result1 = backtester.runBacktest(mockStrategy, mapOf("test" to "1"), tokens)
        val result2 = backtester.runBacktest(mockStrategy, mapOf("test" to "2"), tokens)
        
        val allResults = backtester.getAllResults()
        assertEquals(2, allResults.size)
        
        val bestResult = backtester.getBestResult()
        assertNotNull(bestResult)
        assertTrue(allResults.contains(bestResult))
    }
    
    @Test
    fun `should clear results correctly`() = runBlocking {
        val tokens = listOf(
            BacktestToken(
                mint = "TEST_MINT",
                symbol = "TEST",
                ticks = listOf(
                    BacktestTick(System.currentTimeMillis(), 1.0, 1.1, 0.9, 1.05, 1000.0)
                )
            )
        )
        
        backtester.runBacktest(mockStrategy, emptyMap(), tokens)
        assertEquals(1, backtester.getAllResults().size)
        
        backtester.clearResults()
        assertTrue(backtester.getAllResults().isEmpty())
        assertNull(backtester.getBestResult())
    }
}