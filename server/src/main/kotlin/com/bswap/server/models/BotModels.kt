package com.bswap.server.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class BotStatus(
    val isRunning: Boolean,
    val uptime: Long,
    val activeTokens: Int,
    val totalTrades: Int,
    val successfulTrades: Int,
    val failedTrades: Int,
    val currentBalance: String,
    val lastActivity: Long?
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
    val useJito: Boolean
)

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
    val totalVolume: String,
    val profitLoss: String,
    val averageTradeSize: String,
    val winRate: Double,
    val bestTrade: String,
    val worstTrade: String,
    val totalFees: String
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