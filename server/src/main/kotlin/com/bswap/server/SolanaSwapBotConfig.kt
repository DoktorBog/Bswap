package com.bswap.server

import com.bswap.server.data.dexscreener.models.TokenBoost
import com.bswap.server.data.dexscreener.models.TokenProfile
import com.bswap.server.data.solana.pumpfun.TokenTradeResponse
import com.bswap.server.data.solana.transaction.TokenInfo
import com.bswap.shared.wallet.WalletConfig
import foundation.metaplex.solanapublickeys.PublicKey
import java.math.BigDecimal

data class SolanaSwapBotConfig(
    val swapMint: PublicKey = PublicKey("So11111111111111111111111111111111111111112"),
    val solAmountToTrade: BigDecimal = BigDecimal("0.001"),
    val autoSellAllSpl: Boolean = true,
    val sellAllSplIntervalMs: Long = 60_000 * 10,
    val closeAccountsIntervalMs: Long = 60_000,
    val zeroBalanceCloseBatch: Int = 10,
    val splSellBatch: Int = 3,
    val sellWaitMs: Long = 60_000,
    val useJito: Boolean = true,
    val validationMaxRisk: Double = 0.7,
    val maxKnownTokens: Int = 1000,
    val strategySettings: TradingStrategySettings = TradingStrategySettings(),
    val strategyTickMs: Long = 1_000,
    val blockBuy: Boolean = false,
)

sealed interface TokenState {
    data object TradePending : TokenState
    data object Swapped : TokenState
    data object Selling : TokenState
    data object Sold : TokenState
    data class SellFailed(val reason: String) : TokenState
}

data class TokenStatus(
    val tokenAddress: String,
    var state: TokenState,
    val createdAt: Long = System.currentTimeMillis()
)

enum class StrategyType {
    IMMEDIATE,
    DELAYED_ENTRY,
    BATCH_ACCUMULATE,
    PUMPFUN_PRIORITY,
    SMA_CROSS,
    RSI_BASED,
    BREAKOUT,
    BOLLINGER_MEAN_REVERSION,
    MOMENTUM,
    TECHNICAL_ANALYSIS_COMBINED,
    AI_STRATEGY
}

data class ImmediateConfig(
    val minHoldMs: Long = 60_000
)

data class DelayedEntryConfig(
    val entryDelayMs: Long = 15_000,
    val minHoldMs: Long = 60_000
)


data class BatchAccumulateConfig(
    val batchSize: Int = 5,
    val batchIntervalMs: Long = 30_000,
    val minHoldMs: Long = 120_000
)

data class PumpFunPriorityConfig(
    val minHoldMs: Long = 60_000
)

data class SmaCrossConfig(
    val fastPeriod: Int = 5,
    val slowPeriod: Int = 20,
    val confirmBars: Int = 2,
    val qtyFraction: Double = 1.0,
    val minHoldMs: Long = 60_000
)

data class RsiBasedConfig(
    val period: Int = 14,
    val buyBelow: Double = 30.0,
    val sellAbove: Double = 70.0,
    val qtyFraction: Double = 1.0,
    val minHoldMs: Long = 60_000
)

data class BreakoutConfig(
    val lookback: Int = 20,
    val bufferPct: Double = 0.002,
    val qtyFraction: Double = 1.0,
    val minHoldMs: Long = 60_000
)

data class BollingerMeanReversionConfig(
    val period: Int = 20,
    val dev: Double = 2.0,
    val qtyFraction: Double = 1.0,
    val minHoldMs: Long = 60_000
)

data class MomentumConfig(
    val rocPeriod: Int = 6,
    val buyThreshold: Double = 0.01,
    val sellThreshold: Double = 0.01,
    val qtyFraction: Double = 1.0,
    val minHoldMs: Long = 60_000
)

data class TechnicalAnalysisConfig(
    val smaWeight: Double = 0.5,
    val rsiWeight: Double = 0.35,
    val breakoutWeight: Double = 0.4,
    val bollingerWeight: Double = 0.25,
    val momentumWeight: Double = 0.2,
    val decisionThreshold: Double = 0.6,
    val qtyFraction: Double = 1.0,
    val minHoldMs: Long = 60_000,
    val takeProfitPct: Double = 0.25,
    val stopLossPct: Double = 0.15,
    val trailingStopPct: Double = 0.1,
    val slippageBps: Int = 150
)

data class AIStrategyConfig(
    val modelType: AIModelType = AIModelType.NEURAL_NETWORK,
    val learningRate: Double = 0.001,
    val hiddenLayers: List<Int> = listOf(128, 64, 32),
    val lookbackPeriod: Int = 50,
    val predictionHorizon: Int = 5,
    val confidenceThreshold: Double = 0.75,
    val retrainIntervalMs: Long = 3600000, // 1 hour
    val maxTrainingSamples: Int = 10000,
    val featureWeights: FeatureWeights = FeatureWeights(),
    val riskParameters: AIRiskParameters = AIRiskParameters(),
    val qtyFraction: Double = 1.0,
    val minHoldMs: Long = 60_000,
    val takeProfitPct: Double = 0.20,
    val stopLossPct: Double = 0.12,
    val adaptiveLearning: Boolean = true,
    val useEnsemble: Boolean = true,
    val sentimentAnalysis: Boolean = true
)

data class FeatureWeights(
    val priceAction: Double = 0.3,
    val volume: Double = 0.25,
    val momentum: Double = 0.2,
    val volatility: Double = 0.15,
    val sentiment: Double = 0.1
)

data class AIRiskParameters(
    val maxPositionSize: Double = 0.1, // 10% of portfolio
    val volatilityAdjustment: Boolean = true,
    val drawdownLimit: Double = 0.05, // 5% max drawdown
    val sharpeRatioThreshold: Double = 1.0
)

enum class AIModelType {
    NEURAL_NETWORK,
    RANDOM_FOREST,
    GRADIENT_BOOSTING,
    ENSEMBLE
}

data class TradingStrategySettings(
    val type: StrategyType = StrategyType.AI_STRATEGY,
    val immediate: ImmediateConfig = ImmediateConfig(),
    val delayed: DelayedEntryConfig = DelayedEntryConfig(),
    val batch: BatchAccumulateConfig = BatchAccumulateConfig(),
    val pumpFun: PumpFunPriorityConfig = PumpFunPriorityConfig(),
    val smaCross: SmaCrossConfig = SmaCrossConfig(),
    val rsiBased: RsiBasedConfig = RsiBasedConfig(),
    val breakout: BreakoutConfig = BreakoutConfig(),
    val bollingerMeanReversion: BollingerMeanReversionConfig = BollingerMeanReversionConfig(),
    val momentum: MomentumConfig = MomentumConfig(),
    val technicalAnalysis: TechnicalAnalysisConfig = TechnicalAnalysisConfig(),
    val aiStrategy: AIStrategyConfig = AIStrategyConfig()
)

enum class TokenSource {
    PROFILE,
    BOOST,
    PUMPFUN
}

data class TokenMeta(
    val mint: String,
    val source: TokenSource,
    val profile: TokenProfile? = null,
    val boost: TokenBoost? = null,
    val pump: TokenTradeResponse? = null
)

interface TradingRuntime {
    val walletConfig: WalletConfig
    val config: SolanaSwapBotConfig
    fun now(): Long
    fun isNew(mint: String): Boolean
    fun status(mint: String): TokenStatus?
    suspend fun buy(mint: String): Boolean
    suspend fun sell(mint: String): Boolean
    suspend fun tokenInfo(mint: String): TokenInfo?
    suspend fun allTokens(): List<TokenInfo>
    suspend fun getTokenUsdPrice(mint: String): Double?
}

var privateKey: String = ""
