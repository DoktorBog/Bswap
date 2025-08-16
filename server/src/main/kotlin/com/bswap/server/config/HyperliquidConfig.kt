package com.bswap.server.config

import kotlinx.serialization.Serializable

/**
 * Hyperliquid exchange configuration for DEX trading
 * Supports both spot and perpetual futures markets
 */
@Serializable
data class HyperliquidConfig(
    // Exchange selection
    val enabled: Boolean = false,
    val exchangeType: ExchangeType = ExchangeType.SOLANA,  // Default to Solana, can switch to HYPERLIQUID
    
    // API credentials (for Hyperliquid)
    val apiKey: String = "",
    val apiSecret: String = "",
    val walletAddress: String = "",  // Ethereum wallet address for Hyperliquid
    val privateKey: String = "",     // Private key for signing transactions
    
    // Trading parameters
    val defaultLeverage: Double = 1.0,  // 1x leverage by default (spot-like)
    val maxLeverage: Double = 20.0,     // Maximum allowed leverage
    val marginMode: MarginMode = MarginMode.CROSS,
    
    // Position management
    val maxPositions: Int = 10,
    val positionSizePercent: Double = 10.0,  // % of account balance per position
    val autoCloseOnProfit: Double = 0.05,    // Auto close at 5% profit
    val autoCloseOnLoss: Double = 0.02,      // Auto close at 2% loss
    
    // Risk management
    val enableStopLoss: Boolean = true,
    val stopLossPercent: Double = 0.02,      // 2% stop loss
    val enableTakeProfit: Boolean = true,
    val takeProfitPercent: Double = 0.05,    // 5% take profit
    val enableTrailingStop: Boolean = true,
    val trailingStopPercent: Double = 0.015, // 1.5% trailing stop
    
    // Order configuration
    val orderType: OrderType = OrderType.LIMIT,
    val timeInForce: TimeInForce = TimeInForce.GTC,
    val postOnly: Boolean = false,           // Maker only orders
    val reduceOnly: Boolean = false,         // For closing positions only
    
    // Fee configuration
    val makerFee: Double = 0.0002,  // 0.02% maker fee
    val takerFee: Double = 0.0005,  // 0.05% taker fee
    
    // Network settings
    val rpcUrl: String = "https://api.hyperliquid.xyz",
    val wsUrl: String = "wss://api.hyperliquid.xyz/ws",
    val testnet: Boolean = false,
    
    // Rate limiting
    val maxRequestsPerSecond: Int = 10,
    val maxOrdersPerMinute: Int = 100,
    
    // Monitoring
    val enableWebSocket: Boolean = true,
    val enableOrderBookStream: Boolean = true,
    val enableTradeStream: Boolean = true,
    val enablePositionStream: Boolean = true,
    val enableBalanceStream: Boolean = true,
    
    // Auto-tuning
    val enableAutoTuning: Boolean = true,
    val autoAdjustLeverage: Boolean = true,
    val autoAdjustPositionSize: Boolean = true,
    
    // Logging
    val logAllTrades: Boolean = true,
    val logAllOrders: Boolean = true,
    val logBalanceChanges: Boolean = true,
    val logLevel: LogLevel = LogLevel.INFO
)

enum class ExchangeType {
    SOLANA,      // Current Solana DEX (Raydium, Jupiter, etc.)
    HYPERLIQUID  // Hyperliquid DEX
}

enum class MarginMode {
    CROSS,       // Cross margin - shares margin across all positions
    ISOLATED     // Isolated margin - separate margin for each position
}

enum class OrderType {
    MARKET,      // Market order - immediate execution
    LIMIT,       // Limit order - execute at specific price or better
    STOP,        // Stop order - trigger at stop price
    STOP_LIMIT,  // Stop limit - trigger limit order at stop price
    TRAILING_STOP // Trailing stop order
}

enum class TimeInForce {
    GTC,  // Good Till Cancelled
    IOC,  // Immediate Or Cancel
    FOK,  // Fill Or Kill
    ALO   // Add Liquidity Only (Post-only)
}

enum class LogLevel {
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE
}

/**
 * Position information for Hyperliquid
 */
@Serializable
data class HyperliquidPosition(
    val symbol: String,
    val side: PositionSide,
    val size: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val liquidationPrice: Double?,
    val unrealizedPnl: Double,
    val realizedPnl: Double,
    val margin: Double,
    val leverage: Double,
    val positionId: String,
    val openTime: Long,
    val updateTime: Long
)

enum class PositionSide {
    LONG,
    SHORT
}

/**
 * Order information for Hyperliquid
 */
@Serializable
data class HyperliquidOrder(
    val orderId: String,
    val clientOrderId: String?,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val status: OrderStatus,
    val price: Double,
    val size: Double,
    val filled: Double,
    val remaining: Double,
    val averagePrice: Double?,
    val fee: Double,
    val timeInForce: TimeInForce,
    val postOnly: Boolean,
    val reduceOnly: Boolean,
    val createTime: Long,
    val updateTime: Long
)

enum class OrderSide {
    BUY,
    SELL
}

enum class OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED,
    EXPIRED
}

/**
 * Balance information for Hyperliquid
 */
@Serializable
data class HyperliquidBalance(
    val asset: String,
    val free: Double,        // Available balance
    val used: Double,        // Balance in orders
    val total: Double,       // Total balance
    val usdValue: Double,    // USD value of total balance
    val marginBalance: Double?, // For futures trading
    val availableMargin: Double?, // Available margin for new positions
    val updateTime: Long
)

/**
 * Market information for Hyperliquid
 */
@Serializable
data class HyperliquidMarket(
    val symbol: String,
    val base: String,
    val quote: String,
    val type: MarketType,
    val active: Boolean,
    val minOrderSize: Double,
    val maxOrderSize: Double,
    val tickSize: Double,     // Minimum price increment
    val stepSize: Double,     // Minimum quantity increment
    val makerFee: Double,
    val takerFee: Double,
    val maxLeverage: Double?,
    val maintenanceMargin: Double?,
    val initialMargin: Double?
)

enum class MarketType {
    SPOT,
    PERPETUAL,
    FUTURE
}

/**
 * Trade execution result
 */
@Serializable
data class HyperliquidTradeResult(
    val success: Boolean,
    val orderId: String?,
    val executedPrice: Double?,
    val executedSize: Double?,
    val fee: Double?,
    val timestamp: Long,
    val error: String?
)

/**
 * Funding rate information for perpetuals
 */
@Serializable
data class HyperliquidFundingRate(
    val symbol: String,
    val fundingRate: Double,
    val fundingTime: Long,
    val nextFundingTime: Long,
    val markPrice: Double,
    val indexPrice: Double,
    val openInterest: Double,
    val volume24h: Double
)