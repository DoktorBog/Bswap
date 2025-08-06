package com.bswap.server.routes

import com.bswap.server.models.*
import com.bswap.server.service.BotManagementService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.botRoutes(botManagementService: BotManagementService) {
    route("/bot") {
        
        // Get bot status
        get("/status") {
            val response = botManagementService.getBotStatus()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Control bot (start/stop)
        post("/control") {
            try {
                val request = call.receive<BotControlRequest>()
                val response = when (request.action.lowercase()) {
                    "start" -> botManagementService.startBot()
                    "stop" -> botManagementService.stopBot()
                    else -> ApiResponse<BotStatus>(false, "Invalid action. Use 'start' or 'stop'")
                }
                
                val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.BadRequest
                call.respond(statusCode, response)
            } catch (e: Exception) {
                val errorResponse = ApiResponse<BotStatus>(false, "Invalid request format: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, errorResponse)
            }
        }
        
        // Get bot configuration
        get("/config") {
            val response = botManagementService.getBotConfig()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Update bot configuration
        put("/config") {
            try {
                val request = call.receive<BotConfigUpdateRequest>()
                val response = botManagementService.updateBotConfig(request.config)
                
                val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.BadRequest
                call.respond(statusCode, response)
            } catch (e: Exception) {
                val errorResponse = ApiResponse<BotConfig>(false, "Invalid request format: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, errorResponse)
            }
        }
        
        // Execute manual trade
        post("/trade") {
            try {
                val request = call.receive<ManualTradeRequest>()
                val response = botManagementService.executeManualTrade(request)
                
                val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.BadRequest
                call.respond(statusCode, response)
            } catch (e: Exception) {
                val errorResponse = ApiResponse<String>(false, "Invalid request format: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, errorResponse)
            }
        }
        
        // Get active tokens
        get("/tokens") {
            val response = botManagementService.getActiveTokens()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Get trading statistics
        get("/statistics") {
            val response = botManagementService.getTradingStatistics()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Health check
        get("/health") {
            val response = ApiResponse<String>(true, "Bot API is healthy", "OK")
            call.respond(HttpStatusCode.OK, response)
        }
    }
}