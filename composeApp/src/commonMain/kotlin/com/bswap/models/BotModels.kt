package com.bswap.models

import kotlinx.serialization.Serializable

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