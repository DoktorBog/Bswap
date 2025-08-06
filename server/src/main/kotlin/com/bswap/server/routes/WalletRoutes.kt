package com.bswap.server.routes

import com.bswap.server.models.*
import com.bswap.server.service.ServerWalletService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WalletRoutes")

fun Route.walletRoutes(walletService: ServerWalletService) {
    
    route("/wallet") {
        
        // Create new wallet
        post("/create") {
            try {
                val request = call.receive<CreateWalletRequest>()
                logger.info("Creating wallet with name: ${request.name}")
                
                val response = walletService.createWallet(request)
                
                if (response.success) {
                    call.respond(HttpStatusCode.Created, response)
                } else {
                    call.respond(HttpStatusCode.BadRequest, response)
                }
            } catch (e: Exception) {
                logger.error("Error creating wallet", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<CreateWalletResponse>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
        
        // Import existing wallet
        post("/import") {
            try {
                val request = call.receive<ImportWalletRequest>()
                logger.info("Importing wallet with ${request.mnemonic.size} words")
                
                val response = walletService.importWallet(request)
                
                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.BadRequest, response)
                }
            } catch (e: Exception) {
                logger.error("Error importing wallet", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<ImportWalletResponse>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
        
        // Get wallet balance
        get("/balance/{publicKey}") {
            try {
                val publicKey = call.parameters["publicKey"] ?: throw IllegalArgumentException("Public key is required")
                logger.info("Getting balance for wallet: $publicKey")
                
                val response = walletService.getWalletBalance(publicKey)
                
                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.NotFound, response)
                }
            } catch (e: Exception) {
                logger.error("Error getting wallet balance", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<WalletBalance>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
        
        // Get transaction history
        post("/history") {
            try {
                val request = call.receive<WalletHistoryRequest>()
                logger.info("Getting history for wallet: ${request.publicKey}")
                
                val response = walletService.getWalletHistory(request)
                
                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.NotFound, response)
                }
            } catch (e: Exception) {
                logger.error("Error getting wallet history", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<WalletHistoryResponse>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
        
        // List all wallets
        get("/list") {
            try {
                logger.info("Getting all wallets")
                val wallets = walletService.getAllWallets()
                
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = wallets,
                        message = "Wallets retrieved successfully"
                    )
                )
            } catch (e: Exception) {
                logger.error("Error getting wallet list", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<WalletInfo>>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
        
        // Set active wallet for bot
        post("/set-active") {
            try {
                val request = call.receive<Map<String, String>>()
                val botId = request["botId"] ?: throw IllegalArgumentException("Bot ID is required")
                val publicKey = request["publicKey"] ?: throw IllegalArgumentException("Public key is required")
                
                logger.info("Setting active wallet for bot $botId: $publicKey")
                
                val response = walletService.setActiveBotWallet(botId, publicKey)
                
                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.BadRequest, response)
                }
            } catch (e: Exception) {
                logger.error("Error setting active wallet", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<String>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
        
        // Get active wallet for bot
        get("/active/{botId}") {
            try {
                val botId = call.parameters["botId"] ?: throw IllegalArgumentException("Bot ID is required")
                logger.info("Getting active wallet for bot: $botId")
                
                val publicKey = walletService.getActiveBotWallet(botId)
                
                if (publicKey != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            data = mapOf("publicKey" to publicKey),
                            message = "Active wallet retrieved successfully"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Map<String, String>>(
                            success = false,
                            message = "No active wallet found for bot"
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("Error getting active wallet", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Map<String, String>>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
    }
}