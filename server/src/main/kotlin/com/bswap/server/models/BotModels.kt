package com.bswap.server.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class BotStatus(
    val isRunning: Boolean,
    val uptimeMillis: Long,
    val activeTokens: Int,
    val totalTrades: Int,
    val successfulTrades: Int,
    val failedTrades: Int,
    val currentBalance: String,
    val lastActivity: Long?,
    val currentToken: String? = null,
    val statistics: TradingStatistics? = null,
    val connectedWallet: String? = null,
    val version: String = "1.0.0"
)

@Serializable
data class BotConfig(
    val solAmountToTrade: String,
    val autoSellAllSpl: Boolean,
    val maxKnownTokens: Int,
    val sellWaitMs: Long,
    val zeroBalanceCloseBatch: Int,
    val splSellBatch: Int,
    val closeAccountsIntervalMs: Long,
    val sellAllSplIntervalMs: Long,
    val useJito: Boolean,
    val connectedWallet: String? = null,
    val tradingParameters: TradingParameters? = null,
    val riskSettings: RiskSettings? = null
)

@Serializable
data class TradingParameters(
    val minTradeAmount: Double = 0.01,
    val maxTradeAmount: Double = 1.0,
    val takeProfitPercent: Double = 20.0,
    val stopLossPercent: Double = -10.0,
    val slippageTolerance: Double = 5.0,
    val maxConcurrentTrades: Int = 5,
    val tradingMode: TradingMode = TradingMode.CONSERVATIVE,
    val onlyBuyVerifiedTokens: Boolean = true,
    val minLiquidity: Double = 1000.0,
    val maxMarketCap: Double = 1000000.0
)

@Serializable
data class RiskSettings(
    val maxDailyLoss: Double = -100.0,
    val maxConsecutiveLosses: Int = 3,
    val pauseOnLossStreak: Boolean = true,
    val emergencyStopEnabled: Boolean = true,
    val maxPortfolioAllocation: Double = 50.0,
    val requireConfirmation: Boolean = false
)

@Serializable
enum class TradingMode {
    CONSERVATIVE, BALANCED, AGGRESSIVE
}

@Serializable
data class BotPosition(
    val tokenAddress: String,
    val symbol: String,
    val amount: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val unrealizedPnL: Double,
    val entryTime: Long,
    val status: PositionStatus
)

@Serializable
enum class PositionStatus {
    OPEN, CLOSING, CLOSED
}

@Serializable
data class BotAnalytics(
    val totalProfit: Double,
    val totalLoss: Double,
    val winRate: Double,
    val avgHoldTime: Long,
    val bestTrade: TradeResult,
    val worstTrade: TradeResult,
    val dailyPnL: Map<String, Double>,
    val performanceMetrics: PerformanceMetrics
)

@Serializable
data class TradeResult(
    val tokenSymbol: String,
    val profit: Double,
    val percentage: Double,
    val timestamp: Long
)

@Serializable
data class PerformanceMetrics(
    val sharpeRatio: Double,
    val maxDrawdown: Double,
    val volatility: Double,
    val totalReturn: Double
)

@Serializable
data class BotAlert(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long,
    val isRead: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class AlertType {
    TRADE_EXECUTED, PROFIT_TARGET, STOP_LOSS, ERROR, SYSTEM, RISK_WARNING
}

@Serializable
enum class AlertSeverity {
    INFO, WARNING, ERROR, CRITICAL
}

@Serializable
data class TokenTradeInfo(
    val tokenAddress: String,
    val state: String,
    val createdAt: Long,
    val symbol: String? = null,
    val balance: String? = null
)

@Serializable
data class TradingStatistics(
    val totalTrades: Int,
    val successfulTrades: Int,
    val failedTrades: Int,
    val totalVolume: Double,
    val totalProfitLoss: Double,
    val successRate: Double,
    val averageTradeSize: Double,
    val totalFees: Double,
    val bestTradeProfit: Double = 0.0,
    val worstTradeLoss: Double = 0.0
)

@Serializable
data class BotControlRequest(
    val action: String // "start", "stop", "pause", "resume"
)

@Serializable
data class ManualTradeRequest(
    val tokenAddress: String,
    val action: String, // "buy" or "sell"
    val amount: String? = null
)

@Serializable
data class BotConfigUpdateRequest(
    val config: BotConfig
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val timestamp: Long = System.currentTimeMillis()
)