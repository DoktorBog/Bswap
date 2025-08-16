package com.bswap.server.routes

import com.bswap.server.config.*
import com.bswap.server.service.UnifiedTradingService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TradingRoutes")

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
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
)

@Serializable
data class Balance(
    val asset: String,
    val free: Double,
    val used: Double,
    val total: Double,
    val usdValue: Double,
    val marginBalance: Double?,
    val availableMargin: Double?
)

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
)

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
)

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

// Global reference to the trading service (will be injected)
lateinit var globalTradingService: UnifiedTradingService

fun Route.tradingRoutes(tradingService: UnifiedTradingService) {
    // Set global reference
    globalTradingService = tradingService
    
    route("/api/wallet") {
        
        // Setup wallet for Hyperliquid trading
        post("/setup") {
            try {
                val walletData = call.receive<Map<String, String>>()
                val address = walletData["address"] ?: throw IllegalArgumentException("Address required")
                val privateKey = walletData["privateKey"] ?: throw IllegalArgumentException("Private key required")
                val seedPhrase = walletData["seedPhrase"] ?: throw IllegalArgumentException("Seed phrase required")
                
                logger.info("🔐 Setting up wallet for Hyperliquid trading: $address")
                
                // TODO: Update the Hyperliquid service configuration with the new wallet
                // For now, just return success
                
                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = "Wallet setup successful",
                    timestamp = System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                logger.error("Error setting up wallet", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
    }
    
    route("/api/trading") {
        
        // =================================================================================================
        // GENERAL STATUS AND CONTROL
        // =================================================================================================
        
        get("/status") {
            try {
                val stats = tradingService.getStats()
                val (unrealizedPnL, realizedPnL) = tradingService.getPnL()
                
                val tradingStats = TradingStats(
                    exchange = stats["exchange"] as? String ?: "UNKNOWN",
                    isRunning = stats["isRunning"] as? Boolean ?: false,
                    activePositions = stats["activePositions"] as? Int ?: 0,
                    openOrders = stats["openOrders"] as? Int ?: 0,
                    totalBalanceUsd = stats["accountBalance"] as? Double ?: 0.0,
                    unrealizedPnl = unrealizedPnL,
                    realizedPnl = realizedPnL,
                    requestCount = stats["requestCount"] as? Long ?: 0L,
                    wsActive = stats["wsActive"] as? Boolean ?: false
                )
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = tradingStats))
            } catch (e: Exception) {
                logger.error("Error getting trading status", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<TradingStats>(success = false, error = e.message))
            }
        }
        
        post("/start") {
            try {
                tradingService.startTrading()
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Trading started"))
            } catch (e: Exception) {
                logger.error("Error starting trading", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        post("/stop") {
            try {
                tradingService.stopTrading()
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Trading stopped"))
            } catch (e: Exception) {
                logger.error("Error stopping trading", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        post("/emergency-stop") {
            try {
                val reason = call.receiveText().ifBlank { "Emergency stop from UI" }
                tradingService.emergencyStop(reason)
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Emergency stop executed"))
            } catch (e: Exception) {
                logger.error("Error executing emergency stop", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        // =================================================================================================
        // EXCHANGE MANAGEMENT
        // =================================================================================================
        
        get("/exchange") {
            try {
                val currentExchange = tradingService.getCurrentExchange().name
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = currentExchange))
            } catch (e: Exception) {
                logger.error("Error getting current exchange", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        post("/exchange/switch") {
            try {
                val request = call.receive<SwitchExchangeRequest>()
                val exchangeType = ExchangeType.valueOf(request.exchangeType)
                val success = tradingService.switchExchange(exchangeType)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Exchange switched to ${request.exchangeType}"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<String>(success = false, error = "Failed to switch exchange"))
                }
            } catch (e: Exception) {
                logger.error("Error switching exchange", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        // =================================================================================================
        // BALANCE MANAGEMENT
        // =================================================================================================
        
        get("/balance") {
            try {
                val balanceMap = tradingService.getBalance()
                val balances = balanceMap.map { (asset, total) ->
                    Balance(
                        asset = asset,
                        free = total, // Simplified for mock
                        used = 0.0,
                        total = total,
                        usdValue = total,
                        marginBalance = if (asset == "USDC") total else null,
                        availableMargin = if (asset == "USDC") total else null
                    )
                }
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = balances))
            } catch (e: Exception) {
                logger.error("Error getting balance", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<List<Balance>>(success = false, error = e.message))
            }
        }
        
        // =================================================================================================
        // POSITION MANAGEMENT
        // =================================================================================================
        
        get("/positions") {
            try {
                val positionList = tradingService.getPositions()
                val positions = positionList.map { pos ->
                    Position(
                        symbol = pos.symbol,
                        side = pos.side,
                        size = pos.size,
                        entryPrice = pos.entryPrice,
                        markPrice = pos.markPrice,
                        pnl = pos.pnl,
                        leverage = pos.leverage,
                        margin = pos.size * pos.entryPrice / pos.leverage,
                        liquidationPrice = null, // Will be calculated
                        updateTime = System.currentTimeMillis()
                    )
                }
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = positions))
            } catch (e: Exception) {
                logger.error("Error getting positions", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<List<Position>>(success = false, error = e.message))
            }
        }
        
        post("/positions/open") {
            try {
                val request = call.receive<OpenPositionRequest>()
                // This would call the appropriate service method
                // For now, return success
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Position opened"))
            } catch (e: Exception) {
                logger.error("Error opening position", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        post("/positions/close") {
            try {
                val request = call.receive<ClosePositionRequest>()
                val success = tradingService.closePosition(request.symbol)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Position closed"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<String>(success = false, error = "Failed to close position"))
                }
            } catch (e: Exception) {
                logger.error("Error closing position", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        post("/positions/close-all") {
            try {
                val success = tradingService.closeAllPositions()
                
                if (success) {
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "All positions closed"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<String>(success = false, error = "Failed to close all positions"))
                }
            } catch (e: Exception) {
                logger.error("Error closing all positions", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        post("/positions/leverage") {
            try {
                val request = call.receive<SetLeverageRequest>()
                // This would call the appropriate service method
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Leverage set to ${request.leverage}x"))
            } catch (e: Exception) {
                logger.error("Error setting leverage", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        post("/positions/margin/add") {
            try {
                val request = call.receive<MarginRequest>()
                // This would call the appropriate service method
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Margin added"))
            } catch (e: Exception) {
                logger.error("Error adding margin", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        post("/positions/margin/reduce") {
            try {
                val request = call.receive<MarginRequest>()
                // This would call the appropriate service method
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Margin reduced"))
            } catch (e: Exception) {
                logger.error("Error reducing margin", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        // =================================================================================================
        // ORDER MANAGEMENT
        // =================================================================================================
        
        get("/orders") {
            try {
                // Mock orders for now
                val orders = emptyList<Order>()
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = orders))
            } catch (e: Exception) {
                logger.error("Error getting orders", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<List<Order>>(success = false, error = e.message))
            }
        }
        
        post("/orders") {
            try {
                val request = call.receive<CreateOrderRequest>()
                // This would call the appropriate service method
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Order created"))
            } catch (e: Exception) {
                logger.error("Error creating order", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        delete("/orders/{orderId}") {
            try {
                val orderId = call.parameters["orderId"] ?: throw IllegalArgumentException("Order ID required")
                // This would call the appropriate service method
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "Order cancelled"))
            } catch (e: Exception) {
                logger.error("Error cancelling order", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        delete("/orders") {
            try {
                // This would call the appropriate service method to cancel all orders
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = "All orders cancelled"))
            } catch (e: Exception) {
                logger.error("Error cancelling all orders", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<String>(success = false, error = e.message))
            }
        }
        
        // =================================================================================================
        // MARKET DATA
        // =================================================================================================
        
        get("/markets") {
            try {
                logger.info("📊 Markets endpoint called - fetching market data from trading service")
                
                // Get market data from the actual trading service
                val marketDataList = tradingService.getMarkets()
                logger.info("📈 Retrieved ${marketDataList.size} markets from trading service")
                
                val markets = if (marketDataList.isNotEmpty()) {
                    // Convert from service format to API format
                    marketDataList.map { market ->
                        MarketData(
                            symbol = market.symbol,
                            price = market.price,
                            bid = market.bid ?: market.price * 0.999,
                            ask = market.ask ?: market.price * 1.001,
                            volume24h = market.volume24h ?: 1000000.0,
                            change24h = market.change24h ?: 0.0,
                            fundingRate = market.fundingRate,
                            markPrice = market.markPrice ?: market.price,
                            indexPrice = market.indexPrice ?: market.price,
                            openInterest = market.openInterest
                        )
                    }
                } else {
                    logger.warn("⚠️ No markets found from trading service, returning fallback mock data")
                    // Fallback mock data if no markets available
                    listOf(
                        MarketData(
                            symbol = "BTC-PERP",
                            price = 50000.0,
                            bid = 49950.0,
                            ask = 50050.0,
                            volume24h = 1000000.0,
                            change24h = 0.025,
                            fundingRate = 0.0001,
                            markPrice = 50000.0,
                            indexPrice = 50000.0,
                            openInterest = 5000000.0
                        ),
                        MarketData(
                            symbol = "ETH-PERP",
                            price = 3000.0,
                            bid = 2995.0,
                            ask = 3005.0,
                            volume24h = 500000.0,
                            change24h = 0.015,
                            fundingRate = 0.0001,
                            markPrice = 3000.0,
                            indexPrice = 3000.0,
                            openInterest = 2000000.0
                        ),
                        MarketData(
                            symbol = "SOL-PERP",
                            price = 150.0,
                            bid = 149.5,
                            ask = 150.5,
                            volume24h = 200000.0,
                            change24h = -0.005,
                            fundingRate = 0.0001,
                            markPrice = 150.0,
                            indexPrice = 150.0,
                            openInterest = 1000000.0
                        )
                    )
                }
                
                logger.info("✅ Returning ${markets.size} markets to frontend")
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = markets))
            } catch (e: Exception) {
                logger.error("Error getting markets", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<List<MarketData>>(success = false, error = e.message))
            }
        }
        
        get("/markets/{symbol}") {
            try {
                val symbol = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol required")
                // This would call the appropriate service method
                val marketData = MarketData(
                    symbol = symbol,
                    price = 50000.0,
                    bid = 49950.0,
                    ask = 50050.0,
                    volume24h = 1000000.0,
                    change24h = 0.025,
                    fundingRate = 0.0001,
                    markPrice = 50000.0,
                    indexPrice = 50000.0,
                    openInterest = 5000000.0
                )
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = marketData))
            } catch (e: Exception) {
                logger.error("Error getting market data", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<MarketData>(success = false, error = e.message))
            }
        }
        
        // =================================================================================================
        // CHART DATA AND TECHNICAL ANALYSIS
        // =================================================================================================
        
        get("/charts/{symbol}/candlesticks") {
            try {
                val symbol = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol required")
                val timeframe = call.request.queryParameters["timeframe"] ?: "1h"
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                
                // Generate mock candlestick data
                val now = System.currentTimeMillis()
                val intervalMs = when (timeframe) {
                    "1m" -> 60_000L
                    "5m" -> 300_000L
                    "15m" -> 900_000L
                    "1h" -> 3_600_000L
                    "4h" -> 14_400_000L
                    "1d" -> 86_400_000L
                    else -> 3_600_000L
                }
                
                val basePrice = when (symbol) {
                    "BTC-PERP" -> 50000.0
                    "ETH-PERP" -> 3000.0
                    "SOL-PERP" -> 150.0
                    else -> 100.0
                }
                
                val candles = (0 until limit).map { i ->
                    val timestamp = now - (limit - i) * intervalMs
                    val variation = (Math.random() - 0.5) * 0.05 // 5% variation
                    val open = basePrice * (1 + variation)
                    val close = open * (1 + (Math.random() - 0.5) * 0.03)
                    val high = maxOf(open, close) * (1 + Math.random() * 0.02)
                    val low = minOf(open, close) * (1 - Math.random() * 0.02)
                    val volume = Math.random() * 1000000
                    
                    CandlestickData(timestamp, open, high, low, close, volume)
                }
                
                val tradingViewData = TradingViewData(
                    symbol = symbol,
                    timeframe = timeframe,
                    candles = candles,
                    indicators = mapOf(
                        "sma20" to candles.takeLast(20).map { it.close }.let { prices ->
                            prices.indices.map { i ->
                                prices.take(i + 1).average()
                            }
                        },
                        "rsi" to candles.map { 50.0 + (Math.random() - 0.5) * 60 } // Mock RSI
                    )
                )
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = tradingViewData))
            } catch (e: Exception) {
                logger.error("Error getting candlestick data", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<TradingViewData>(success = false, error = e.message))
            }
        }
        
        get("/charts/{symbol}/orderbook") {
            try {
                val symbol = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol required")
                val depth = call.request.queryParameters["depth"]?.toIntOrNull() ?: 20
                
                val basePrice = when (symbol) {
                    "BTC-PERP" -> 50000.0
                    "ETH-PERP" -> 3000.0
                    "SOL-PERP" -> 150.0
                    else -> 100.0
                }
                
                // Generate mock order book
                val bids = (1..depth).map { i ->
                    val price = basePrice - (i * basePrice * 0.001)
                    val size = Math.random() * 10
                    val total = if (i == 1) size else {
                        // Cumulative total would be calculated here
                        size * i
                    }
                    OrderBookEntry(price, size, total)
                }
                
                val asks = (1..depth).map { i ->
                    val price = basePrice + (i * basePrice * 0.001)
                    val size = Math.random() * 10
                    val total = if (i == 1) size else {
                        // Cumulative total would be calculated here
                        size * i
                    }
                    OrderBookEntry(price, size, total)
                }
                
                val orderBook = OrderBook(
                    symbol = symbol,
                    bids = bids,
                    asks = asks,
                    timestamp = System.currentTimeMillis()
                )
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = orderBook))
            } catch (e: Exception) {
                logger.error("Error getting order book", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<OrderBook>(success = false, error = e.message))
            }
        }
        
        get("/charts/{symbol}/trades") {
            try {
                val symbol = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol required")
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                
                val basePrice = when (symbol) {
                    "BTC-PERP" -> 50000.0
                    "ETH-PERP" -> 3000.0
                    "SOL-PERP" -> 150.0
                    else -> 100.0
                }
                
                // Generate mock recent trades
                val trades = (1..limit).map { i ->
                    val price = basePrice * (1 + (Math.random() - 0.5) * 0.01)
                    val size = Math.random() * 5
                    val side = if (Math.random() > 0.5) "BUY" else "SELL"
                    val timestamp = System.currentTimeMillis() - (i * 1000)
                    
                    RecentTrade(
                        id = "trade_${timestamp}_$i",
                        symbol = symbol,
                        price = price,
                        size = size,
                        side = side,
                        timestamp = timestamp
                    )
                }
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = trades.reversed()))
            } catch (e: Exception) {
                logger.error("Error getting recent trades", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<List<RecentTrade>>(success = false, error = e.message))
            }
        }
        
        get("/charts/{symbol}/funding") {
            try {
                val symbol = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol required")
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 24
                
                val basePrice = when (symbol) {
                    "BTC-PERP" -> 50000.0
                    "ETH-PERP" -> 3000.0
                    "SOL-PERP" -> 150.0
                    else -> 100.0
                }
                
                // Generate mock funding history (8-hour intervals)
                val fundingHistory = (0 until limit).map { i ->
                    val timestamp = System.currentTimeMillis() - (i * 8 * 3600 * 1000)
                    val fundingRate = (Math.random() - 0.5) * 0.002 // -0.1% to +0.1%
                    val markPrice = basePrice * (1 + (Math.random() - 0.5) * 0.1)
                    
                    FundingHistory(
                        symbol = symbol,
                        fundingRate = fundingRate,
                        fundingTime = timestamp,
                        markPrice = markPrice
                    )
                }.reversed()
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = fundingHistory))
            } catch (e: Exception) {
                logger.error("Error getting funding history", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<List<FundingHistory>>(success = false, error = e.message))
            }
        }
        
        // =================================================================================================
        // TRADE EVENTS (WEBSOCKET-LIKE ENDPOINT)
        // =================================================================================================
        
        get("/events") {
            try {
                // Get recent trade events
                val events = tradingService.tradeFlow
                    .take(10)
                    .toList()
                    .map { event ->
                        TradeEvent(
                            exchange = event.exchange.name,
                            symbol = event.symbol,
                            action = event.action,
                            price = event.price,
                            amount = event.amount,
                            pnl = event.pnl,
                            success = event.success,
                            timestamp = event.timestamp,
                            metadata = event.metadata.mapValues { it.value.toString() }
                        )
                    }
                
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = events))
            } catch (e: Exception) {
                logger.error("Error getting trade events", e)
                call.respond(HttpStatusCode.InternalServerError, 
                    ApiResponse<List<TradeEvent>>(success = false, error = e.message))
            }
        }
    }
}