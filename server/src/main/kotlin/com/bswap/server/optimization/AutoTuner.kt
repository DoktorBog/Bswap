package com.bswap.server.optimization

import com.bswap.server.backtest.*
import com.bswap.server.config.*
import com.bswap.server.stratagy.*
import com.bswap.server.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
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
 * Advanced AutoTuner for hyperparameter optimization across strategies
 * Uses genetic algorithm with constraint validation for optimal configuration discovery
 */

// =================================================================================================
// PARAMETER GRID SYSTEM
// =================================================================================================

data class Parameter(
    val name: String,
    val type: ParameterType,
    val minValue: Double,
    val maxValue: Double,
    val stepSize: Double? = null,
    val discreteValues: List<Any>? = null,
    val defaultValue: Any
)

enum class ParameterType {
    CONTINUOUS,    // Continuous values within range
    DISCRETE,      // Discrete numeric values
    CATEGORICAL,   // Categorical values from list
    BOOLEAN,       // Boolean values
    INTEGER        // Integer values within range
}

data class ParameterSet(
    val values: Map<String, Any>,
    val fitness: Double = 0.0,
    val constraints: Map<String, Double> = emptyMap(),
    val isValid: Boolean = true
) {
    fun copy(newValues: Map<String, Any>): ParameterSet {
        return ParameterSet(
            values = values + newValues,
            fitness = fitness,
            constraints = constraints,
            isValid = isValid
        )
    }
}

class ParameterGrid {
    companion object {
        private val logger = LoggerFactory.getLogger(ParameterGrid::class.java)
    }

    private val parameters = mutableMapOf<String, Parameter>()

    fun addParameter(parameter: Parameter) {
        parameters[parameter.name] = parameter
        logger.debug("Added parameter: ${parameter.name} (${parameter.type})")
    }

    fun generateRandomParameterSet(random: Random = Random.Default): ParameterSet {
        val values = mutableMapOf<String, Any>()

        parameters.forEach { (name, param) ->
            val value = when (param.type) {
                ParameterType.CONTINUOUS -> {
                    param.minValue + random.nextDouble() * (param.maxValue - param.minValue)
                }
                ParameterType.DISCRETE -> {
                    val steps = ((param.maxValue - param.minValue) / (param.stepSize ?: 1.0)).toInt()
                    param.minValue + random.nextInt(steps + 1) * (param.stepSize ?: 1.0)
                }
                ParameterType.INTEGER -> {
                    random.nextInt(param.minValue.toInt(), param.maxValue.toInt() + 1)
                }
                ParameterType.BOOLEAN -> {
                    random.nextBoolean()
                }
                ParameterType.CATEGORICAL -> {
                    param.discreteValues?.random(random) ?: param.defaultValue
                }
            }
            values[name] = value
        }

        return ParameterSet(values)
    }

    fun mutateParameterSet(parameterSet: ParameterSet, mutationRate: Double, random: Random = Random.Default): ParameterSet {
        val newValues = mutableMapOf<String, Any>()

        parameterSet.values.forEach { (name, value) ->
            val param = parameters[name] ?: return@forEach
            
            val shouldMutate = random.nextDouble() < mutationRate
            val newValue = if (shouldMutate) {
                when (param.type) {
                    ParameterType.CONTINUOUS -> {
                        val range = param.maxValue - param.minValue
                        val mutation = generateGaussian(random) * range * 0.1 // 10% of range std dev
                        (value as Double + mutation).coerceIn(param.minValue, param.maxValue)
                    }
                    ParameterType.DISCRETE -> {
                        val currentValue = value as Double
                        val step = param.stepSize ?: 1.0
                        val direction = if (random.nextBoolean()) step else -step
                        (currentValue + direction).coerceIn(param.minValue, param.maxValue)
                    }
                    ParameterType.INTEGER -> {
                        val currentValue = value as Int
                        val direction = if (random.nextBoolean()) 1 else -1
                        (currentValue + direction).coerceIn(param.minValue.toInt(), param.maxValue.toInt())
                    }
                    ParameterType.BOOLEAN -> {
                        !(value as Boolean)
                    }
                    ParameterType.CATEGORICAL -> {
                        param.discreteValues?.random(random) ?: value
                    }
                }
            } else value

            newValues[name] = newValue
        }

        return ParameterSet(newValues)
    }

    fun crossover(parent1: ParameterSet, parent2: ParameterSet, random: Random = Random.Default): ParameterSet {
        val childValues = mutableMapOf<String, Any>()

        parameters.keys.forEach { name ->
            val value1 = parent1.values[name]
            val value2 = parent2.values[name]
            
            if (value1 != null && value2 != null) {
                val param = parameters[name]!!
                
                val childValue = when (param.type) {
                    ParameterType.CONTINUOUS -> {
                        val v1 = value1 as Double
                        val v2 = value2 as Double
                        val alpha = random.nextDouble()
                        alpha * v1 + (1 - alpha) * v2
                    }
                    ParameterType.DISCRETE -> {
                        if (random.nextBoolean()) value1 else value2
                    }
                    ParameterType.INTEGER -> {
                        if (random.nextBoolean()) value1 else value2
                    }
                    ParameterType.BOOLEAN -> {
                        if (random.nextBoolean()) value1 else value2
                    }
                    ParameterType.CATEGORICAL -> {
                        if (random.nextBoolean()) value1 else value2
                    }
                }
                childValues[name] = childValue
            }
        }

        return ParameterSet(childValues)
    }

    fun getParameterNames(): Set<String> = parameters.keys
    
    fun getParameter(name: String): Parameter? = parameters[name]
}

// =================================================================================================
// CONSTRAINT VALIDATOR
// =================================================================================================

class ConstraintValidator(private val config: ConstraintConfig) {
    companion object {
        private val logger = LoggerFactory.getLogger(ConstraintValidator::class.java)
    }

    data class ConstraintResult(
        val isValid: Boolean,
        val violations: Map<String, Double>,
        val score: Double
    )

    fun validateConstraints(result: OfflineBacktester.BacktestResult): ConstraintResult {
        val violations = mutableMapOf<String, Double>()
        var totalScore = 1.0

        // Maximum drawdown constraint
        if (result.maxDrawdown > config.maxDrawdownPercent / 100.0) {
            val violation = result.maxDrawdown - config.maxDrawdownPercent / 100.0
            violations["maxDrawdown"] = violation
            totalScore *= (1.0 - violation * config.constraintWeights["drawdown"]!!)
        }

        // Minimum win rate constraint
        if (result.winRate < config.minWinRatePercent / 100.0) {
            val violation = config.minWinRatePercent / 100.0 - result.winRate
            violations["winRate"] = violation
            totalScore *= (1.0 - violation * config.constraintWeights["winRate"]!!)
        }

        // Minimum Sharpe ratio constraint
        if (result.sharpeRatio < config.minSharpeRatio) {
            val violation = config.minSharpeRatio - result.sharpeRatio
            violations["sharpeRatio"] = violation
            totalScore *= (1.0 - violation * 0.1 * config.constraintWeights["sharpe"]!!)
        }

        // Maximum volatility constraint
        if (result.volatility > config.maxVolatilityPercent / 100.0) {
            val violation = result.volatility - config.maxVolatilityPercent / 100.0
            violations["volatility"] = violation
            totalScore *= (1.0 - violation * config.constraintWeights["volatility"]!!)
        }

        // Maximum VaR constraint
        if (result.valueAtRisk > config.maxVaRPercent / 100.0) {
            val violation = result.valueAtRisk - config.maxVaRPercent / 100.0
            violations["valueAtRisk"] = violation
            totalScore *= (1.0 - violation * 0.5)
        }

        // Minimum profit factor constraint
        if (result.profitFactor < config.minProfitFactor) {
            val violation = config.minProfitFactor - result.profitFactor
            violations["profitFactor"] = violation
            totalScore *= (1.0 - violation * 0.1)
        }

        val isValid = if (config.enableStrictConstraints) {
            violations.isEmpty()
        } else {
            totalScore > 0.5 // Soft constraint: at least 50% of original score
        }

        totalScore = maxOf(0.0, totalScore)

        return ConstraintResult(isValid, violations, totalScore)
    }

    fun calculateFitness(result: OfflineBacktester.BacktestResult, objectiveFunction: ObjectiveFunction): Double {
        val constraintResult = validateConstraints(result)
        
        if (!constraintResult.isValid && config.enableStrictConstraints) {
            return 0.0 // Invalid solutions get zero fitness
        }

        val baseFitness = when (objectiveFunction) {
            ObjectiveFunction.TOTAL_RETURN -> result.totalReturnPercent
            ObjectiveFunction.SHARPE_RATIO -> result.sharpeRatio
            ObjectiveFunction.CALMAR_RATIO -> result.calmarRatio
            ObjectiveFunction.WIN_RATE -> result.winRate
            ObjectiveFunction.PROFIT_FACTOR -> ln(result.profitFactor + 1.0) // Log to prevent extreme values
            ObjectiveFunction.MULTI_OBJECTIVE -> {
                // Weighted combination of multiple objectives
                0.3 * result.sharpeRatio +
                0.2 * result.totalReturnPercent * 10.0 + // Scale return to similar magnitude
                0.2 * result.winRate * 5.0 +
                0.15 * ln(result.profitFactor + 1.0) * 2.0 +
                0.15 * result.calmarRatio
            }
        }

        // Apply constraint penalty
        return baseFitness * constraintResult.score
    }
}

// =================================================================================================
// GENETIC ALGORITHM OPTIMIZER
// =================================================================================================

class GeneticOptimizer(
    private val config: OptimizationConfig,
    private val constraintConfig: ConstraintConfig,
    private val backtester: OfflineBacktester,
    private val tokens: List<BacktestToken>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(GeneticOptimizer::class.java)
    }

    private val random = Random(42) // Deterministic for reproducible results
    private val constraintValidator = ConstraintValidator(constraintConfig)

    data class OptimizationResult(
        val bestParameterSet: ParameterSet,
        val bestResult: OfflineBacktester.BacktestResult,
        val allResults: List<Pair<ParameterSet, OfflineBacktester.BacktestResult>>,
        val generations: Int,
        val convergenceHistory: List<Double>,
        val executionTimeMs: Long
    )

    suspend fun optimize(
        strategyFactory: (ParameterSet) -> TradingStrategy,
        parameterGrid: ParameterGrid
    ): OptimizationResult {
        val startTime = System.currentTimeMillis()
        logger.info("🚀 OPTIMIZATION START: Population=${config.populationSize}, Max Iterations=${config.maxIterations}")

        var population = initializePopulation(parameterGrid)
        val convergenceHistory = mutableListOf<Double>()
        val allResults = mutableListOf<Pair<ParameterSet, OfflineBacktester.BacktestResult>>()
        var bestFitness = 0.0
        var stagnantGenerations = 0

        for (generation in 0 until config.maxIterations) {
            logger.info("🧬 Generation $generation: Evaluating ${population.size} individuals")

            // Evaluate population
            val evaluatedPopulation = evaluatePopulation(population, strategyFactory, allResults)
            
            // Sort by fitness
            val sortedPopulation = evaluatedPopulation.sortedByDescending { it.fitness }
            val currentBestFitness = sortedPopulation.first().fitness
            
            convergenceHistory.add(currentBestFitness)

            // Check for convergence
            if (abs(currentBestFitness - bestFitness) < config.convergenceThreshold) {
                stagnantGenerations++
            } else {
                stagnantGenerations = 0
                bestFitness = currentBestFitness
            }

            logger.info("📊 Generation $generation: Best fitness = ${"%.4f".format(currentBestFitness)}")

            if (stagnantGenerations >= config.maxStagnantGenerations) {
                logger.info("🏁 Optimization converged after $generation generations")
                break
            }

            // Selection, crossover, and mutation
            population = evolvePopulation(sortedPopulation, parameterGrid)
        }

        val endTime = System.currentTimeMillis()
        val executionTime = endTime - startTime

        // Find best result
        val bestParameterSet = allResults.maxByOrNull { 
            constraintValidator.calculateFitness(it.second, config.objectiveFunction) 
        }!!

        logger.info("✅ OPTIMIZATION COMPLETE: Best fitness = ${"%.4f".format(bestParameterSet.first.fitness)}, Time = ${executionTime}ms")

        return OptimizationResult(
            bestParameterSet = bestParameterSet.first,
            bestResult = bestParameterSet.second,
            allResults = allResults,
            generations = convergenceHistory.size,
            convergenceHistory = convergenceHistory,
            executionTimeMs = executionTime
        )
    }

    private fun initializePopulation(parameterGrid: ParameterGrid): List<ParameterSet> {
        return (0 until config.populationSize).map {
            parameterGrid.generateRandomParameterSet(random)
        }
    }

    private suspend fun evaluatePopulation(
        population: List<ParameterSet>,
        strategyFactory: (ParameterSet) -> TradingStrategy,
        allResults: MutableList<Pair<ParameterSet, OfflineBacktester.BacktestResult>>
    ): List<ParameterSet> {
        
        return if (config.enableParallelOptimization) {
            evaluatePopulationParallel(population, strategyFactory, allResults)
        } else {
            evaluatePopulationSequential(population, strategyFactory, allResults)
        }
    }

    private suspend fun evaluatePopulationSequential(
        population: List<ParameterSet>,
        strategyFactory: (ParameterSet) -> TradingStrategy,
        allResults: MutableList<Pair<ParameterSet, OfflineBacktester.BacktestResult>>
    ): List<ParameterSet> {
        return population.map { parameterSet ->
            val strategy = strategyFactory(parameterSet)
            val result = backtester.runBacktest(strategy, parameterSet.values, tokens)
            val fitness = constraintValidator.calculateFitness(result, config.objectiveFunction)
            
            allResults.add(Pair(parameterSet, result))
            parameterSet.copy(newValues = emptyMap()).copy(newValues = parameterSet.values).apply {
                // Update fitness in a new instance
            }.let { 
                ParameterSet(
                    values = parameterSet.values,
                    fitness = fitness,
                    constraints = constraintValidator.validateConstraints(result).violations,
                    isValid = constraintValidator.validateConstraints(result).isValid
                )
            }
        }
    }

    private suspend fun evaluatePopulationParallel(
        population: List<ParameterSet>,
        strategyFactory: (ParameterSet) -> TradingStrategy,
        allResults: MutableList<Pair<ParameterSet, OfflineBacktester.BacktestResult>>
    ): List<ParameterSet> {
        return coroutineScope {
            val chunks = population.chunked(maxOf(1, population.size / config.maxParallelJobs))
            
            chunks.map { chunk ->
                async(Dispatchers.Default) {
                    chunk.map { parameterSet ->
                        val strategy = strategyFactory(parameterSet)
                        val result = backtester.runBacktest(strategy, parameterSet.values, tokens)
                        val fitness = constraintValidator.calculateFitness(result, config.objectiveFunction)
                        
                        synchronized(allResults) {
                            allResults.add(Pair(parameterSet, result))
                        }
                        
                        ParameterSet(
                            values = parameterSet.values,
                            fitness = fitness,
                            constraints = constraintValidator.validateConstraints(result).violations,
                            isValid = constraintValidator.validateConstraints(result).isValid
                        )
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private fun evolvePopulation(
        sortedPopulation: List<ParameterSet>,
        parameterGrid: ParameterGrid
    ): List<ParameterSet> {
        val newPopulation = mutableListOf<ParameterSet>()
        
        // Elitism: keep best individuals
        val eliteCount = (config.populationSize * config.elitismPercent).toInt()
        newPopulation.addAll(sortedPopulation.take(eliteCount))
        
        // Generate remaining population through crossover and mutation
        while (newPopulation.size < config.populationSize) {
            val parent1 = tournamentSelection(sortedPopulation)
            val parent2 = tournamentSelection(sortedPopulation)
            
            val child = if (random.nextDouble() < config.crossoverRate) {
                parameterGrid.crossover(parent1, parent2, random)
            } else {
                parent1
            }
            
            val mutatedChild = parameterGrid.mutateParameterSet(child, config.mutationRate, random)
            newPopulation.add(mutatedChild)
        }
        
        return newPopulation
    }

    private fun tournamentSelection(population: List<ParameterSet>, tournamentSize: Int = 3): ParameterSet {
        val tournament = population.shuffled(random).take(tournamentSize)
        return tournament.maxByOrNull { it.fitness }!!
    }
}

// =================================================================================================
// AUTO TUNER MAIN CLASS
// =================================================================================================

class AutoTuner(
    private val enhancedConfig: EnhancedTradingConfig = EnhancedTradingConfig()
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AutoTuner::class.java)
    }

    private val backtester = OfflineBacktester(enhancedConfig.backtest, enhancedConfig)
    private val optimizer = GeneticOptimizer(
        enhancedConfig.optimization,
        enhancedConfig.constraints,
        backtester,
        emptyList() // Will be set during optimization
    )

    data class TuningResult(
        val bestStrategy: com.bswap.server.StrategyType,
        val bestParameters: Map<String, Any>,
        val bestResult: OfflineBacktester.BacktestResult,
        val allStrategyResults: Map<com.bswap.server.StrategyType, OfflineBacktester.BacktestResult>,
        val optimizationDetails: GeneticOptimizer.OptimizationResult,
        val recommendations: List<String>
    )

    suspend fun tuneAllStrategies(tokens: List<BacktestToken>): TuningResult {
        logger.info("🎯 AUTO TUNER: Starting comprehensive strategy optimization")
        
        val strategyResults = mutableMapOf<com.bswap.server.StrategyType, OfflineBacktester.BacktestResult>()
        var bestOverallResult: OfflineBacktester.BacktestResult? = null
        var bestStrategy: com.bswap.server.StrategyType? = null
        var bestParameters: Map<String, Any> = emptyMap()
        var bestOptimizationDetails: GeneticOptimizer.OptimizationResult? = null

        // Define strategies to test
        val strategiesToTest = listOf(
            com.bswap.server.StrategyType.SHITCOIN_SCALPER,
            com.bswap.server.StrategyType.RSI_BASED,
            com.bswap.server.StrategyType.MOMENTUM,
            com.bswap.server.StrategyType.BREAKOUT,
            com.bswap.server.StrategyType.TECHNICAL_ANALYSIS_COMBINED
        )

        for (strategyType in strategiesToTest) {
            logger.info("🔧 Optimizing strategy: $strategyType")
            
            try {
                val parameterGrid = createParameterGridForStrategy(strategyType)
                val strategyFactory: (ParameterSet) -> TradingStrategy = { params ->
                    createStrategyFromParameters(strategyType, params)
                }

                // Create optimizer for this strategy
                val strategyOptimizer = GeneticOptimizer(
                    enhancedConfig.optimization,
                    enhancedConfig.constraints,
                    backtester,
                    tokens
                )

                val optimizationResult = strategyOptimizer.optimize(strategyFactory, parameterGrid)
                strategyResults[strategyType] = optimizationResult.bestResult

                // Check if this is the best overall result
                val fitness = ConstraintValidator(enhancedConfig.constraints)
                    .calculateFitness(optimizationResult.bestResult, enhancedConfig.optimization.objectiveFunction)

                if (bestOverallResult == null || fitness > 
                    ConstraintValidator(enhancedConfig.constraints)
                        .calculateFitness(bestOverallResult, enhancedConfig.optimization.objectiveFunction)) {
                    
                    bestOverallResult = optimizationResult.bestResult
                    bestStrategy = strategyType
                    bestParameters = optimizationResult.bestParameterSet.values
                    bestOptimizationDetails = optimizationResult
                }

                logger.info("✅ Strategy $strategyType optimized: Return=${"%.2f".format(optimizationResult.bestResult.totalReturnPercent * 100)}%, Sharpe=${"%.2f".format(optimizationResult.bestResult.sharpeRatio)}")

            } catch (e: Exception) {
                logger.error("❌ Failed to optimize strategy $strategyType: ${e.message}", e)
            }
        }

        // Generate recommendations
        val recommendations = generateRecommendations(strategyResults, bestOverallResult)

        logger.info("🏆 BEST STRATEGY: $bestStrategy with return=${"%.2f".format(bestOverallResult?.totalReturnPercent?.times(100) ?: 0.0)}%")

        return TuningResult(
            bestStrategy = bestStrategy ?: com.bswap.server.StrategyType.SHITCOIN_SCALPER,
            bestParameters = bestParameters,
            bestResult = bestOverallResult ?: OfflineBacktester.BacktestResult(
                strategyName = "None",
                parameters = emptyMap(),
                totalReturn = 0.0,
                totalReturnPercent = 0.0,
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
                trades = emptyList(),
                equity = emptyList(),
                startDate = "",
                endDate = "",
                duration = ""
            ),
            allStrategyResults = strategyResults,
            optimizationDetails = bestOptimizationDetails ?: GeneticOptimizer.OptimizationResult(
                bestParameterSet = ParameterSet(emptyMap()),
                bestResult = bestOverallResult ?: throw IllegalStateException("No results"),
                allResults = emptyList(),
                generations = 0,
                convergenceHistory = emptyList(),
                executionTimeMs = 0L
            ),
            recommendations = recommendations
        )
    }

    private fun createParameterGridForStrategy(strategyType: com.bswap.server.StrategyType): ParameterGrid {
        val grid = ParameterGrid()

        when (strategyType) {
            com.bswap.server.StrategyType.SHITCOIN_SCALPER -> {
                grid.addParameter(Parameter("profitTakePercent", ParameterType.CONTINUOUS, 0.005, 0.05, defaultValue = 0.02))
                grid.addParameter(Parameter("stopLossPercent", ParameterType.CONTINUOUS, 0.02, 0.15, defaultValue = 0.08))
                grid.addParameter(Parameter("maxHoldTimeMs", ParameterType.INTEGER, 10_000.0, 120_000.0, defaultValue = 45_000))
                grid.addParameter(Parameter("trailingStopPercent", ParameterType.CONTINUOUS, 0.01, 0.08, defaultValue = 0.03))
                grid.addParameter(Parameter("minProfitBeforeTrailing", ParameterType.CONTINUOUS, 0.002, 0.02, defaultValue = 0.005))
            }
            com.bswap.server.StrategyType.RSI_BASED -> {
                grid.addParameter(Parameter("period", ParameterType.INTEGER, 8.0, 25.0, defaultValue = 14))
                grid.addParameter(Parameter("oversoldThreshold", ParameterType.CONTINUOUS, 20.0, 35.0, defaultValue = 30.0))
                grid.addParameter(Parameter("overboughtThreshold", ParameterType.CONTINUOUS, 65.0, 80.0, defaultValue = 70.0))
                grid.addParameter(Parameter("minHoldMs", ParameterType.INTEGER, 1_000.0, 10_000.0, defaultValue = 3_000))
            }
            com.bswap.server.StrategyType.MOMENTUM -> {
                grid.addParameter(Parameter("rocPeriod", ParameterType.INTEGER, 3.0, 15.0, defaultValue = 6))
                grid.addParameter(Parameter("buyThreshold", ParameterType.CONTINUOUS, 0.005, 0.03, defaultValue = 0.01))
                grid.addParameter(Parameter("sellThreshold", ParameterType.CONTINUOUS, 0.005, 0.03, defaultValue = 0.01))
                grid.addParameter(Parameter("minHoldMs", ParameterType.INTEGER, 30_000.0, 120_000.0, defaultValue = 60_000))
            }
            com.bswap.server.StrategyType.BREAKOUT -> {
                grid.addParameter(Parameter("lookback", ParameterType.INTEGER, 10.0, 30.0, defaultValue = 20))
                grid.addParameter(Parameter("bufferPct", ParameterType.CONTINUOUS, 0.001, 0.01, defaultValue = 0.002))
                grid.addParameter(Parameter("minHoldMs", ParameterType.INTEGER, 30_000.0, 120_000.0, defaultValue = 60_000))
            }
            com.bswap.server.StrategyType.TECHNICAL_ANALYSIS_COMBINED -> {
                grid.addParameter(Parameter("smaWeight", ParameterType.CONTINUOUS, 0.1, 1.0, defaultValue = 0.5))
                grid.addParameter(Parameter("rsiWeight", ParameterType.CONTINUOUS, 0.1, 1.0, defaultValue = 0.35))
                grid.addParameter(Parameter("breakoutWeight", ParameterType.CONTINUOUS, 0.1, 1.0, defaultValue = 0.4))
                grid.addParameter(Parameter("decisionThreshold", ParameterType.CONTINUOUS, 0.3, 1.0, defaultValue = 0.6))
                grid.addParameter(Parameter("takeProfitPct", ParameterType.CONTINUOUS, 0.1, 0.5, defaultValue = 0.25))
                grid.addParameter(Parameter("stopLossPct", ParameterType.CONTINUOUS, 0.05, 0.3, defaultValue = 0.15))
            }
            else -> {
                // Default parameters for other strategies
                grid.addParameter(Parameter("minHoldMs", ParameterType.INTEGER, 30_000.0, 120_000.0, defaultValue = 60_000))
            }
        }

        return grid
    }

    private fun createStrategyFromParameters(strategyType: com.bswap.server.StrategyType, parameters: ParameterSet): TradingStrategy {
        // Create strategy settings with optimized parameters
        val settings = TradingStrategySettings(type = strategyType)
        
        // This is a simplified version - in practice, you'd need to properly apply parameters
        // to the specific strategy configurations
        return TradingStrategyFactory.create(settings)
    }

    private fun generateRecommendations(
        strategyResults: Map<com.bswap.server.StrategyType, OfflineBacktester.BacktestResult>,
        bestResult: OfflineBacktester.BacktestResult?
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (bestResult == null) {
            recommendations.add("No successful optimization found. Consider relaxing constraints or improving data quality.")
            return recommendations
        }

        // Performance recommendations
        if (bestResult.sharpeRatio > 2.0) {
            recommendations.add("Excellent risk-adjusted returns achieved. Consider increasing position sizes cautiously.")
        } else if (bestResult.sharpeRatio < 1.0) {
            recommendations.add("Low Sharpe ratio detected. Focus on improving win rate or reducing volatility.")
        }

        // Drawdown recommendations
        if (bestResult.maxDrawdown > 0.15) {
            recommendations.add("High maximum drawdown detected. Consider implementing tighter risk controls.")
        }

        // Win rate recommendations
        if (bestResult.winRate < 0.5) {
            recommendations.add("Win rate below 50%. Consider improving entry signals or exit timing.")
        }

        // Trading frequency recommendations
        if (bestResult.totalTrades < 10) {
            recommendations.add("Low trade frequency. Consider relaxing entry criteria or using shorter timeframes.")
        } else if (bestResult.totalTrades > 1000) {
            recommendations.add("Very high trade frequency. Monitor for overtrading and transaction costs.")
        }

        // Strategy-specific recommendations
        strategyResults.forEach { (strategy, result) ->
            when (strategy) {
                com.bswap.server.StrategyType.SHITCOIN_SCALPER -> {
                    if (result.avgTimeInPosition > 60_000L) {
                        recommendations.add("Scalper holding positions too long. Consider reducing max hold time.")
                    }
                }
                com.bswap.server.StrategyType.RSI_BASED -> {
                    if (result.winRate > 0.7) {
                        recommendations.add("RSI strategy showing strong performance. Consider increasing allocation.")
                    }
                }
                else -> {} // No specific recommendations for other strategies
            }
        }

        return recommendations
    }
}