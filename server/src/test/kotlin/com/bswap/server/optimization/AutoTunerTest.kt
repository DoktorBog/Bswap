package com.bswap.server.optimization

import com.bswap.server.backtest.*
import com.bswap.server.config.*
import com.bswap.server.stratagy.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*
import kotlin.random.Random

class ParameterGridTest {
    
    private lateinit var parameterGrid: ParameterGrid
    
    @BeforeEach
    fun setup() {
        parameterGrid = ParameterGrid()
    }
    
    @Test
    fun `should add parameters correctly`() {
        val param = Parameter(
            name = "testParam",
            type = ParameterType.CONTINUOUS,
            minValue = 0.0,
            maxValue = 1.0,
            defaultValue = 0.5
        )
        
        parameterGrid.addParameter(param)
        
        assertTrue(parameterGrid.getParameterNames().contains("testParam"))
        assertEquals(param, parameterGrid.getParameter("testParam"))
    }
    
    @Test
    fun `should generate random parameter set for continuous parameter`() {
        val param = Parameter(
            name = "continuous",
            type = ParameterType.CONTINUOUS,
            minValue = 0.0,
            maxValue = 10.0,
            defaultValue = 5.0
        )
        
        parameterGrid.addParameter(param)
        val paramSet = parameterGrid.generateRandomParameterSet(Random(42))
        
        assertTrue(paramSet.values.containsKey("continuous"))
        val value = paramSet.values["continuous"] as Double
        assertTrue(value >= 0.0 && value <= 10.0)
    }
    
    @Test
    fun `should generate random parameter set for discrete parameter`() {
        val param = Parameter(
            name = "discrete",
            type = ParameterType.DISCRETE,
            minValue = 1.0,
            maxValue = 10.0,
            stepSize = 1.0,
            defaultValue = 5
        )
        
        parameterGrid.addParameter(param)
        val paramSet = parameterGrid.generateRandomParameterSet(Random(42))
        
        assertTrue(paramSet.values.containsKey("discrete"))
        val value = paramSet.values["discrete"] as Double
        assertTrue(value >= 1.0 && value <= 10.0)
        assertEquals(0.0, value % 1.0, 0.001) // Should be integer
    }
    
    @Test
    fun `should generate random parameter set for categorical parameter`() {
        val param = Parameter(
            name = "categorical",
            type = ParameterType.CATEGORICAL,
            minValue = 0.0,
            maxValue = 0.0,
            discreteValues = listOf("A", "B", "C"),
            defaultValue = "B"
        )
        
        parameterGrid.addParameter(param)
        val paramSet = parameterGrid.generateRandomParameterSet(Random(42))
        
        assertTrue(paramSet.values.containsKey("categorical"))
        val value = paramSet.values["categorical"] as String
        assertTrue(value in listOf("A", "B", "C"))
    }
    
    @Test
    fun `should generate random parameter set for boolean parameter`() {
        val param = Parameter(
            name = "boolean",
            type = ParameterType.BOOLEAN,
            minValue = 0.0,
            maxValue = 1.0,
            defaultValue = true
        )
        
        parameterGrid.addParameter(param)
        val paramSet = parameterGrid.generateRandomParameterSet(Random(42))
        
        assertTrue(paramSet.values.containsKey("boolean"))
        val value = paramSet.values["boolean"] as Boolean
        assertTrue(value is Boolean)
    }
    
    @Test
    fun `should mutate parameter set correctly`() {
        val param = Parameter(
            name = "continuous",
            type = ParameterType.CONTINUOUS,
            minValue = 0.0,
            maxValue = 10.0,
            defaultValue = 5.0
        )
        
        parameterGrid.addParameter(param)
        val original = ParameterSet(mapOf("continuous" to 5.0))
        
        val mutated = parameterGrid.mutateParameterSet(original, 1.0, Random(42)) // 100% mutation rate
        
        val originalValue = original.values["continuous"] as Double
        val mutatedValue = mutated.values["continuous"] as Double
        assertNotEquals(originalValue, mutatedValue)
        assertTrue(mutatedValue >= 0.0 && mutatedValue <= 10.0)
    }
    
    @Test
    fun `should perform crossover correctly`() {
        val param = Parameter(
            name = "continuous",
            type = ParameterType.CONTINUOUS,
            minValue = 0.0,
            maxValue = 10.0,
            defaultValue = 5.0
        )
        
        parameterGrid.addParameter(param)
        val parent1 = ParameterSet(mapOf("continuous" to 2.0))
        val parent2 = ParameterSet(mapOf("continuous" to 8.0))
        
        val child = parameterGrid.crossover(parent1, parent2, Random(42))
        
        val childValue = child.values["continuous"] as Double
        assertTrue(childValue >= 2.0 && childValue <= 8.0)
    }
    
    @Test
    fun `should handle integer parameters correctly`() {
        val param = Parameter(
            name = "integer",
            type = ParameterType.INTEGER,
            minValue = 1.0,
            maxValue = 100.0,
            defaultValue = 50
        )
        
        parameterGrid.addParameter(param)
        val paramSet = parameterGrid.generateRandomParameterSet(Random(42))
        
        val value = paramSet.values["integer"] as Int
        assertTrue(value >= 1 && value <= 100)
    }
}

class ConstraintValidatorTest {
    
    private lateinit var constraintValidator: ConstraintValidator
    private lateinit var config: ConstraintConfig
    
    @BeforeEach
    fun setup() {
        config = ConstraintConfig()
        constraintValidator = ConstraintValidator(config)
    }
    
    @Test
    fun `should validate good result as valid`() {
        val goodResult = BacktestResult(
            strategyName = "TestStrategy",
            parameters = emptyMap(),
            totalReturn = 1000.0,
            totalReturnPercent = 0.2, // 20% return
            sharpeRatio = 2.5,
            maxDrawdown = 0.05, // 5% drawdown
            winRate = 0.7, // 70% win rate
            profitFactor = 2.0,
            totalTrades = 100,
            avgSlippage = 0.01,
            avgTimeInPosition = 60000L,
            volatility = 0.15,
            calmarRatio = 4.0,
            valueAtRisk = 0.03,
            trades = emptyList(),
            equity = emptyList(),
            startDate = "2024-01-01",
            endDate = "2024-12-31",
            duration = "1h 0m 0s"
        )
        
        val constraintResult = constraintValidator.validateConstraints(goodResult)
        
        assertTrue(constraintResult.isValid)
        assertTrue(constraintResult.violations.isEmpty())
        assertTrue(constraintResult.score > 0.9)
    }
    
    @Test
    fun `should detect constraint violations`() {
        val badResult = BacktestResult(
            strategyName = "TestStrategy",
            parameters = emptyMap(),
            totalReturn = -500.0,
            totalReturnPercent = -0.1, // 10% loss
            sharpeRatio = 0.5, // Low Sharpe
            maxDrawdown = 0.25, // 25% drawdown (too high)
            winRate = 0.3, // 30% win rate (too low)
            profitFactor = 0.8, // Less than 1
            totalTrades = 100,
            avgSlippage = 0.01,
            avgTimeInPosition = 60000L,
            volatility = 0.35, // High volatility
            calmarRatio = -0.4,
            valueAtRisk = 0.15, // High VaR
            trades = emptyList(),
            equity = emptyList(),
            startDate = "2024-01-01",
            endDate = "2024-12-31",
            duration = "1h 0m 0s"
        )
        
        val constraintResult = constraintValidator.validateConstraints(badResult)
        
        assertFalse(constraintResult.isValid)
        assertTrue(constraintResult.violations.isNotEmpty())
        assertTrue(constraintResult.score < 0.5)
    }
    
    @Test
    fun `should calculate fitness correctly for different objectives`() {
        val result = BacktestResult(
            strategyName = "TestStrategy",
            parameters = emptyMap(),
            totalReturn = 1000.0,
            totalReturnPercent = 0.15,
            sharpeRatio = 1.8,
            maxDrawdown = 0.08,
            winRate = 0.65,
            profitFactor = 1.5,
            totalTrades = 100,
            avgSlippage = 0.01,
            avgTimeInPosition = 60000L,
            volatility = 0.18,
            calmarRatio = 1.875,
            valueAtRisk = 0.05,
            trades = emptyList(),
            equity = emptyList(),
            startDate = "2024-01-01",
            endDate = "2024-12-31",
            duration = "1h 0m 0s"
        )
        
        val objectives = listOf(
            ObjectiveFunction.TOTAL_RETURN,
            ObjectiveFunction.SHARPE_RATIO,
            ObjectiveFunction.CALMAR_RATIO,
            ObjectiveFunction.WIN_RATE,
            ObjectiveFunction.PROFIT_FACTOR,
            ObjectiveFunction.MULTI_OBJECTIVE
        )
        
        objectives.forEach { objective ->
            val fitness = constraintValidator.calculateFitness(result, objective)
            assertTrue(fitness > 0.0, "Fitness should be positive for $objective")
        }
    }
}

class GeneticOptimizerTest {
    
    private lateinit var optimizer: GeneticOptimizer
    private lateinit var config: OptimizationConfig
    private lateinit var constraintConfig: ConstraintConfig
    private lateinit var backtester: OfflineBacktester
    private lateinit var tokens: List<BacktestToken>
    
    @BeforeEach
    fun setup() {
        config = OptimizationConfig().apply {
            populationSize = 10
            maxIterations = 5
            convergenceThreshold = 0.001
            maxStagnantGenerations = 3
        }
        constraintConfig = ConstraintConfig()
        backtester = mock()
        tokens = listOf(
            BacktestToken(
                mint = "TEST_MINT_1",
                symbol = "TEST1",
                ticks = listOf(
                    BacktestTick(System.currentTimeMillis(), 1.0, 1.1, 0.9, 1.05, 1000.0)
                )
            )
        )
        
        optimizer = GeneticOptimizer(config, constraintConfig, backtester, tokens)
    }
    
    @Test
    fun `should optimize strategy parameters`() = runBlocking {
        val mockResult = BacktestResult(
            strategyName = "TestStrategy",
            parameters = emptyMap(),
            totalReturn = 100.0,
            totalReturnPercent = 0.1,
            sharpeRatio = 1.5,
            maxDrawdown = 0.05,
            winRate = 0.6,
            profitFactor = 1.2,
            totalTrades = 50,
            avgSlippage = 0.01,
            avgTimeInPosition = 30000L,
            volatility = 0.2,
            calmarRatio = 2.0,
            valueAtRisk = 0.04,
            trades = emptyList(),
            equity = emptyList(),
            startDate = "2024-01-01",
            endDate = "2024-12-31",
            duration = "1h 0m 0s"
        )
        
        whenever(backtester.runBacktest(any(), any(), any())).thenReturn(mockResult)
        
        val parameterGrid = ParameterGrid().apply {
            addParameter(Parameter("param1", ParameterType.CONTINUOUS, 0.0, 1.0, defaultValue = 0.5))
        }
        
        val strategyFactory: (ParameterSet) -> TradingStrategy = { _ ->
            mock<TradingStrategy>()
        }
        
        val result = optimizer.optimize(strategyFactory, parameterGrid)
        
        assertNotNull(result.bestParameterSet)
        assertNotNull(result.bestResult)
        assertTrue(result.allResults.isNotEmpty())
        assertTrue(result.generations <= config.maxIterations)
        assertTrue(result.convergenceHistory.isNotEmpty())
        assertTrue(result.executionTimeMs > 0)
    }
}

class AutoTunerTest {
    
    private lateinit var autoTuner: AutoTuner
    private lateinit var config: EnhancedTradingConfig
    
    @BeforeEach
    fun setup() {
        config = EnhancedTradingConfig().apply {
            optimization = OptimizationConfig().apply {
                populationSize = 5
                maxIterations = 3
            }
        }
        autoTuner = AutoTuner(config)
    }
    
    @Test
    fun `should create parameter grid for shitcoin scalper`() {
        val grid = autoTuner.createParameterGridForStrategy(StrategyType.SHITCOIN_SCALPER)
        
        val parameterNames = grid.getParameterNames()
        assertTrue(parameterNames.contains("profitTakePercent"))
        assertTrue(parameterNames.contains("stopLossPercent"))
        assertTrue(parameterNames.contains("maxHoldTimeMs"))
        assertTrue(parameterNames.contains("trailingStopPercent"))
    }
    
    @Test
    fun `should create parameter grid for RSI strategy`() {
        val grid = autoTuner.createParameterGridForStrategy(StrategyType.RSI_BASED)
        
        val parameterNames = grid.getParameterNames()
        assertTrue(parameterNames.contains("period"))
        assertTrue(parameterNames.contains("oversoldThreshold"))
        assertTrue(parameterNames.contains("overboughtThreshold"))
        assertTrue(parameterNames.contains("minHoldMs"))
    }
    
    @Test
    fun `should create parameter grid for momentum strategy`() {
        val grid = autoTuner.createParameterGridForStrategy(StrategyType.MOMENTUM)
        
        val parameterNames = grid.getParameterNames()
        assertTrue(parameterNames.contains("rocPeriod"))
        assertTrue(parameterNames.contains("buyThreshold"))
        assertTrue(parameterNames.contains("sellThreshold"))
    }
    
    @Test
    fun `should create parameter grid for breakout strategy`() {
        val grid = autoTuner.createParameterGridForStrategy(StrategyType.BREAKOUT)
        
        val parameterNames = grid.getParameterNames()
        assertTrue(parameterNames.contains("lookback"))
        assertTrue(parameterNames.contains("bufferPct"))
    }
    
    @Test
    fun `should create parameter grid for technical analysis combined`() {
        val grid = autoTuner.createParameterGridForStrategy(StrategyType.TECHNICAL_ANALYSIS_COMBINED)
        
        val parameterNames = grid.getParameterNames()
        assertTrue(parameterNames.contains("smaWeight"))
        assertTrue(parameterNames.contains("rsiWeight"))
        assertTrue(parameterNames.contains("breakoutWeight"))
        assertTrue(parameterNames.contains("decisionThreshold"))
    }
    
    @Test
    fun `should generate meaningful recommendations`() {
        val strategyResults = mapOf(
            StrategyType.SHITCOIN_SCALPER to BacktestResult(
                strategyName = "ScalperStrategy",
                parameters = emptyMap(),
                totalReturn = 100.0,
                totalReturnPercent = 0.1,
                sharpeRatio = 0.8, // Low Sharpe
                maxDrawdown = 0.2, // High drawdown
                winRate = 0.4, // Low win rate
                profitFactor = 1.1,
                totalTrades = 5, // Low trade count
                avgSlippage = 0.01,
                avgTimeInPosition = 120_000L, // Long hold time for scalper
                volatility = 0.3,
                calmarRatio = 0.5,
                valueAtRisk = 0.08,
                trades = emptyList(),
                equity = emptyList(),
                startDate = "2024-01-01",
                endDate = "2024-12-31",
                duration = "1h 0m 0s"
            )
        )
        
        val bestResult = strategyResults[StrategyType.SHITCOIN_SCALPER]!!
        val recommendations = autoTuner.generateRecommendations(strategyResults, bestResult)
        
        assertTrue(recommendations.any { it.contains("Sharpe ratio") })
        assertTrue(recommendations.any { it.contains("drawdown") })
        assertTrue(recommendations.any { it.contains("win rate") })
        assertTrue(recommendations.any { it.contains("trade frequency") })
        assertTrue(recommendations.any { it.contains("holding positions too long") })
    }
    
    @Test
    fun `should handle no successful optimization`() {
        val recommendations = autoTuner.generateRecommendations(emptyMap(), null)
        
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.first().contains("No successful optimization"))
    }
}