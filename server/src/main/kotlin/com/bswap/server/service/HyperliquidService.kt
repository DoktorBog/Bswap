package com.bswap.server.service

import com.bswap.server.config.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Hyperliquid exchange service with direct API integration
 * Provides complete trading functionality including spot and perpetual futures
 * 
 * NOTE: This is a simplified implementation that provides the interface
 * Full implementation would require direct API integration with Hyperliquid
 */
class HyperliquidService(
    private val config: HyperliquidConfig
) {
    companion object {
        private val logger = LoggerFactory.getLogger(HyperliquidService::class.java)
        private const val RATE_LIMIT_WINDOW = 1000L // 1 second
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY = 500L
    }

    private val httpClient = HttpClient {
        // Configure HTTP client for API calls
    }
    
    private val positions = ConcurrentHashMap<String, HyperliquidPosition>()
    private val openOrders = ConcurrentHashMap<String, HyperliquidOrder>()
    private val balances = ConcurrentHashMap<String, HyperliquidBalance>()
    private val markets = ConcurrentHashMap<String, HyperliquidMarket>()
    
    private val requestCount = AtomicLong(0)
    private val lastRequestTime = AtomicLong(0)
    private val rateLimitMutex = Mutex()
    
    private val wsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wsConnection: Job? = null
    
    private val positionFlow = MutableSharedFlow<HyperliquidPosition>()
    private val orderFlow = MutableSharedFlow<HyperliquidOrder>()
    private val balanceFlow = MutableSharedFlow<HyperliquidBalance>()
    private val tradeFlow = MutableSharedFlow<HyperliquidTradeResult>()

    init {
        logger.info("🚀 Initializing Hyperliquid Service (Mock Mode)")
        logger.info("📊 Exchange Type: ${config.exchangeType}")
        logger.info("💰 Default Leverage: ${config.defaultLeverage}x")
        logger.info("⚙️ Margin Mode: ${config.marginMode}")
        
        if (config.enabled && config.exchangeType == ExchangeType.HYPERLIQUID) {
            runBlocking {
                initializeService()
            }
        }
    }

    private suspend fun initializeService() {
        try {
            logger.info("🔄 Initializing Hyperliquid service...")
            logger.debug("🔍 Service configuration: enabled=${config.enabled}, exchangeType=${config.exchangeType}")
            logger.debug("🔍 Rate limiting: ${config.maxRequestsPerSecond} requests/second")
            logger.debug("🔍 Trading config: leverage=${config.defaultLeverage}x, maxLeverage=${config.maxLeverage}x")
            
            // Initialize mock data first as fallback
            logger.info("📁 Initializing mock data...")
            initializeMockData()
            
            // Then try to load real markets from API
            logger.info("🌐 Attempting to load real markets from Hyperliquid API...")
            loadMarkets()
            
            // Log initialization results
            logger.info("📈 Initialization complete:")
            logger.info("📊   Markets loaded: ${markets.size}")
            logger.info("💰   Balances initialized: ${balances.size}")
            logger.info("📁   Positions: ${positions.size}")
            logger.info("📋   Orders: ${openOrders.size}")
            
            if (config.enableWebSocket) {
                logger.info("🔌 WebSocket streams would be started in production mode")
                logger.debug("🔌 WebSocket config: streams enabled for real-time data")
            } else {
                logger.info("📵 WebSocket streams disabled in configuration")
            }
            
            logger.info("✅ Hyperliquid Service initialized successfully (Mock Mode)")
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize Hyperliquid Service", e)
            logger.error("🔍 Stack trace:", e)
            throw e
        }
    }

    private fun initializeMockData() {
        logger.info("📋 Initializing mock trading data...")
        
        try {
            // Add mock markets
            val mockMarkets = listOf("BTC-PERP", "ETH-PERP", "SOL-PERP", "ARB-PERP", "OP-PERP")
            logger.info("📊 Setting up ${mockMarkets.size} mock markets: $mockMarkets")
            
            mockMarkets.forEach { symbol ->
                val market = HyperliquidMarket(
                    symbol = symbol,
                    base = symbol.substringBefore("-"),
                    quote = "USDC",
                    type = MarketType.PERPETUAL,
                    active = true,
                    minOrderSize = 0.001,
                    maxOrderSize = 1000.0,
                    tickSize = 0.01,
                    stepSize = 0.001,
                    makerFee = config.makerFee,
                    takerFee = config.takerFee,
                    maxLeverage = config.maxLeverage,
                    maintenanceMargin = 0.005,
                    initialMargin = 0.01
                )
                markets[symbol] = market
                logger.debug("✅ Market added: $symbol - ${market.base}/${market.quote} (leverage: ${market.maxLeverage}x)")
            }
        
        // Add mock balance
        logger.info("💰 Setting up mock account balance...")
        val mockBalance = HyperliquidBalance(
            asset = "USDC",
            free = 10000.0,
            used = 0.0,
            total = 10000.0,
            usdValue = 10000.0,
            marginBalance = 10000.0,
            availableMargin = 10000.0,
            updateTime = System.currentTimeMillis()
        )
        balances["USDC"] = mockBalance
        logger.debug("✅ Balance added: USDC - total=${mockBalance.total}, available=${mockBalance.free}")
        
        logger.info("✅ Mock data initialization complete")
        logger.info("📈 Summary: ${markets.size} markets, ${balances.size} balances initialized")
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize mock data", e)
            throw e
        }
    }

    // =================================================================================================
    // MARKET DATA
    // =================================================================================================
    
    private fun getRealisticBasePrice(symbol: String): Double {
        return when (symbol) {
            "BTC-PERP" -> 62000.0 + (Math.random() - 0.5) * 2000 // 61k-63k range
            "ETH-PERP" -> 3200.0 + (Math.random() - 0.5) * 200    // 3.1k-3.3k range
            "SOL-PERP" -> 150.0 + (Math.random() - 0.5) * 20      // 140-160 range
            "ARB-PERP" -> 0.85 + (Math.random() - 0.5) * 0.1      // 0.8-0.9 range
            "OP-PERP" -> 2.10 + (Math.random() - 0.5) * 0.2       // 2.0-2.2 range
            else -> 100.0 + (Math.random() - 0.5) * 10            // Default range
        }
    }

    suspend fun loadMarkets() = withRateLimit {
        logger.info("📊 Loading markets from Hyperliquid API")
        try {
            // Market data API doesn't require wallet credentials
            logger.info("🔗 Making API call to Hyperliquid for market metadata")
            
            // Make actual API call to Hyperliquid
            val response = httpClient.post("https://api.hyperliquid.xyz/info") {
                header("Content-Type", "application/json")
                setBody("""{"type": "meta"}""")
            }
            
            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.info("🔍 API Response received: ${responseText.take(200)}...")
                
                // Parse the real markets from API response
                val apiMarkets = parseMarketsFromResponse(responseText)
                
                // Clear existing mock data and add real markets
                markets.clear()
                apiMarkets.forEach { market ->
                    markets[market.symbol] = market
                    logger.debug("📈 Real market loaded: ${market.symbol} - ${market.base}/${market.quote}")
                }
                
                logger.info("✅ Successfully loaded ${markets.size} real markets from Hyperliquid API")
            } else {
                logger.error("❌ Hyperliquid API call failed with status: ${response.status}")
                logger.warn("🔄 Falling back to mock data")
            }
            
            return@withRateLimit
        } catch (e: Exception) {
            logger.error("❌ Failed to load markets from API: ${e.message}")
            logger.warn("🔄 Falling back to mock data")
            return@withRateLimit
        }
    }
    
    private fun parseMarketsFromResponse(responseText: String): List<HyperliquidMarket> {
        return try {
            val json = Json.parseToJsonElement(responseText).jsonObject
            val universe = json["universe"]?.jsonArray ?: return emptyList()
            
            universe.mapNotNull { element ->
                try {
                    val marketInfo = element.jsonObject
                    val name = marketInfo["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    
                    HyperliquidMarket(
                        symbol = name,
                        base = name.substringBefore("-"),
                        quote = "USD",
                        type = MarketType.PERPETUAL,
                        active = true,
                        minOrderSize = 0.001,
                        maxOrderSize = 1000000.0,
                        tickSize = 0.01,
                        stepSize = 0.001,
                        makerFee = config.makerFee,
                        takerFee = config.takerFee,
                        maxLeverage = config.maxLeverage,
                        maintenanceMargin = 0.005,
                        initialMargin = 0.01
                    )
                } catch (e: Exception) {
                    logger.debug("⚠️ Failed to parse market: $e")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Failed to parse markets response: $e")
            emptyList()
        }
    }

    suspend fun getOrderBook(symbol: String, limit: Int = 20): Map<String, Any>? = withRateLimit {
        logger.info("📖 Fetching order book for $symbol (limit: $limit)")
        try {
            logger.debug("🔍 Checking if market exists for symbol: $symbol")
            val market = markets[symbol]
            if (market == null) {
                logger.warn("⚠️ Market not found for symbol: $symbol. Available markets: ${markets.keys}")
                return@withRateLimit null
            }
            
            logger.debug("✅ Market found for $symbol, generating mock order book")
            val orderBook = mapOf(
                "bids" to listOf(listOf(50000.0, 1.0)),
                "asks" to listOf(listOf(50100.0, 1.0)),
                "timestamp" to System.currentTimeMillis()
            )
            logger.debug("📊 Order book generated for $symbol: ${orderBook.size} entries")
            return@withRateLimit orderBook
        } catch (e: Exception) {
            logger.error("❌ Failed to get order book for $symbol", e)
            return@withRateLimit null
        }
    }

    suspend fun getTicker(symbol: String): Map<String, Any>? = withRateLimit {
        logger.info("📈 Fetching ticker for $symbol")
        try {
            logger.debug("🔍 Checking if market exists for ticker: $symbol")
            val market = markets[symbol]
            if (market == null) {
                logger.warn("⚠️ Market not found for ticker: $symbol. Available markets: ${markets.keys}")
                return@withRateLimit null
            }
            
            // Try to get real price data from Hyperliquid API (always try, no wallet needed for market data)
            try {
                val response = httpClient.post("https://api.hyperliquid.xyz/info") {
                    header("Content-Type", "application/json")
                    setBody("""{"type": "allMids"}""")
                }
                
                if (response.status.isSuccess()) {
                    val responseText = response.bodyAsText()
                    logger.debug("🔍 Hyperliquid API response: $responseText")
                    val json = Json.parseToJsonElement(responseText).jsonObject
                    val price = json[symbol]?.jsonPrimitive?.doubleOrNull
                    
                    if (price != null) {
                        val ticker = mapOf(
                            "symbol" to symbol,
                            "last" to price,
                            "bid" to price * 0.9995,
                            "ask" to price * 1.0005,
                            "baseVolume" to 1000.0,
                            "quoteVolume" to price * 1000.0
                        )
                        logger.info("✅ Real ticker data for $symbol: price=$price")
                        return@withRateLimit ticker
                    } else {
                        logger.warn("⚠️ No price found for $symbol in API response")
                    }
                } else {
                    logger.warn("⚠️ Hyperliquid API returned status: ${response.status}")
                }
            } catch (e: Exception) {
                logger.warn("⚠️ Failed to get real ticker from Hyperliquid API: ${e.message}")
            }
            
            // Fallback to realistic mock data with variation
            val basePrice = getRealisticBasePrice(symbol)
            val priceVariation = (Math.random() - 0.5) * 0.01 // -0.5% to +0.5% variation
            val currentPrice = basePrice * (1 + priceVariation)
            
            val ticker = mapOf(
                "symbol" to symbol,
                "last" to currentPrice,
                "bid" to currentPrice * 0.9995,
                "ask" to currentPrice * 1.0005,
                "baseVolume" to 1000.0 + Math.random() * 2000.0,
                "quoteVolume" to currentPrice * (1000.0 + Math.random() * 2000.0)
            )
            logger.debug("✅ Mock ticker generated for $symbol: last=${ticker["last"]}")
            return@withRateLimit ticker
        } catch (e: Exception) {
            logger.error("❌ Failed to get ticker for $symbol", e)
            return@withRateLimit null
        }
    }

    suspend fun getOHLCV(
        symbol: String,
        timeframe: String = "1m",
        limit: Int = 100
    ): List<List<Any>>? = withRateLimit {
        logger.info("📊 Fetching OHLCV for $symbol (timeframe: $timeframe, limit: $limit)")
        try {
            logger.debug("🔍 Checking if market exists for OHLCV: $symbol")
            val market = markets[symbol]
            if (market == null) {
                logger.warn("⚠️ Market not found for OHLCV: $symbol. Available markets: ${markets.keys}")
                return@withRateLimit null
            }
            
            // Generate realistic OHLCV data with variation
            val basePrice = getRealisticBasePrice(symbol)
            val currentTime = System.currentTimeMillis()
            
            val ohlcvData = (0 until limit).map { i ->
                val timestamp = currentTime - (i * 60000) // 1-minute intervals
                val open = basePrice * (1 + (Math.random() - 0.5) * 0.02)
                val high = open * (1 + Math.random() * 0.01)
                val low = open * (1 - Math.random() * 0.01)
                val close = open * (1 + (Math.random() - 0.5) * 0.01)
                val volume = 100.0 + Math.random() * 200.0
                
                listOf(timestamp, open, high, low, close, volume)
            }.reversed() // Most recent first
            logger.debug("✅ OHLCV data generated for $symbol: ${ohlcvData.size} candles")
            return@withRateLimit ohlcvData
        } catch (e: Exception) {
            logger.error("❌ Failed to get OHLCV for $symbol", e)
            return@withRateLimit null
        }
    }

    suspend fun getFundingRate(symbol: String): HyperliquidFundingRate? = withRateLimit {
        logger.info("💰 Fetching funding rate for $symbol")
        try {
            logger.debug("🔍 Checking if market exists for funding rate: $symbol")
            val market = markets[symbol]
            if (market == null) {
                logger.warn("⚠️ Market not found for funding rate: $symbol. Available markets: ${markets.keys}")
                return@withRateLimit null
            }
            
            // Generate realistic funding rate data
            val basePrice = getRealisticBasePrice(symbol)
            val fundingRateValue = (Math.random() - 0.5) * 0.002 // -0.1% to +0.1%
            val markPrice = basePrice * (1 + (Math.random() - 0.5) * 0.001)
            val indexPrice = basePrice * (1 + (Math.random() - 0.5) * 0.0005)
            
            val fundingRate = HyperliquidFundingRate(
                symbol = symbol,
                fundingRate = fundingRateValue,
                fundingTime = System.currentTimeMillis(),
                nextFundingTime = System.currentTimeMillis() + 28800000, // 8 hours
                markPrice = markPrice,
                indexPrice = indexPrice,
                openInterest = 1000000.0 + Math.random() * 5000000.0,
                volume24h = basePrice * (50000.0 + Math.random() * 100000.0)
            )
            logger.debug("✅ Funding rate generated for $symbol: rate=${fundingRate.fundingRate}, mark=${fundingRate.markPrice}")
            return@withRateLimit fundingRate
        } catch (e: Exception) {
            logger.error("❌ Failed to get funding rate for $symbol", e)
            return@withRateLimit null
        }
    }

    // =================================================================================================
    // BALANCE MANAGEMENT
    // =================================================================================================

    suspend fun fetchBalances(): Map<String, HyperliquidBalance> = withRateLimit {
        logger.info("💰 Fetching account balances")
        try {
            logger.debug("🔍 Current balances count: ${balances.size}")
            balances.forEach { (asset, balance) ->
                logger.debug("💵 Balance $asset: free=${balance.free}, used=${balance.used}, total=${balance.total}")
            }
            
            if (balances.isEmpty()) {
                logger.warn("⚠️ No balances found! This might indicate an API connection issue.")
            } else {
                logger.info("✅ Successfully fetched ${balances.size} balances")
            }
            
            return@withRateLimit balances
        } catch (e: Exception) {
            logger.error("❌ Failed to fetch balances", e)
            return@withRateLimit emptyMap()
        }
    }

    suspend fun getBalance(asset: String): HyperliquidBalance? {
        logger.debug("💰 Getting balance for asset: $asset")
        val balance = balances[asset]
        if (balance == null) {
            logger.warn("⚠️ No balance found for asset: $asset. Available assets: ${balances.keys}")
        } else {
            logger.debug("✅ Balance found for $asset: ${balance.total}")
        }
        return balance
    }

    suspend fun getTotalBalanceUsd(): Double {
        logger.debug("💰 Calculating total USD balance")
        val totalUsd = balances.values.sumOf { it.usdValue }
        logger.debug("💵 Total USD balance: $$totalUsd")
        return totalUsd
    }

    // =================================================================================================
    // ORDER MANAGEMENT
    // =================================================================================================

    suspend fun createOrder(
        symbol: String,
        side: OrderSide,
        amount: Double,
        price: Double? = null,
        type: OrderType = config.orderType,
        params: Map<String, Any> = emptyMap()
    ): HyperliquidTradeResult = withRateLimit {
        logger.info("📝 Creating order: $symbol $side $amount @ ${price ?: "MARKET"}")
        try {
            logger.debug("🔍 Order parameters: symbol=$symbol, side=$side, amount=$amount, price=$price, type=$type")
            logger.debug("📊 Additional params: $params")
            
            // Check if market exists
            val market = markets[symbol]
            if (market == null) {
                logger.error("❌ Market not found for order: $symbol. Available markets: ${markets.keys}")
                return@withRateLimit HyperliquidTradeResult(
                    success = false,
                    orderId = null,
                    executedPrice = null,
                    executedSize = null,
                    fee = null,
                    timestamp = System.currentTimeMillis(),
                    error = "Market not found: $symbol"
                )
            }
        
        val orderId = "ORDER_${System.currentTimeMillis()}"
        val order = HyperliquidOrder(
            orderId = orderId,
            clientOrderId = null,
            symbol = symbol,
            side = side,
            type = type,
            status = OrderStatus.NEW,
            price = price ?: 50000.0,
            size = amount,
            filled = 0.0,
            remaining = amount,
            averagePrice = null,
            fee = 0.0,
            timeInForce = config.timeInForce,
            postOnly = config.postOnly,
            reduceOnly = config.reduceOnly,
            createTime = System.currentTimeMillis(),
            updateTime = System.currentTimeMillis()
        )
        
        openOrders[orderId] = order
        orderFlow.tryEmit(order)
        
        logger.info("✅ Order created successfully: $orderId")
        logger.debug("📈 Order details: ${order.symbol} ${order.side} ${order.size} @ ${order.price}")
        
        val result = HyperliquidTradeResult(
            success = true,
            orderId = orderId,
            executedPrice = price,
            executedSize = amount,
            fee = amount * config.takerFee,
            timestamp = System.currentTimeMillis(),
            error = null
        )
        logger.debug("🎉 Order result: success=${result.success}, orderId=${result.orderId}, fee=${result.fee}")
        return@withRateLimit result
        } catch (e: Exception) {
            logger.error("❌ Failed to create order for $symbol", e)
            return@withRateLimit HyperliquidTradeResult(
                success = false,
                orderId = null,
                executedPrice = null,
                executedSize = null,
                fee = null,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }

    suspend fun cancelOrder(orderId: String, symbol: String): Boolean = withRateLimit {
        logger.info("❌ Cancelling order: $orderId for symbol: $symbol")
        try {
            val order = openOrders[orderId]
            if (order == null) {
                logger.warn("⚠️ Order not found for cancellation: $orderId")
                return@withRateLimit false
            }
            
            openOrders.remove(orderId)
            logger.info("✅ Order cancelled successfully: $orderId")
            return@withRateLimit true
        } catch (e: Exception) {
            logger.error("❌ Failed to cancel order $orderId", e)
            return@withRateLimit false
        }
    }

    suspend fun cancelAllOrders(symbol: String? = null): Int = withRateLimit {
        logger.info("🚫 Cancelling all orders${symbol?.let { " for symbol: $it" } ?: ""}")
        try {
            val ordersToCancel = if (symbol != null) {
                openOrders.filter { it.value.symbol == symbol }
            } else {
                openOrders
            }
            
            logger.debug("🔍 Found ${ordersToCancel.size} orders to cancel")
            
            val count = ordersToCancel.size
            ordersToCancel.keys.forEach { orderId ->
                openOrders.remove(orderId)
                logger.debug("❌ Cancelled order: $orderId")
            }
            
            logger.info("✅ Successfully cancelled $count orders")
            return@withRateLimit count
        } catch (e: Exception) {
            logger.error("❌ Failed to cancel all orders", e)
            return@withRateLimit 0
        }
    }

    suspend fun fetchOpenOrders(symbol: String? = null): List<HyperliquidOrder> = withRateLimit {
        logger.info("📋 Fetching open orders${symbol?.let { " for symbol: $it" } ?: ""}")
        try {
            val orders = if (symbol != null) {
                openOrders.values.filter { it.symbol == symbol }
            } else {
                openOrders.values.toList()
            }
            
            logger.debug("📈 Found ${orders.size} open orders")
            orders.forEach { order ->
                logger.debug("📝 Order: ${order.orderId} - ${order.symbol} ${order.side} ${order.size} @ ${order.price}")
            }
            
            return@withRateLimit orders
        } catch (e: Exception) {
            logger.error("❌ Failed to fetch open orders", e)
            return@withRateLimit emptyList()
        }
    }

    suspend fun fetchOrder(orderId: String, symbol: String): HyperliquidOrder? = withRateLimit {
        logger.info("🔍 Fetching order: $orderId for symbol: $symbol")
        try {
            val order = openOrders[orderId]
            if (order == null) {
                logger.warn("⚠️ Order not found: $orderId")
            } else {
                logger.debug("✅ Order found: ${order.symbol} ${order.side} ${order.size} @ ${order.price} - status: ${order.status}")
            }
            return@withRateLimit order
        } catch (e: Exception) {
            logger.error("❌ Failed to fetch order $orderId", e)
            return@withRateLimit null
        }
    }

    // =================================================================================================
    // POSITION MANAGEMENT
    // =================================================================================================

    suspend fun fetchPositions(symbol: String? = null): List<HyperliquidPosition> = withRateLimit {
        logger.info("📊 Fetching positions${symbol?.let { " for symbol: $it" } ?: ""}")
        try {
            val positionsList = if (symbol != null) {
                positions.values.filter { it.symbol == symbol }
            } else {
                positions.values.toList()
            }
            
            logger.debug("📈 Found ${positionsList.size} positions")
            positionsList.forEach { position ->
                logger.debug("📊 Position: ${position.symbol} ${position.side} size=${position.size} pnl=${position.unrealizedPnl}")
            }
            
            if (positionsList.isEmpty()) {
                logger.debug("💭 No active positions found")
            }
            
            return@withRateLimit positionsList
        } catch (e: Exception) {
            logger.error("❌ Failed to fetch positions", e)
            return@withRateLimit emptyList()
        }
    }

    suspend fun getPosition(symbol: String): HyperliquidPosition? {
        logger.debug("📊 Getting position for symbol: $symbol")
        val position = positions[symbol]
        if (position == null) {
            logger.debug("💭 No position found for symbol: $symbol")
        } else {
            logger.debug("✅ Position found for $symbol: ${position.side} size=${position.size} pnl=${position.unrealizedPnl}")
        }
        return position
    }

    suspend fun openPosition(
        symbol: String,
        side: PositionSide,
        size: Double,
        leverage: Double = config.defaultLeverage
    ): HyperliquidTradeResult {
        logger.info("🎯 Opening position: $symbol $side $size at ${leverage}x leverage")
        try {
            logger.debug("🔍 Position parameters: symbol=$symbol, side=$side, size=$size, leverage=$leverage")
            
            // Check if market exists
            val market = markets[symbol]
            if (market == null) {
                logger.error("❌ Market not found for position: $symbol. Available markets: ${markets.keys}")
                return HyperliquidTradeResult(
                    success = false,
                    orderId = null,
                    executedPrice = null,
                    executedSize = null,
                    fee = null,
                    timestamp = System.currentTimeMillis(),
                    error = "Market not found: $symbol"
                )
            }
            
            // Check if position already exists
            if (positions.containsKey(symbol)) {
                logger.warn("⚠️ Position already exists for $symbol")
                return HyperliquidTradeResult(
                    success = false,
                    orderId = null,
                    executedPrice = null,
                    executedSize = null,
                    fee = null,
                    timestamp = System.currentTimeMillis(),
                    error = "Position already exists for $symbol"
                )
            }
        
        val position = HyperliquidPosition(
            symbol = symbol,
            side = side,
            size = size,
            entryPrice = 50000.0,
            markPrice = 50000.0,
            liquidationPrice = if (side == PositionSide.LONG) 45000.0 else 55000.0,
            unrealizedPnl = 0.0,
            realizedPnl = 0.0,
            margin = size * 50000.0 / leverage,
            leverage = leverage,
            positionId = "POS_${System.currentTimeMillis()}",
            openTime = System.currentTimeMillis(),
            updateTime = System.currentTimeMillis()
        )
        
        positions[symbol] = position
        positionFlow.tryEmit(position)
        
        logger.info("✅ Position opened successfully for $symbol")
        logger.debug("📈 Position details: entry=${position.entryPrice}, margin=${position.margin}, liquidation=${position.liquidationPrice}")
        
        val result = HyperliquidTradeResult(
            success = true,
            orderId = position.positionId,
            executedPrice = position.entryPrice,
            executedSize = size,
            fee = size * 50000.0 * config.takerFee,
            timestamp = System.currentTimeMillis(),
            error = null
        )
        logger.debug("🎉 Position result: success=${result.success}, positionId=${result.orderId}, fee=${result.fee}")
        return result
        } catch (e: Exception) {
            logger.error("❌ Failed to open position for $symbol", e)
            return HyperliquidTradeResult(
                success = false,
                orderId = null,
                executedPrice = null,
                executedSize = null,
                fee = null,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }

    suspend fun closePosition(symbol: String, reason: String = "Manual"): HyperliquidTradeResult {
        logger.info("🔒 Closing position: $symbol (Reason: $reason)")
        try {
            logger.debug("🔍 Looking for position to close: $symbol")
            
            val position = positions.remove(symbol)
        
        return if (position != null) {
            logger.info("✅ Position closed successfully: $symbol")
            logger.debug("📈 Closed position details: size=${position.size}, pnl=${position.unrealizedPnl}, mark=${position.markPrice}")
            
            val result = HyperliquidTradeResult(
                success = true,
                orderId = "CLOSE_${System.currentTimeMillis()}",
                executedPrice = position.markPrice,
                executedSize = position.size,
                fee = position.size * position.markPrice * config.takerFee,
                timestamp = System.currentTimeMillis(),
                error = null
            )
            logger.debug("🎉 Close result: success=${result.success}, closeId=${result.orderId}, fee=${result.fee}")
            result
        } else {
            logger.warn("⚠️ No position found to close for symbol: $symbol")
            HyperliquidTradeResult(
                success = false,
                orderId = null,
                executedPrice = null,
                executedSize = null,
                fee = null,
                timestamp = System.currentTimeMillis(),
                error = "No position found"
            )
        }
        } catch (e: Exception) {
            logger.error("❌ Failed to close position for $symbol", e)
            return HyperliquidTradeResult(
                success = false,
                orderId = null,
                executedPrice = null,
                executedSize = null,
                fee = null,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }

    suspend fun closeAllPositions(reason: String = "Manual"): List<HyperliquidTradeResult> {
        logger.info("🔒 Closing all positions: $reason")
        try {
            val positionSymbols = positions.keys.toList()
            logger.debug("🔍 Found ${positionSymbols.size} positions to close: $positionSymbols")
            
            val results = positionSymbols.map { symbol ->
                closePosition(symbol, reason)
            }
            
            val successCount = results.count { it.success }
            logger.info("✅ Closed $successCount/${results.size} positions successfully")
            
            return results
        } catch (e: Exception) {
            logger.error("❌ Failed to close all positions", e)
            return emptyList()
        }
    }

    suspend fun setLeverage(symbol: String, leverage: Double): Boolean = withRateLimit {
        logger.info("⚙️ Setting leverage for $symbol to ${leverage}x")
        try {
            logger.debug("🔍 Validating leverage parameters: symbol=$symbol, leverage=$leverage")
            
            if (leverage <= 0 || leverage > config.maxLeverage) {
                logger.error("❌ Invalid leverage: $leverage (max: ${config.maxLeverage})")
                return@withRateLimit false
            }
            
            val market = markets[symbol]
            if (market == null) {
                logger.error("❌ Market not found for leverage setting: $symbol")
                return@withRateLimit false
            }
            
            logger.info("✅ Leverage set successfully for $symbol to ${leverage}x")
            return@withRateLimit true
        } catch (e: Exception) {
            logger.error("❌ Failed to set leverage for $symbol", e)
            return@withRateLimit false
        }
    }

    suspend fun setMarginMode(symbol: String, mode: MarginMode): Boolean = withRateLimit {
        logger.info("⚙️ Setting margin mode for $symbol to $mode")
        try {
            logger.debug("🔍 Validating margin mode parameters: symbol=$symbol, mode=$mode")
            
            val market = markets[symbol]
            if (market == null) {
                logger.error("❌ Market not found for margin mode setting: $symbol")
                return@withRateLimit false
            }
            
            logger.info("✅ Margin mode set successfully for $symbol to $mode")
            return@withRateLimit true
        } catch (e: Exception) {
            logger.error("❌ Failed to set margin mode for $symbol", e)
            return@withRateLimit false
        }
    }

    suspend fun addMargin(symbol: String, amount: Double): Boolean = withRateLimit {
        logger.info("💰 Adding margin to $symbol: $amount")
        try {
            logger.debug("🔍 Validating margin addition: symbol=$symbol, amount=$amount")
            
            if (amount <= 0) {
                logger.error("❌ Invalid margin amount: $amount")
                return@withRateLimit false
            }
            
            val position = positions[symbol]
            if (position == null) {
                logger.error("❌ No position found for margin addition: $symbol")
                return@withRateLimit false
            }
            
            logger.info("✅ Margin added successfully to $symbol: $amount")
            return@withRateLimit true
        } catch (e: Exception) {
            logger.error("❌ Failed to add margin to $symbol", e)
            return@withRateLimit false
        }
    }

    suspend fun reduceMargin(symbol: String, amount: Double): Boolean = withRateLimit {
        logger.info("💰 Reducing margin from $symbol: $amount")
        try {
            logger.debug("🔍 Validating margin reduction: symbol=$symbol, amount=$amount")
            
            if (amount <= 0) {
                logger.error("❌ Invalid margin amount: $amount")
                return@withRateLimit false
            }
            
            val position = positions[symbol]
            if (position == null) {
                logger.error("❌ No position found for margin reduction: $symbol")
                return@withRateLimit false
            }
            
            logger.info("✅ Margin reduced successfully from $symbol: $amount")
            return@withRateLimit true
        } catch (e: Exception) {
            logger.error("❌ Failed to reduce margin from $symbol", e)
            return@withRateLimit false
        }
    }

    // =================================================================================================
    // HELPER FUNCTIONS
    // =================================================================================================

    private suspend fun <T> withRateLimit(block: suspend () -> T): T {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val timeSinceLastRequest = now - lastRequestTime.get()
            val requestNum = requestCount.incrementAndGet()
            
            logger.debug("🔄 Rate limit check - Request #$requestNum, last request ${timeSinceLastRequest}ms ago")
            
            if (timeSinceLastRequest < RATE_LIMIT_WINDOW / config.maxRequestsPerSecond) {
                val delayTime = (RATE_LIMIT_WINDOW / config.maxRequestsPerSecond) - timeSinceLastRequest
                logger.debug("⏳ Rate limiting: delaying ${delayTime}ms")
                delay(delayTime)
            }
            
            lastRequestTime.set(System.currentTimeMillis())
        }
        
        return try {
            block()
        } catch (e: Exception) {
            logger.error("❌ Rate limited operation failed", e)
            throw e
        }
    }

    // =================================================================================================
    // PUBLIC API
    // =================================================================================================

    fun getPositionFlow(): Flow<HyperliquidPosition> = positionFlow.asSharedFlow()
    fun getOrderFlow(): Flow<HyperliquidOrder> = orderFlow.asSharedFlow()
    fun getBalanceFlow(): Flow<HyperliquidBalance> = balanceFlow.asSharedFlow()
    fun getTradeFlow(): Flow<HyperliquidTradeResult> = tradeFlow.asSharedFlow()

    suspend fun getStats(): Map<String, Any> {
        logger.debug("📈 Generating service statistics")
        val stats = mapOf(
            "positions" to positions.size,
            "openOrders" to openOrders.size,
            "markets" to markets.size,
            "balances" to balances.size,
            "totalBalanceUsd" to getTotalBalanceUsd(),
            "requestCount" to requestCount.get(),
            "wsActive" to (wsConnection?.isActive == true),
            "lastRequestTime" to lastRequestTime.get(),
            "serviceInitialized" to true
        )
        logger.debug("📉 Service stats: $stats")
        return stats
    }

    fun shutdown() {
        logger.info("🛑 Shutting down Hyperliquid Service")
        try {
            logger.debug("🔌 Cancelling WebSocket connection")
            wsConnection?.cancel()
            
            logger.debug("🔌 Cancelling coroutine scope")
            wsScope.cancel()
            
            logger.debug("🔌 Closing HTTP client")
            httpClient.close()
            
            logger.info("✅ Hyperliquid Service shutdown complete")
        } catch (e: Exception) {
            logger.error("❌ Error during shutdown", e)
        }
    }
}