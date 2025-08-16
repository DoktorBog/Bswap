package com.bswap.server.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class EnhancedTradingConfigTest {
    
    private lateinit var config: EnhancedTradingConfig
    
    @BeforeEach
    fun setup() {
        config = EnhancedTradingConfig()
    }
    
    @Test
    fun `should have default values for all configurations`() {
        assertNotNull(config.riskManagement)
        assertNotNull(config.liquidityProtection)
        assertNotNull(config.rugDetection)
        assertNotNull(config.antiChop)
        assertNotNull(config.timeBasedExit)
        assertNotNull(config.scalper)
        assertNotNull(config.rsi)
        assertNotNull(config.slippage)
        assertNotNull(config.execution)
        assertNotNull(config.backtest)
        assertNotNull(config.optimization)
        assertNotNull(config.constraints)
        assertTrue(config.enableGracefulDegradation)
    }
    
    @Test
    fun `should create risk management config with sensible defaults`() {
        val riskConfig = config.riskManagement
        
        assertTrue(riskConfig.hardStopLossPercent > 0.0)
        assertTrue(riskConfig.emergencyStopLossPercent > 0.0)
        assertTrue(riskConfig.maxPositionSizePercent > 0.0)
        assertTrue(riskConfig.maxPositionSizePercent <= 100.0)
        assertTrue(riskConfig.maxConcurrentPositions > 0)
        assertTrue(riskConfig.positionSizingMethod.isNotEmpty())
    }
    
    @Test
    fun `should create liquidity protection config with sensible defaults`() {
        val liquidityConfig = config.liquidityProtection
        
        assertTrue(liquidityConfig.enablePreTradeValidation)
        assertTrue(liquidityConfig.maxPriceImpactPercent > 0.0)
        assertTrue(liquidityConfig.minVolumeUsd > 0.0)
        assertTrue(liquidityConfig.minLiquidityPoolSizeUsd > 0.0)
        assertTrue(liquidityConfig.liquidityCheckTimeoutMs > 0L)
    }
    
    @Test
    fun `should create rug detection config with sensible defaults`() {
        val rugConfig = config.rugDetection
        
        assertTrue(rugConfig.enableRugDetection)
        assertTrue(rugConfig.emergencyExitOnRug)
        assertTrue(rugConfig.priceDropThreshold > 0.0)
        assertTrue(rugConfig.volumeDropThreshold > 0.0)
        assertTrue(rugConfig.analysisWindowMs > 0L)
        assertTrue(rugConfig.confidence > 0.0 && rugConfig.confidence <= 1.0)
    }
    
    @Test
    fun `should create anti-chop config with sensible defaults`() {
        val antiChopConfig = config.antiChop
        
        assertTrue(antiChopConfig.enableAntiChop)
        assertTrue(antiChopConfig.minTrendStrength > 0.0)
        assertTrue(antiChopConfig.analysisWindowMs > 0L)
        assertTrue(antiChopConfig.maxChopDuration > 0L)
        assertTrue(antiChopConfig.volatilityThreshold > 0.0)
    }
    
    @Test
    fun `should create time-based exit config with sensible defaults`() {
        val timeExitConfig = config.timeBasedExit
        
        assertTrue(timeExitConfig.enableTimeBasedExit)
        assertTrue(timeExitConfig.maxHoldTimeUnprofitableMs > 0L)
        assertTrue(timeExitConfig.maxHoldTimeProfitableMs > 0L)
        assertTrue(timeExitConfig.flatTimeExitMs > 0L)
        assertTrue(timeExitConfig.flatThreshold > 0.0)
    }
    
    @Test
    fun `should create scalper config with sensible defaults`() {
        val scalperConfig = config.scalper
        
        assertTrue(scalperConfig.entryConfidenceThreshold > 0.0)
        assertTrue(scalperConfig.volatilityScalingFactor > 0.0)
        assertTrue(scalperConfig.enableVolumeConfirmation)
        assertTrue(scalperConfig.enableMomentumFiltering)
        assertTrue(scalperConfig.momentumPeriods > 0)
    }
    
    @Test
    fun `should create RSI config with sensible defaults`() {
        val rsiConfig = config.rsi
        
        assertTrue(rsiConfig.enableRSIDivergence)
        assertTrue(rsiConfig.adaptivePeriods)
        assertTrue(rsiConfig.minPeriod > 0)
        assertTrue(rsiConfig.maxPeriod > rsiConfig.minPeriod)
        assertTrue(rsiConfig.oversoldLevel > 0.0 && rsiConfig.oversoldLevel < 50.0)
        assertTrue(rsiConfig.overboughtLevel > 50.0 && rsiConfig.overboughtLevel < 100.0)
        assertTrue(rsiConfig.rsiExtremeThreshold > 0.0)
        assertTrue(rsiConfig.divergenceLookbackPeriods > 0)
    }
    
    @Test
    fun `should create slippage config with sensible defaults`() {
        val slippageConfig = config.slippage
        
        assertTrue(slippageConfig.targetSlippagePercent > 0.0)
        assertTrue(slippageConfig.maxSlippagePercent > slippageConfig.targetSlippagePercent)
        assertTrue(slippageConfig.liquiditySlippageMultiplier > 0.0)
        assertTrue(slippageConfig.volatilitySlippageMultiplier > 0.0)
        assertTrue(slippageConfig.enableDynamicSlippage)
    }
    
    @Test
    fun `should create execution config with sensible defaults`() {
        val executionConfig = config.execution
        
        assertTrue(executionConfig.rpcTimeoutMs > 0L)
        assertTrue(executionConfig.retryDelayMs > 0L)
        assertTrue(executionConfig.maxRetries > 0)
        assertTrue(executionConfig.enableIdempotentOperations)
        assertTrue(executionConfig.orderExpiryMs > 0L)
    }
    
    @Test
    fun `should create backtest config with sensible defaults`() {
        val backtestConfig = config.backtest
        
        assertTrue(backtestConfig.initialCapitalUsd > 0.0)
        assertTrue(backtestConfig.commissionPercent >= 0.0)
        assertTrue(backtestConfig.avgLatencyMs > 0.0)
        assertTrue(backtestConfig.latencyStdDevMs >= 0.0)
        assertTrue(backtestConfig.marketImpactFactor > 0.0)
        assertTrue(backtestConfig.partialFillProbability >= 0.0 && backtestConfig.partialFillProbability <= 1.0)
        assertNotNull(backtestConfig.startDate)
        assertNotNull(backtestConfig.endDate)
    }
    
    @Test
    fun `should create optimization config with sensible defaults`() {
        val optimizationConfig = config.optimization
        
        assertTrue(optimizationConfig.populationSize > 0)
        assertTrue(optimizationConfig.maxIterations > 0)
        assertTrue(optimizationConfig.convergenceThreshold > 0.0)
        assertTrue(optimizationConfig.maxStagnantGenerations > 0)
        assertTrue(optimizationConfig.elitismPercent > 0.0 && optimizationConfig.elitismPercent < 1.0)
        assertTrue(optimizationConfig.crossoverRate > 0.0 && optimizationConfig.crossoverRate <= 1.0)
        assertTrue(optimizationConfig.mutationRate > 0.0 && optimizationConfig.mutationRate <= 1.0)
        assertTrue(optimizationConfig.maxParallelJobs > 0)
        assertNotNull(optimizationConfig.objectiveFunction)
    }
    
    @Test
    fun `should create constraint config with sensible defaults`() {
        val constraintConfig = config.constraints
        
        assertTrue(constraintConfig.maxDrawdownPercent > 0.0)
        assertTrue(constraintConfig.minWinRatePercent > 0.0 && constraintConfig.minWinRatePercent <= 100.0)
        assertTrue(constraintConfig.minSharpeRatio >= 0.0)
        assertTrue(constraintConfig.maxVolatilityPercent > 0.0)
        assertTrue(constraintConfig.maxVaRPercent > 0.0)
        assertTrue(constraintConfig.minProfitFactor > 0.0)
        assertTrue(constraintConfig.constraintWeights.isNotEmpty())
        assertTrue(constraintConfig.constraintWeights.values.all { it > 0.0 })
    }
}

class RiskManagementConfigTest {
    
    @Test
    fun `should create risk management config with proper relationships`() {
        val config = RiskManagementConfig()
        
        // Emergency stop should be more lenient than hard stop
        assertTrue(config.emergencyStopLossPercent > config.hardStopLossPercent)
        
        // Position size should be reasonable
        assertTrue(config.maxPositionSizePercent <= 50.0) // No more than 50% in one position
        
        // Should have reasonable concurrent position limits
        assertTrue(config.maxConcurrentPositions >= 1)
        assertTrue(config.maxConcurrentPositions <= 20) // Reasonable upper bound
    }
}

class LiquidityProtectionConfigTest {
    
    @Test
    fun `should create liquidity protection with reasonable thresholds`() {
        val config = LiquidityProtectionConfig()
        
        // Price impact should be reasonable
        assertTrue(config.maxPriceImpactPercent <= 10.0) // No more than 10% price impact
        
        // Volume requirements should be meaningful
        assertTrue(config.minVolumeUsd >= 100.0) // At least $100 volume
        
        // Pool size should be substantial
        assertTrue(config.minLiquidityPoolSizeUsd >= 1000.0) // At least $1k pool
        
        // Timeout should be reasonable
        assertTrue(config.liquidityCheckTimeoutMs <= 30000L) // Max 30 seconds
    }
}

class SlippageConfigTest {
    
    @Test
    fun `should create slippage config with proper hierarchy`() {
        val config = SlippageConfig()
        
        // Max slippage should be higher than target
        assertTrue(config.maxSlippagePercent > config.targetSlippagePercent)
        
        // Multipliers should be reasonable
        assertTrue(config.liquiditySlippageMultiplier >= 1.0)
        assertTrue(config.volatilitySlippageMultiplier >= 0.1)
        assertTrue(config.volatilitySlippageMultiplier <= 10.0)
        
        // Target slippage should be reasonable
        assertTrue(config.targetSlippagePercent <= 5.0) // No more than 5% target
        assertTrue(config.maxSlippagePercent <= 20.0) // No more than 20% max
    }
}

class OptimizationConfigTest {
    
    @Test
    fun `should create optimization config with valid GA parameters`() {
        val config = OptimizationConfig()
        
        // Population size should be reasonable for genetic algorithm
        assertTrue(config.populationSize >= 10)
        assertTrue(config.populationSize <= 1000)
        
        // Genetic algorithm rates should be in valid range
        assertTrue(config.elitismPercent < 0.5) // Less than 50% elitism
        assertTrue(config.crossoverRate >= 0.5) // At least 50% crossover
        assertTrue(config.mutationRate <= 0.3) // No more than 30% mutation
        
        // Convergence parameters should be reasonable
        assertTrue(config.convergenceThreshold <= 0.01) // 1% convergence threshold
        assertTrue(config.maxStagnantGenerations <= config.maxIterations / 2)
    }
}

class ConstraintConfigTest {
    
    @Test
    fun `should create constraint config with realistic trading constraints`() {
        val config = ConstraintConfig()
        
        // Drawdown constraint should be reasonable
        assertTrue(config.maxDrawdownPercent <= 50.0) // No more than 50% drawdown
        assertTrue(config.maxDrawdownPercent >= 5.0) // At least 5% allowable drawdown
        
        // Win rate should be achievable
        assertTrue(config.minWinRatePercent <= 80.0) // No more than 80% win rate requirement
        assertTrue(config.minWinRatePercent >= 30.0) // At least 30% win rate
        
        // Sharpe ratio should be reasonable
        assertTrue(config.minSharpeRatio <= 3.0) // No more than 3.0 Sharpe requirement
        
        // Volatility constraint should be reasonable
        assertTrue(config.maxVolatilityPercent <= 100.0) // No more than 100% volatility
        
        // VaR should be reasonable
        assertTrue(config.maxVaRPercent <= 20.0) // No more than 20% VaR
        
        // Profit factor should be achievable
        assertTrue(config.minProfitFactor >= 1.0) // At least break-even
        assertTrue(config.minProfitFactor <= 3.0) // No more than 3.0 profit factor requirement
        
        // Constraint weights should sum to reasonable values
        val totalWeight = config.constraintWeights.values.sum()
        assertTrue(totalWeight > 0.0)
    }
}