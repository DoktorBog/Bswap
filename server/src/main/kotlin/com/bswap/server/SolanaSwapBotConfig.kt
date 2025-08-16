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
    val solAmountToTrade: BigDecimal = BigDecimal("0.0026"),  // Smaller amounts for more trades
    val autoSellAllSpl: Boolean = true,
    val sellAllSplIntervalMs: Long = 60_000 * 3,
    val closeAccountsIntervalMs: Long = 60_000,
    val zeroBalanceCloseBatch: Int = 10,
    val splSellBatch: Int = 3,
    val sellWaitMs: Long = 60_000,
    val useJito: Boolean = true,
    val validationMaxRisk: Double = 0.7,
    val maxKnownTokens: Int = 1000,
    val strategySettings: TradingStrategySettings = TradingStrategySettings(type = StrategyType.SHITCOIN_SCALPER),
    val strategyTickMs: Long = 500,    // Ultra fast strategy ticks for scalping
    val blockBuy: Boolean = false,  // Allow buying - ENABLED for new token discovery

    // Sell queue configuration
    val sellQueue: SellQueueConfig = SellQueueConfig(),

    // RPC rate limiter configuration
    val rpcRateLimiter: RpcRateLimiterConfig = RpcRateLimiterConfig(),

    // Price service configuration
    val priceService: PriceServiceConfig = PriceServiceConfig(),

    // Whitelist configuration
    val whitelist: WhitelistConfig = WhitelistConfig(enabled = false),

    // Whitelist buy observer configuration
    val whitelistBuyObserver: WhitelistBuyObserverConfig = WhitelistBuyObserverConfig(enabled = false),

    // New token discovery limits
    val maxActiveTokens: Int = 10  // Maximum tokens to hold at once
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
    WALLET_SELL_ONLY,
    SHITCOIN_SCALPER
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
    val period: Int = 14,              // Standard RSI period - now uses real price history
    val oversoldThreshold: Double = 30.0,  // Buy when RSI below this (with price history)
    val overboughtThreshold: Double = 70.0, // Sell when RSI above this (with price history)
    val buyBelow: Double = 30.0,      // Legacy - same as oversoldThreshold
    val sellAbove: Double = 70.0,     // Legacy - same as overboughtThreshold
    val qtyFraction: Double = 1.0,
    val minHoldMs: Long = 3_000       // Minimum hold time before RSI-based sell signals
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

data class TradingStrategySettings(
    val type: StrategyType = StrategyType.WALLET_SELL_ONLY,
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
    val walletSellOnly: WalletSellOnlyConfig = WalletSellOnlyConfig(),
    val shitcoinScalper: ShitcoinScalperConfig = ShitcoinScalperConfig()
)

data class WalletSellOnlyConfig(
    val sellIntervalMs: Long = 5_000L,      // Check for sells every 5 seconds (more frequent)
    val minHoldTimeMs: Long = 1_000L,       // Minimum 1 second before selling (immediate)
    val maxHoldTimeMs: Long = 60_000L,      // Force sell after 1 minute (faster)
    val sellDelayBetweenTokensMs: Long = 500L, // 0.5 seconds between sell batches (faster)
    val ignoreTokens: Set<String> = setOf(  // Don't sell these tokens
        "So11111111111111111111111111111111111111112", // SOL
        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", // USDC
        "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"  // USDT
    )
)

data class ShitcoinScalperConfig(
    val maxHoldTimeMs: Long = 45_000L,      // Hold up to 45 seconds for better profit opportunity
    val profitTakePercent: Double = 0.02,   // Take profit at 2% (more reasonable target)
    val stopLossPercent: Double = 0.08,     // Stop loss at 8% (less aggressive, allows for volatility)
    val emergencyStopLoss: Double = 0.15,   // Emergency stop at 15% to prevent major losses
    val volumeCheckIntervalMs: Long = 2_000L, // Check volume every 2 seconds (less frequent)
    val minVolumeIncrease: Double = 0.15,   // 15% volume increase triggers sell
    val priceMovementThreshold: Double = 0.03, // 3% price movement triggers sell
    val onlyPumpTokens: Boolean = true,     // Focus on pump.fun tokens for better liquidity
    val qtyFraction: Double = 1.0,
    val fastExitOnActivity: Boolean = true, // Sell when others start buying
    val maxTokensHeld: Int = 10,           // Maximum concurrent positions
    val onlyNewTokens: Boolean = true,     // Only buy newly discovered tokens
    val validatePools: Boolean = false,    // Disable pool validation for faster new token discovery

    // Smart exit parameters
    val trendAnalysisWindow: Int = 8,       // Look at last 8 price points for trend
    val minProfitBeforeTrailing: Double = 0.005, // 0.5% profit before enabling trailing stop
    val trailingStopPercent: Double = 0.03, // 3% trailing stop from peak
    val consolidationTimeMs: Long = 10_000L, // If flat for 10 seconds, consider exit
    val consolidationThreshold: Double = 0.01 // 1% range considered "flat"
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
    val getPriceHistory: (suspend (String) -> List<Double>?)? // Optional price history provider
    fun now(): Long
    fun isNew(mint: String): Boolean
    fun status(mint: String): TokenStatus?
    suspend fun buy(mint: String): Boolean
    suspend fun sell(mint: String): Boolean
    suspend fun tokenInfo(mint: String): TokenInfo?
    suspend fun allTokens(): List<TokenInfo>
    suspend fun getTokenUsdPrice(mint: String): Double?
}

data class SellQueueConfig(
    val enabled: Boolean = false,     // Disable queue - use direct sells
    val maxConcurrency: Int = 3,
    val spacingMs: Long = 100L,
    val retryCount: Int = 2,
    val retryDelayMs: Long = 500L
)

data class RpcRateLimiterConfig(
    val enabled: Boolean = true,
    val maxRps: Int = 14,
    val bucketSize: Int = 28 // Allow some burst capacity
)

data class PriceServiceConfig(
    val sellOnPriceMissing: Boolean = false,
    val priceMissingMaxStrikes: Int = 4,
    val priceMissingWindowMs: Long = 60_000L,
    val allowBuyWithoutPrice: Boolean = true  // Enable for RSI strategy with synthetic data
)

data class WhitelistConfig(
    val enabled: Boolean = false,
    val symbols: Set<String> = setOf(
        // Major liquid tokens
        "SOL", "USDC", "USDT", "JUP", "PYTH", "JTO", "RAY", "ORCA",
        // LST tokens
        "mSOL", "bSOL", "jitoSOL",
        // Large meme coins with good liquidity
        "BONK", "WIF", "POPCAT",
        // Other ecosystem tokens
        "TNSR", "HNT", "WEN", "SAMO"
    )
)

data class WhitelistBuyObserverConfig(
    val enabled: Boolean = false,
    val observationIntervalMs: Long = 10000, // Check every 10 seconds
    val priceChangeThreshold: Double = 0.05, // 5% price increase threshold
    val volumeThreshold: Double = 50000.0, // Minimum volume threshold
    val maxConcurrentBuys: Int = 2, // Maximum concurrent buy operations
    val cooldownMs: Long = 300000, // 5 minutes cooldown between buys for same token
    val priorityThreshold: Int = 50, // Only observe coins with priority >= this value
    val enablePriceAlerts: Boolean = true,
    val enableVolumeAlerts: Boolean = true,
    val autoExecuteBuys: Boolean = true // Automatically execute buys on signals
)

var privateKey: String = ""
