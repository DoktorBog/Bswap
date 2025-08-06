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
        
        // Get trading parameters
        get("/trading-params") {
            val response = botManagementService.getTradingParameters()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Update trading parameters
        put("/trading-params") {
            try {
                val request = call.receive<TradingParameters>()
                val response = botManagementService.updateTradingParameters(request)
                
                val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.BadRequest
                call.respond(statusCode, response)
            } catch (e: Exception) {
                val errorResponse = ApiResponse<TradingParameters>(false, "Invalid request format: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, errorResponse)
            }
        }
        
        // Get open positions
        get("/positions") {
            val response = botManagementService.getOpenPositions()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Close specific position
        post("/positions/{tokenAddress}/close") {
            try {
                val tokenAddress = call.parameters["tokenAddress"] ?: throw IllegalArgumentException("Token address is required")
                val response = botManagementService.closePosition(tokenAddress)
                
                val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.BadRequest
                call.respond(statusCode, response)
            } catch (e: Exception) {
                val errorResponse = ApiResponse<String>(false, "Error closing position: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, errorResponse)
            }
        }
        
        // Get risk settings
        get("/risk-settings") {
            val response = botManagementService.getRiskSettings()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Update risk settings
        put("/risk-settings") {
            try {
                val request = call.receive<RiskSettings>()
                val response = botManagementService.updateRiskSettings(request)
                
                val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.BadRequest
                call.respond(statusCode, response)
            } catch (e: Exception) {
                val errorResponse = ApiResponse<RiskSettings>(false, "Invalid request format: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, errorResponse)
            }
        }
        
        // Get bot analytics
        get("/analytics") {
            val response = botManagementService.getBotAnalytics()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Get performance metrics
        get("/analytics/performance") {
            val response = botManagementService.getPerformanceMetrics()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Get daily P&L
        get("/analytics/pnl") {
            val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
            val response = botManagementService.getDailyPnL(days)
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Get alerts
        get("/alerts") {
            val unreadOnly = call.request.queryParameters["unreadOnly"]?.toBoolean() ?: false
            val response = botManagementService.getAlerts(unreadOnly)
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Mark alert as read
        post("/alerts/{alertId}/read") {
            try {
                val alertId = call.parameters["alertId"] ?: throw IllegalArgumentException("Alert ID is required")
                val response = botManagementService.markAlertAsRead(alertId)
                
                val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.NotFound
                call.respond(statusCode, response)
            } catch (e: Exception) {
                val errorResponse = ApiResponse<String>(false, "Error marking alert as read: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, errorResponse)
            }
        }
        
        // Clear all alerts
        delete("/alerts") {
            val response = botManagementService.clearAllAlerts()
            call.respond(HttpStatusCode.OK, response)
        }
        
        // Emergency stop
        post("/emergency-stop") {
            val response = botManagementService.emergencyStop()
            val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.InternalServerError
            call.respond(statusCode, response)
        }
        
        // Health check
        get("/health") {
            val response = ApiResponse<String>(true, "Bot API is healthy", "OK")
            call.respond(HttpStatusCode.OK, response)
        }
    }
}