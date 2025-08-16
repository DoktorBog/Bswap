package com.bswap.server.config

import java.math.BigDecimal

/**
 * Enhanced trading configuration with all tunables externalized
 * No magic numbers - everything is configurable for low-liquidity token optimization
 */

// =================================================================================================
// LIQUIDITY & RISK MANAGEMENT CONFIGS
// =================================================================================================

data class LiquidityProtectionConfig(
    val minPoolReserveUsd: Double = 1_000.0,               // Minimum pool reserve in USD
    val maxPriceImpactPercent: Double = 5.0,               // Maximum acceptable price impact %
    val minVolumeUsd24h: Double = 10_000.0,                // Minimum 24h volume in USD
    val maxSlippagePercent: Double = 3.0,                  // Maximum acceptable slippage %
    val enableJupiterSignals: Boolean = true,              // Use Jupiter API for liquidity data
    val liquidityCheckTimeoutMs: Long = 2_000L,            // Timeout for liquidity checks
    val priceImpactCacheTimeMs: Long = 30_000L,            // Cache price impact data
    val enablePreTradeValidation: Boolean = true,          // Validate before each trade
    val emergencyLiquidityThreshold: Double = 100.0        // Emergency exit threshold USD
)

data class RiskManagementConfig(
    val hardStopLossPercent: Double = 15.0,                // Hard stop loss (never override)
    val emergencyStopLossPercent: Double = 25.0,           // Emergency stop for disasters
    val trailingStopPercent: Double = 3.0,                 // Trailing stop from peak
    val breakevenBufferPercent: Double = 0.5,              // Buffer before breakeven
    val maxDrawdownPercent: Double = 10.0,                 // Maximum portfolio drawdown
    val maxConcurrentPositions: Int = 10,                  // Maximum open positions
    val maxPositionSizePercent: Double = 10.0,             // Max % of portfolio per position
    val forceStopAfterDrawdown: Boolean = true,            // Stop trading after max DD
    val enableDynamicPositionSizing: Boolean = true,       // Adjust size based on volatility
    val volatilityLookbackPeriods: Int = 20,               // Periods for volatility calculation
    val riskScoreThreshold: Double = 0.8                   // Maximum risk score (0-1)
)

data class RugDetectionConfig(
    val tickDropThresholdPercent: Double = 8.0,            // Price drop per tick threshold
    val rugDetectionWindow: Int = 5,                       // Ticks to analyze for rug
    val volumeDropThresholdPercent: Double = 50.0,         // Volume drop indicating rug
    val priceVelocityThreshold: Double = 20.0,             // Price velocity threshold %/sec
    val enableLiquidityRugDetection: Boolean = true,       // Monitor liquidity removal
    val liquidityDropThresholdPercent: Double = 30.0,      // Liquidity removal threshold
    val rugConfidenceThreshold: Double = 0.7,              // Confidence level for rug detection
    val emergencyExitOnRug: Boolean = true,                // Immediate exit on rug detection
    val rugCheckIntervalMs: Long = 500L,                   // How often to check for rugs
    val minTicksForRugDetection: Int = 3                   // Minimum ticks before rug detection
)

data class AntiChopConfig(
    val choppyMarketThreshold: Double = 1.0,               // Range threshold for choppy market %
    val choppyDetectionPeriods: Int = 10,                  // Periods to analyze for choppiness
    val antiChopMode: AntiChopMode = AntiChopMode.REDUCE_SIZE, // How to handle choppy markets
    val choppyMarketPauseDurationMs: Long = 60_000L,       // Pause duration in choppy markets
    val enableChopDetection: Boolean = true,               // Enable anti-chop filter
    val choppyVolatilityMultiplier: Double = 1.5,          // Volatility multiplier for chop detection
    val maxConsecutiveChopTrades: Int = 3,                 // Max trades in choppy conditions
    val chopRecoveryWaitMs: Long = 30_000L                 // Wait time after chop detection
)

enum class AntiChopMode {
    PAUSE_TRADING,      // Stop trading completely
    REDUCE_SIZE,        // Reduce position sizes
    INCREASE_STOPS,     // Tighten stop losses
    FILTER_SIGNALS      // Only take highest confidence signals
}

data class TimeBasedExitConfig(
    val timeToFlatMs: Long = 15_000L,                      // Time before flat exit
    val flatRangeThresholdPercent: Double = 1.0,          // Range considered "flat"
    val maxHoldTimeMs: Long = 45_000L,                     // Maximum position hold time
    val profitTargetTimeReductionPercent: Double = 0.8,    // Reduce time target when profitable
    val lossTimeExtensionPercent: Double = 1.2,           // Extend time when losing
    val enableTimeBasedExit: Boolean = true,              // Enable time-based exits
    val timeBasedExitMode: TimeExitMode = TimeExitMode.CONDITIONAL, // How to apply time exits
    val quickExitTimeMs: Long = 5_000L,                   // Quick exit time for bad positions
    val extendedHoldTimeMs: Long = 90_000L                // Extended hold for strong positions
)

enum class TimeExitMode {
    HARD_LIMIT,         // Always exit at time limit
    CONDITIONAL,        // Exit based on P&L and trend
    PROFIT_ONLY,        // Only exit profitable positions on time
    LOSS_ONLY           // Only exit losing positions on time
}

// =================================================================================================
// STRATEGY-SPECIFIC CONFIGS
// =================================================================================================

data class EnhancedScalperConfig(
    val quickProfitTargetPercent: Double = 0.8,           // Quick profit target %
    val microProfitTargetPercent: Double = 0.3,           // Micro profit target %
    val volatilityScalingFactor: Double = 1.5,            // Scale targets with volatility
    val entryConfidenceThreshold: Double = 0.7,           // Minimum confidence for entry
    val exitConfidenceThreshold: Double = 0.6,            // Minimum confidence for exit
    val enableMomentumFiltering: Boolean = true,          // Filter entries by momentum
    val momentumPeriods: Int = 5,                         // Periods for momentum calculation
    val enableVolumeConfirmation: Boolean = true,         // Require volume confirmation
    val volumeMultiplierThreshold: Double = 2.0,          // Volume multiplier for confirmation
    val enableNewsImpactDetection: Boolean = false,       // Detect news impact (placeholder)
    val maxSlippageMultiplier: Double = 2.0               // Multiplier for max slippage in fast markets
)

data class EnhancedRSIConfig(
    val adaptivePeriods: Boolean = true,                  // Use adaptive RSI periods
    val minPeriod: Int = 8,                               // Minimum RSI period
    val maxPeriod: Int = 21,                              // Maximum RSI period
    val oversoldLevel: Double = 25.0,                     // RSI oversold level
    val overboughtLevel: Double = 75.0,                   // RSI overbought level
    val enableRSIDivergence: Boolean = true,              // Enable divergence detection
    val divergenceLookbackPeriods: Int = 10,              // Periods for divergence analysis
    val rsiSmoothingFactor: Double = 0.1,                 // Smoothing factor for RSI
    val enableMultiTimeframe: Boolean = false,            // Multi-timeframe RSI analysis
    val divergenceStrengthThreshold: Double = 0.6,        // Minimum divergence strength
    val rsiExtremeThreshold: Double = 15.0                // Extreme RSI levels (>85 or <15)
)

// =================================================================================================
// EXECUTION & LATENCY CONFIGS
// =================================================================================================

data class ExecutionConfig(
    val maxOrderLatencyMs: Long = 2_000L,                 // Maximum acceptable order latency
    val rpcTimeoutMs: Long = 5_000L,                      // RPC call timeout
    val jitoTimeoutMs: Long = 10_000L,                    // Jito bundle timeout
    val retryAttempts: Int = 3,                           // Number of retry attempts
    val retryDelayMs: Long = 500L,                        // Delay between retries
    val enableParallelExecution: Boolean = true,          // Execute orders in parallel
    val maxParallelOrders: Int = 5,                       // Maximum parallel orders
    val orderQueueSize: Int = 100,                        // Maximum order queue size
    val enableLatencyTracking: Boolean = true,            // Track execution latency
    val latencyWarningThresholdMs: Long = 1_000L,         // Warning threshold for latency
    val enableOrderValidation: Boolean = true,            // Validate orders before execution
    val enableExecutionMetrics: Boolean = true            // Collect execution metrics
)

data class SlippageConfig(
    val targetSlippagePercent: Double = 1.0,              // Target slippage %
    val maxSlippagePercent: Double = 3.0,                 // Maximum acceptable slippage %
    val slippageTolerancePercent: Double = 0.5,           // Additional tolerance %
    val enableDynamicSlippage: Boolean = true,            // Adjust slippage based on conditions
    val volatilitySlippageMultiplier: Double = 1.5,       // Slippage multiplier for volatility
    val liquiditySlippageMultiplier: Double = 2.0,        // Slippage multiplier for low liquidity
    val minSlippagePercent: Double = 0.1,                 // Minimum slippage %
    val slippageEstimationMethod: SlippageMethod = SlippageMethod.JUPITER_ESTIMATED,
    val enableSlippageTracking: Boolean = true,           // Track actual vs expected slippage
    val slippageAlertThresholdPercent: Double = 5.0       // Alert when slippage exceeds threshold
)

enum class SlippageMethod {
    FIXED,              // Fixed slippage percentage
    DYNAMIC,            // Dynamic based on market conditions
    JUPITER_ESTIMATED,  // Use Jupiter's slippage estimation
    HISTORICAL_AVERAGE  // Based on historical execution data
}

// =================================================================================================
// BACKTESTING & OPTIMIZATION CONFIGS
// =================================================================================================

data class BacktestConfig(
    val startDate: String = "2024-01-01",                 // Backtest start date (YYYY-MM-DD)
    val endDate: String = "2024-12-31",                   // Backtest end date
    val initialCapitalUsd: Double = 1000.0,               // Initial capital for backtest
    val commissionPercent: Double = 0.1,                  // Commission per trade %
    val enableRealisticLatency: Boolean = true,           // Simulate realistic latency
    val avgLatencyMs: Long = 200L,                        // Average execution latency
    val latencyStdDevMs: Long = 100L,                     // Latency standard deviation
    val enablePartialFills: Boolean = true,               // Simulate partial fills
    val partialFillProbability: Double = 0.1,             // Probability of partial fill
    val enableSlippageSimulation: Boolean = true,         // Simulate slippage
    val marketImpactFactor: Double = 0.01,                // Market impact factor
    val tickDataResolutionMs: Long = 1_000L,              // Tick data resolution
    val enableOrderBookSimulation: Boolean = false        // Simulate order book dynamics
)

data class OptimizationConfig(
    val maxIterations: Int = 1000,                        // Maximum optimization iterations
    val populationSize: Int = 50,                         // Genetic algorithm population size
    val mutationRate: Double = 0.1,                       // Mutation rate for GA
    val crossoverRate: Double = 0.8,                      // Crossover rate for GA
    val elitismPercent: Double = 0.1,                     // Elite solutions to preserve
    val convergenceThreshold: Double = 0.001,             // Convergence threshold
    val maxStagnantGenerations: Int = 20,                 // Max generations without improvement
    val enableParallelOptimization: Boolean = true,       // Run optimization in parallel
    val maxParallelJobs: Int = 4,                         // Maximum parallel optimization jobs
    val objectiveFunction: ObjectiveFunction = ObjectiveFunction.SHARPE_RATIO,
    val constraintTolerance: Double = 0.05,               // Tolerance for constraint violations
    val enableProgressLogging: Boolean = true             // Log optimization progress
)

enum class ObjectiveFunction {
    TOTAL_RETURN,       // Maximize total return
    SHARPE_RATIO,       // Maximize Sharpe ratio
    CALMAR_RATIO,       // Maximize Calmar ratio (return/max drawdown)
    WIN_RATE,           // Maximize win rate
    PROFIT_FACTOR,      // Maximize profit factor
    MULTI_OBJECTIVE     // Multi-objective optimization
}

data class ConstraintConfig(
    val maxDrawdownPercent: Double = 15.0,                // Maximum acceptable drawdown %
    val minWinRatePercent: Double = 55.0,                 // Minimum win rate %
    val minSharpeRatio: Double = 1.5,                     // Minimum Sharpe ratio
    val maxVaRPercent: Double = 5.0,                      // Maximum Value at Risk %
    val minProfitFactor: Double = 1.3,                    // Minimum profit factor
    val maxConsecutiveLosses: Int = 5,                    // Maximum consecutive losses
    val minMonthlyReturn: Double = 2.0,                   // Minimum monthly return %
    val maxVolatilityPercent: Double = 25.0,              // Maximum volatility %
    val enableStrictConstraints: Boolean = true,          // Enforce constraints strictly
    val constraintWeights: Map<String, Double> = mapOf(   // Weights for soft constraints
        "drawdown" to 0.3,
        "winRate" to 0.2,
        "sharpe" to 0.3,
        "volatility" to 0.2
    )
)

// =================================================================================================
// MASTER CONFIGURATION
// =================================================================================================

data class EnhancedTradingConfig(
    val liquidityProtection: LiquidityProtectionConfig = LiquidityProtectionConfig(),
    val riskManagement: RiskManagementConfig = RiskManagementConfig(),
    val rugDetection: RugDetectionConfig = RugDetectionConfig(),
    val antiChop: AntiChopConfig = AntiChopConfig(),
    val timeBasedExit: TimeBasedExitConfig = TimeBasedExitConfig(),
    val scalper: EnhancedScalperConfig = EnhancedScalperConfig(),
    val rsi: EnhancedRSIConfig = EnhancedRSIConfig(),
    val execution: ExecutionConfig = ExecutionConfig(),
    val slippage: SlippageConfig = SlippageConfig(),
    val backtest: BacktestConfig = BacktestConfig(),
    val optimization: OptimizationConfig = OptimizationConfig(),
    val constraints: ConstraintConfig = ConstraintConfig(),
    
    // Feature flags
    val enableAdvancedFeatures: Boolean = true,           // Enable all advanced features
    val enableExperimentalFeatures: Boolean = false,      // Enable experimental features
    val enableDebugMode: Boolean = false,                 // Enable detailed debug logging
    val enablePerformanceMonitoring: Boolean = true,      // Monitor performance metrics
    val enableRealTimeOptimization: Boolean = false,      // Real-time parameter adjustment
    
    // Environment settings
    val environment: TradingEnvironment = TradingEnvironment.PRODUCTION,
    val maxMemoryUsageMb: Int = 512,                      // Maximum memory usage
    val enableGracefulDegradation: Boolean = true,        // Degrade gracefully on errors
    val fallbackMode: FallbackMode = FallbackMode.SAFE_MODE
)

enum class TradingEnvironment {
    DEVELOPMENT,        // Development environment
    TESTING,           // Testing environment
    STAGING,           // Staging environment
    PRODUCTION         // Production environment
}

enum class FallbackMode {
    SAFE_MODE,         // Conservative trading
    CONSERVATIVE,      // Reduce risk
    MINIMAL,           // Minimal trading
    SHUTDOWN           // Stop trading
}