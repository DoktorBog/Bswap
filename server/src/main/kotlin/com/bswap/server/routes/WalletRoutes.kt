package com.bswap.server.routes

import com.bswap.server.models.*
import com.bswap.server.service.ServerWalletService
import com.bswap.shared.model.WalletHistoryRequest
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
        
        // Get wallet balance - for configured bot wallet
        get("/balance") {
            try {
                logger.info("Getting balance for bot wallet")
                
                val response = walletService.getBotWalletBalance()
                
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

        // Get wallet tokens - for configured bot wallet
        get("/tokens") {
            try {
                logger.info("Getting tokens for bot wallet")
                
                val response = walletService.getBotWalletTokens()
                
                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.NotFound, response)
                }
            } catch (e: Exception) {
                logger.error("Error getting wallet tokens", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<com.bswap.shared.model.TokenInfo>>(
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
                logger.info("🔍 DETAILED: WalletRoutes - received history request for bot wallet, limit: ${request.limit}")
                
                val response = walletService.getBotWalletHistory(request)
                logger.info("🔍 DETAILED: WalletRoutes - service returned success: ${response.success}, data: ${response.data != null}")
                
                if (response.success && response.data != null) {
                    logger.info("🔍 DETAILED: WalletRoutes - service data has ${response.data.transactions.size} transactions")
                    
                    // Convert server response to client expected format
                    val historyPage = com.bswap.shared.model.HistoryPage(
                        transactions = response.data.transactions.map { tx ->
                            com.bswap.shared.model.SolanaTx(
                                signature = tx.signature,
                                address = tx.publicKey,
                                amount = tx.amount,
                                incoming = tx.type == TransactionType.RECEIVE || tx.type == TransactionType.BUY
                            )
                        },
                        nextCursor = if (response.data.hasMore) "next_page" else null
                    )
                    
                    logger.info("🔍 DETAILED: WalletRoutes - converted to HistoryPage with ${historyPage.transactions.size} transactions")
                    logger.info("🔍 DETAILED: WalletRoutes - responding with OK status and ${historyPage.transactions.size} transactions")
                    
                    call.respond(HttpStatusCode.OK, historyPage)
                } else {
                    logger.warn("🔍 DETAILED: WalletRoutes - service failed or no data, success: ${response.success}, message: ${response.message}")
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
        
        // Debug endpoint - get cache stats
        get("/cache-stats/{publicKey}") {
            try {
                val publicKey = call.parameters["publicKey"] ?: throw IllegalArgumentException("Public key is required")
                logger.info("Getting cache stats for wallet: $publicKey")
                
                val stats = walletService.getCacheStats(publicKey)
                
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = stats,
                        message = "Cache stats retrieved successfully"
                    )
                )
            } catch (e: Exception) {
                logger.error("Error getting cache stats", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Map<String, Any>>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
        
        // Debug endpoint - clear cache
        post("/clear-cache/{publicKey}") {
            try {
                val publicKey = call.parameters["publicKey"] ?: throw IllegalArgumentException("Public key is required")
                logger.info("Clearing cache for wallet: $publicKey")
                
                walletService.clearWalletCache(publicKey)
                
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = "Cache cleared",
                        message = "Cache cleared successfully for wallet"
                    )
                )
            } catch (e: Exception) {
                logger.error("Error clearing cache", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<String>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
        
        // Debug endpoint - test RPC directly
        get("/test-rpc/{publicKey}") {
            try {
                val publicKey = call.parameters["publicKey"] ?: throw IllegalArgumentException("Public key is required")
                logger.info("Testing RPC directly for wallet: $publicKey")
                
                val result = walletService.testRpcDirect(publicKey)
                
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = result,
                        message = "RPC test completed"
                    )
                )
            } catch (e: Exception) {
                logger.error("Error testing RPC", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Map<String, Any>>(
                        success = false,
                        message = "Internal server error: ${e.message}"
                    )
                )
            }
        }
    }
}