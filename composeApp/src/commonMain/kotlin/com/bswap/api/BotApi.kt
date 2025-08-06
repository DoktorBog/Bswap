package com.bswap.api

import com.bswap.app.networkClient
import com.bswap.models.BotStatus
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

object BotApi {
    private const val BASE_URL = "http://192.168.0.152:9090" // Android emulator localhost
    private val client = networkClient()

    suspend fun getBotStatus(): ApiResponse<BotStatus> {
        return try {
            println("BotApi: Getting bot status from $BASE_URL/bot/status")
            val response = client.get("$BASE_URL/bot/status")
            println("BotApi: Status response: ${response.status}")
            val result = response.body<ApiResponse<BotStatus>>()
            println("BotApi: Parsed result: success=${result.success}")
            result
        } catch (e: Exception) {
            println("BotApi: Error getting status: ${e.message}")
            e.printStackTrace()
            ApiResponse(false, "Network error: ${e.message}")
        }
    }

    suspend fun startBot(): ApiResponse<BotStatus> {
        return try {
            println("BotApi: Starting bot at $BASE_URL/bot/control")
            val response = client.post("$BASE_URL/bot/control") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("action" to "start"))
            }
            println("BotApi: Start response: ${response.status}")
            val result = response.body<ApiResponse<BotStatus>>()
            println("BotApi: Start result: success=${result.success}")
            result
        } catch (e: Exception) {
            println("BotApi: Error starting bot: ${e.message}")
            e.printStackTrace()
            ApiResponse(false, "Network error: ${e.message}")
        }
    }

    suspend fun stopBot(): ApiResponse<BotStatus> {
        return try {
            println("BotApi: Stopping bot at $BASE_URL/bot/control")
            val response = client.post("$BASE_URL/bot/control") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("action" to "stop"))
            }
            println("BotApi: Stop response: ${response.status}")
            val result = response.body<ApiResponse<BotStatus>>()
            println("BotApi: Stop result: success=${result.success}")
            result
        } catch (e: Exception) {
            println("BotApi: Error stopping bot: ${e.message}")
            e.printStackTrace()
            ApiResponse(false, "Network error: ${e.message}")
        }
    }
}

@kotlinx.serialization.Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val timestamp: Long = System.currentTimeMillis()
)
