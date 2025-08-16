package com.bswap.shared.trading

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

class TradingApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://192.168.0.152:9090"
) {
    
    // =================================================================================================
    // GENERAL STATUS AND CONTROL
    // =================================================================================================
    
    suspend fun getStatus(): Result<TradingStats> = try {
        val response = httpClient.get("$baseUrl/api/trading/status")
        val apiResponse: ApiResponse<TradingStats> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun startTrading(): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/start")
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun stopTrading(): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/stop")
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun emergencyStop(reason: String = "Emergency stop from UI"): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/emergency-stop") {
            setBody(reason)
            contentType(ContentType.Text.Plain)
        }
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // =================================================================================================
    // EXCHANGE MANAGEMENT
    // =================================================================================================
    
    suspend fun getCurrentExchange(): Result<String> = try {
        val response = httpClient.get("$baseUrl/api/trading/exchange")
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun switchExchange(exchangeType: String): Result<String> = try {
        val request = SwitchExchangeRequest(exchangeType)
        val response = httpClient.post("$baseUrl/api/trading/exchange/switch") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // =================================================================================================
    // BALANCE MANAGEMENT
    // =================================================================================================
    
    suspend fun getBalances(): Result<List<Balance>> = try {
        val response = httpClient.get("$baseUrl/api/trading/balance")
        val apiResponse: ApiResponse<List<Balance>> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // =================================================================================================
    // POSITION MANAGEMENT
    // =================================================================================================
    
    suspend fun getPositions(): Result<List<Position>> = try {
        val response = httpClient.get("$baseUrl/api/trading/positions")
        val apiResponse: ApiResponse<List<Position>> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun openPosition(request: OpenPositionRequest): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/positions/open") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun closePosition(request: ClosePositionRequest): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/positions/close") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun closeAllPositions(): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/positions/close-all")
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun setLeverage(request: SetLeverageRequest): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/positions/leverage") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun addMargin(request: MarginRequest): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/positions/margin/add") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun reduceMargin(request: MarginRequest): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/positions/margin/reduce") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // =================================================================================================
    // ORDER MANAGEMENT
    // =================================================================================================
    
    suspend fun getOrders(): Result<List<Order>> = try {
        val response = httpClient.get("$baseUrl/api/trading/orders")
        val apiResponse: ApiResponse<List<Order>> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun createOrder(request: CreateOrderRequest): Result<String> = try {
        val response = httpClient.post("$baseUrl/api/trading/orders") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun cancelOrder(orderId: String): Result<String> = try {
        val response = httpClient.delete("$baseUrl/api/trading/orders/$orderId")
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun cancelAllOrders(): Result<String> = try {
        val response = httpClient.delete("$baseUrl/api/trading/orders")
        val apiResponse: ApiResponse<String> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // =================================================================================================
    // MARKET DATA
    // =================================================================================================
    
    suspend fun getMarkets(): Result<List<MarketData>> = try {
        val response = httpClient.get("$baseUrl/api/trading/markets")
        val apiResponse: ApiResponse<List<MarketData>> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    suspend fun getMarketData(symbol: String): Result<MarketData> = try {
        val response = httpClient.get("$baseUrl/api/trading/markets/$symbol")
        val apiResponse: ApiResponse<MarketData> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // =================================================================================================
    // TRADE EVENTS
    // =================================================================================================
    
    suspend fun getTradeEvents(): Result<List<TradeEvent>> = try {
        val response = httpClient.get("$baseUrl/api/trading/events")
        val apiResponse: ApiResponse<List<TradeEvent>> = response.body()
        if (apiResponse.success && apiResponse.data != null) {
            Result.success(apiResponse.data)
        } else {
            Result.failure(Exception(apiResponse.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // =================================================================================================
    // CHART AND MARKET DATA
    // =================================================================================================
    
    suspend fun getCandlestickData(symbol: String, timeframe: String = "1h", limit: Int = 100): Result<TradingViewData> {
        return try {
            val response = httpClient.get("$baseUrl/api/trading/charts/$symbol/candlesticks") {
                parameter("timeframe", timeframe)
                parameter("limit", limit)
            }
            val apiResponse = response.body<ApiResponse<TradingViewData>>()
            if (apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to get candlestick data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getOrderBook(symbol: String, depth: Int = 20): Result<OrderBook> {
        return try {
            val response = httpClient.get("$baseUrl/api/trading/charts/$symbol/orderbook") {
                parameter("depth", depth)
            }
            val apiResponse = response.body<ApiResponse<OrderBook>>()
            if (apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to get order book"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getRecentTrades(symbol: String, limit: Int = 50): Result<List<RecentTrade>> {
        return try {
            val response = httpClient.get("$baseUrl/api/trading/charts/$symbol/trades") {
                parameter("limit", limit)
            }
            val apiResponse = response.body<ApiResponse<List<RecentTrade>>>()
            if (apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to get recent trades"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getFundingHistory(symbol: String, limit: Int = 24): Result<List<FundingHistory>> {
        return try {
            val response = httpClient.get("$baseUrl/api/trading/charts/$symbol/funding") {
                parameter("limit", limit)
            }
            val apiResponse = response.body<ApiResponse<List<FundingHistory>>>()
            if (apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to get funding history"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =================================================================================================
    // REAL-TIME DATA FLOW
    // =================================================================================================
    
    fun tradingDataFlow(intervalMs: Long = 5000L): Flow<TradingState> = flow {
        while (true) {
            try {
                val stats = getStatus().getOrNull()
                val positions = getPositions().getOrNull() ?: emptyList()
                val balances = getBalances().getOrNull() ?: emptyList()
                val orders = getOrders().getOrNull() ?: emptyList()
                val markets = getMarkets().getOrNull() ?: emptyList()
                val events = getTradeEvents().getOrNull() ?: emptyList()
                
                val state = TradingState(
                    stats = stats,
                    positions = positions,
                    balances = balances,
                    orders = orders,
                    markets = markets,
                    events = events,
                    isLoading = false,
                    error = null,
                    lastUpdate = System.currentTimeMillis()
                )
                
                emit(state)
            } catch (e: Exception) {
                emit(TradingState(isLoading = false, error = e.message))
            }
            
            delay(intervalMs)
        }
    }
    
    // Enhanced market data streaming
    fun chartDataFlow(symbol: String, timeframe: String = "1h"): Flow<TradingViewData> = flow {
        while (true) {
            try {
                val result = getCandlestickData(symbol, timeframe)
                result.getOrNull()?.let { emit(it) }
            } catch (e: Exception) {
                // Continue on error
            }
            delay(5000) // Update every 5 seconds
        }
    }
    
    fun orderBookFlow(symbol: String): Flow<OrderBook> = flow {
        while (true) {
            try {
                val result = getOrderBook(symbol)
                result.getOrNull()?.let { emit(it) }
            } catch (e: Exception) {
                // Continue on error
            }
            delay(1000) // Update every second
        }
    }
    
    fun recentTradesFlow(symbol: String): Flow<List<RecentTrade>> = flow {
        while (true) {
            try {
                val result = getRecentTrades(symbol)
                result.getOrNull()?.let { emit(it) }
            } catch (e: Exception) {
                // Continue on error
            }
            delay(2000) // Update every 2 seconds
        }
    }
}