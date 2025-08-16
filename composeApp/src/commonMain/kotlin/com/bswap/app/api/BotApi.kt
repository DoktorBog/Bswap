package com.bswap.app.api

import com.bswap.app.models.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

class BotApi(private val client: HttpClient, private val baseUrl: String = "http://192.168.0.152:9090") {
    
    suspend fun getBotStatus(): ApiResponse<BotStatus> {
        return client.get("$baseUrl/bot/status").body()
    }
    
    suspend fun startBot(): ApiResponse<BotStatus> {
        return client.post("$baseUrl/bot/control") {
            contentType(ContentType.Application.Json)
            setBody(BotControlRequest("start"))
        }.body()
    }
    
    suspend fun stopBot(): ApiResponse<BotStatus> {
        return client.post("$baseUrl/bot/control") {
            contentType(ContentType.Application.Json)
            setBody(BotControlRequest("stop"))
        }.body()
    }
    
    suspend fun getBotConfig(): ApiResponse<BotConfig> {
        return client.get("$baseUrl/bot/config").body()
    }
    
    suspend fun updateBotConfig(config: BotConfig): ApiResponse<BotConfig> {
        return client.put("$baseUrl/bot/config") {
            contentType(ContentType.Application.Json)
            setBody(BotConfigUpdateRequest(config))
        }.body()
    }
    
    suspend fun executeManualTrade(request: ManualTradeRequest): ApiResponse<String> {
        return client.post("$baseUrl/bot/trade") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun getActiveTokens(): ApiResponse<List<TokenTradeInfo>> {
        return client.get("$baseUrl/bot/tokens").body()
    }
    
    suspend fun getTradingStatistics(): ApiResponse<TradingStatistics> {
        return client.get("$baseUrl/bot/statistics").body()
    }
    
    suspend fun healthCheck(): ApiResponse<String> {
        return client.get("$baseUrl/bot/health").body()
    }
}