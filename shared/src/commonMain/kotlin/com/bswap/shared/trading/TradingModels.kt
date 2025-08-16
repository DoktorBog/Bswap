package com.bswap.shared.trading

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Long = 0L
)

@Serializable
data class TradingStats(
    val exchange: String,
    val isRunning: Boolean,
    val activePositions: Int,
    val openOrders: Int,
    val totalBalanceUsd: Double,
    val unrealizedPnl: Double,
    val realizedPnl: Double,
    val requestCount: Long,
    val wsActive: Boolean
)

@Serializable
data class Position(
    val symbol: String,
    val side: String,
    val size: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val pnl: Double,
    val leverage: Double,
    val margin: Double,
    val liquidationPrice: Double?,
    val updateTime: Long
) {
    val pnlPercent: Double
        get() = if (margin > 0) pnl / margin else 0.0
    
    val isProfit: Boolean
        get() = pnl > 0
    
    val liquidationRisk: Double
        get() = liquidationPrice?.let { liq ->
            if (liq > 0) {
                kotlin.math.abs(markPrice - liq) / markPrice
            } else 0.0
        } ?: 0.0
}

@Serializable
data class Balance(
    val asset: String,
    val free: Double,
    val used: Double,
    val total: Double,
    val usdValue: Double,
    val marginBalance: Double?,
    val availableMargin: Double?
) {
    val usagePercent: Double
        get() = if (total > 0) used / total else 0.0
}

@Serializable
data class Order(
    val orderId: String,
    val symbol: String,
    val side: String,
    val type: String,
    val status: String,
    val price: Double,
    val size: Double,
    val filled: Double,
    val remaining: Double,
    val averagePrice: Double?,
    val fee: Double,
    val createTime: Long,
    val updateTime: Long
) {
    val fillPercent: Double
        get() = if (size > 0) filled / size else 0.0
    
    val isActive: Boolean
        get() = status in listOf("NEW", "PARTIALLY_FILLED")
}

@Serializable
data class MarketData(
    val symbol: String,
    val price: Double,
    val bid: Double,
    val ask: Double,
    val volume24h: Double,
    val change24h: Double,
    val fundingRate: Double?,
    val markPrice: Double?,
    val indexPrice: Double?,
    val openInterest: Double?
) {
    val spread: Double
        get() = ask - bid
    
    val spreadPercent: Double
        get() = if (price > 0) spread / price else 0.0
    
    val isPositiveChange: Boolean
        get() = change24h > 0
}

@Serializable
data class TradeEvent(
    val exchange: String,
    val symbol: String,
    val action: String,
    val price: Double?,
    val amount: Double?,
    val pnl: Double?,
    val success: Boolean,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)

// Request models for API calls
@Serializable
data class CreateOrderRequest(
    val symbol: String,
    val side: String, // BUY or SELL
    val amount: Double,
    val price: Double? = null,
    val type: String = "MARKET", // MARKET, LIMIT, STOP, etc.
    val timeInForce: String = "GTC",
    val postOnly: Boolean = false,
    val reduceOnly: Boolean = false
)

@Serializable
data class OpenPositionRequest(
    val symbol: String,
    val side: String, // LONG or SHORT
    val size: Double,
    val leverage: Double = 1.0
)

@Serializable
data class ClosePositionRequest(
    val symbol: String,
    val reason: String = "Manual"
)

@Serializable
data class SetLeverageRequest(
    val symbol: String,
    val leverage: Double
)

@Serializable
data class MarginRequest(
    val symbol: String,
    val amount: Double
)

@Serializable
data class SwitchExchangeRequest(
    val exchangeType: String // SOLANA or HYPERLIQUID
)

// UI State models
@Serializable
data class TradingState(
    val stats: TradingStats? = null,
    val positions: List<Position> = emptyList(),
    val balances: List<Balance> = emptyList(),
    val orders: List<Order> = emptyList(),
    val markets: List<MarketData> = emptyList(),
    val events: List<TradeEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdate: Long = 0L
)

enum class ExchangeType {
    SOLANA,
    HYPERLIQUID
}

enum class PositionSide {
    LONG,
    SHORT
}

enum class OrderSide {
    BUY,
    SELL
}

enum class OrderType {
    MARKET,
    LIMIT,
    STOP,
    STOP_LIMIT
}

enum class TimeInForce {
    GTC, // Good Till Cancelled
    IOC, // Immediate Or Cancel
    FOK, // Fill Or Kill
    ALO  // Add Liquidity Only
}

// Utility extensions
fun Double.formatPrice(): String = "%.2f".format(this)
fun Double.formatPercent(): String = "%.2f%%".format(this * 100)
fun Double.formatPnL(): String = if (this >= 0) "+%.2f".format(this) else "%.2f".format(this)

fun Long.formatTime(): String {
    val date = Instant.fromEpochMilliseconds(this)
    return date.toString().substring(11, 16) // HH:MM format
}

// Chart and Market Data Models
@Serializable
data class CandlestickData(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

@Serializable
data class OrderBookEntry(
    val price: Double,
    val size: Double,
    val total: Double
)

@Serializable
data class OrderBook(
    val symbol: String,
    val bids: List<OrderBookEntry>,
    val asks: List<OrderBookEntry>,
    val timestamp: Long
)

@Serializable
data class TradingViewData(
    val symbol: String,
    val timeframe: String,
    val candles: List<CandlestickData>,
    val indicators: Map<String, List<Double>> = emptyMap()
)

@Serializable
data class RecentTrade(
    val id: String,
    val symbol: String,
    val price: Double,
    val size: Double,
    val side: String,
    val timestamp: Long
)

@Serializable
data class FundingHistory(
    val symbol: String,
    val fundingRate: Double,
    val fundingTime: Long,
    val markPrice: Double
)

// Enhanced Market Data
@Serializable
data class DetailedMarketData(
    val symbol: String,
    val price: Double,
    val bid: Double,
    val ask: Double,
    val volume24h: Double,
    val change24h: Double,
    val fundingRate: Double?,
    val markPrice: Double?,
    val indexPrice: Double?,
    val openInterest: Double?,
    val spreadPercent: Double,
    val lastTradePrice: Double,
    val lastTradeSize: Double,
    val lastTradeTime: Long
)