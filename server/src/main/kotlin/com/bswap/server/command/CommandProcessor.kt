package com.bswap.server.command

import com.bswap.server.service.BotManagementService
import com.bswap.server.service.ServerWalletService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

data class CommandResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

class CommandProcessor(
    private val botManagementService: BotManagementService?,
    private val walletService: ServerWalletService?,
    private val priceService: com.bswap.server.service.PriceService? = null
) {
    private val logger = LoggerFactory.getLogger(CommandProcessor::class.java)
    
    fun processCommand(command: String): CommandResult {
        val trimmedCommand = command.trim()
        
        if (trimmedCommand.isEmpty()) {
            return CommandResult(false, "Empty command")
        }
        
        val parts = trimmedCommand.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        val cmd = parts[0].lowercase()
        val args = parts.drop(1)
        
        logger.info("🎯 Processing command: '$cmd' with args: $args")
        
        return try {
            when (cmd) {
                "help", "h" -> showHelp()
                "status" -> getBotStatus()
                "start" -> startBot()
                "stop" -> stopBot()
                "restart" -> restartBot()
                "balance" -> getWalletBalance()
                "history" -> getWalletHistory(args)
                "stats" -> getBotStats()
                "price" -> getTokenPrice(args)
                "buy" -> testBuyToken(args)
                "clear" -> clearScreen()
                "version" -> getVersion()
                "config" -> showConfig()
                else -> CommandResult(false, "Unknown command '$cmd'. Type 'help' for available commands.")
            }
        } catch (e: Exception) {
            logger.error("Command execution failed: ${e.message}", e)
            CommandResult(false, "Command failed: ${e.message}")
        }
    }
    
    private fun showHelp(): CommandResult {
        val helpText = """
        📟 Available Commands:
        
        Bot Management:
          start                 - Start the trading bot
          stop                  - Stop the trading bot
          restart               - Restart the trading bot
          status                - Show bot status and statistics
          stats                 - Show detailed trading statistics
          
        Wallet Commands:
          balance               - Show wallet balance
          history [limit]       - Show transaction history (default: 10)
          price [token_mint]    - Get token price (SOL if no mint specified)
          buy [token_mint]      - Test buy a specific token
          
        System Commands:
          config                - Show current configuration
          version               - Show server version
          clear                 - Clear command history
          help, h               - Show this help message
          
        Examples:
          > start
          > balance
          > history 20
          > price
          > price EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v
          > status
        """.trimIndent()
        
        return CommandResult(true, helpText)
    }
    
    private fun getBotStatus(): CommandResult {
        return try {
            runBlocking {
                val response = botManagementService?.getBotStatus()
                if (response?.success == true && response.data != null) {
                    val status = response.data
                    val message = """
                    🤖 Bot Status: ${if (status.isRunning) "RUNNING ✅" else "STOPPED ❌"}
                    
                    Uptime: ${formatUptime(status.uptimeMillis)}
                    Current Token: ${status.currentToken ?: "None"}
                    
                    📊 Statistics:
                    • Total Trades: ${status.statistics?.totalTrades ?: 0}
                    • Success Rate: ${status.statistics?.successRate?.let { "${(it * 100).toInt()}%" } ?: "0%"}
                    • Profit/Loss: ${status.statistics?.totalProfitLoss ?: 0.0} SOL
                    """.trimIndent()
                    CommandResult(true, message, status)
                } else {
                    CommandResult(false, response?.message ?: "Failed to get bot status")
                }
            }
        } catch (e: Exception) {
            CommandResult(false, "Error getting bot status: ${e.message}")
        }
    }
    
    private fun startBot(): CommandResult {
        return try {
            runBlocking {
                val response = botManagementService?.startBot()
                if (response?.success == true) {
                    CommandResult(true, "🚀 Bot started successfully!")
                } else {
                    CommandResult(false, response?.message ?: "Failed to start bot")
                }
            }
        } catch (e: Exception) {
            CommandResult(false, "Error starting bot: ${e.message}")
        }
    }
    
    private fun stopBot(): CommandResult {
        return try {
            runBlocking {
                val response = botManagementService?.stopBot()
                if (response?.success == true) {
                    CommandResult(true, "⏹️ Bot stopped successfully!")
                } else {
                    CommandResult(false, response?.message ?: "Failed to stop bot")
                }
            }
        } catch (e: Exception) {
            CommandResult(false, "Error stopping bot: ${e.message}")
        }
    }
    
    private fun restartBot(): CommandResult {
        val stopResult = stopBot()
        if (!stopResult.success) {
            return stopResult
        }
        
        Thread.sleep(1000) // Wait 1 second
        
        return startBot()
    }
    
    private fun getWalletBalance(): CommandResult {
        return try {
            runBlocking {
                val response = walletService?.getBotWalletBalance()
                if (response?.success == true && response.data != null) {
                    val balance = response.data
                    val message = """
                    💰 Wallet Balance:
                    
                    SOL: ${String.format("%.4f", balance.solBalance)} SOL
                    USD Value: $${String.format("%.2f", balance.totalValueUSD)}
                    
                    Token Holdings: ${balance.tokenBalances.size} tokens
                    Last Updated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(balance.lastUpdated))}
                    """.trimIndent()
                    CommandResult(true, message, balance)
                } else {
                    CommandResult(false, response?.message ?: "Failed to get wallet balance")
                }
            }
        } catch (e: Exception) {
            CommandResult(false, "Error getting wallet balance: ${e.message}")
        }
    }
    
    private fun getWalletHistory(args: List<String>): CommandResult {
        val limit = args.firstOrNull()?.toIntOrNull() ?: 10
        
        return try {
            runBlocking {
                val request = com.bswap.shared.model.WalletHistoryRequest(limit = limit, offset = 0)
                val response = walletService?.getBotWalletHistory(request, silent = false)
                if (response?.success == true && response.data != null) {
                    val history = response.data
                    if (history.transactions.isEmpty()) {
                        CommandResult(true, "📜 No transaction history found")
                    } else {
                        val message = buildString {
                            appendLine("📜 Transaction History (Last $limit):")
                            appendLine()
                            history.transactions.forEachIndexed { index, tx ->
                                val symbol = if (tx.type.name.contains("RECEIVE") || tx.type.name.contains("BUY")) "✅" else "📤"
                                appendLine("${index + 1}. $symbol ${tx.type.name} ${String.format("%.4f", tx.amount)} ${tx.token}")
                                appendLine("   Signature: ${tx.signature.take(20)}...")
                                if (index < history.transactions.size - 1) appendLine()
                            }
                        }
                        CommandResult(true, message, history)
                    }
                } else {
                    CommandResult(false, response?.message ?: "Failed to get wallet history")
                }
            }
        } catch (e: Exception) {
            CommandResult(false, "Error getting wallet history: ${e.message}")
        }
    }
    
    private fun getBotStats(): CommandResult {
        val statusResult = getBotStatus()
        if (!statusResult.success) {
            return statusResult
        }
        
        val balanceResult = getWalletBalance()
        val message = buildString {
            appendLine(statusResult.message)
            appendLine()
            if (balanceResult.success) {
                appendLine(balanceResult.message)
            } else {
                appendLine("⚠️ Could not fetch wallet balance: ${balanceResult.message}")
            }
        }
        
        return CommandResult(true, message)
    }
    
    private fun clearScreen(): CommandResult {
        return CommandResult(true, "\n".repeat(50) + "🧹 Screen cleared")
    }
    
    private fun getVersion(): CommandResult {
        return CommandResult(true, """
        🔧 Bswap Trading Bot Server
        Version: 1.0.0
        Build: ${System.currentTimeMillis()}
        Java: ${System.getProperty("java.version")}
        OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}
        """.trimIndent())
    }
    
    private fun showConfig(): CommandResult {
        return CommandResult(true, """
        ⚙️ Current Configuration:
        
        Server: Running
        Bot Wallet: CtKJfXzxVN5cwc1krUrCu2Sd44ybjttJDjopJC1WqPra
        RPC URL: ${com.bswap.server.RPC_URL}
        Jupiter API: ${com.bswap.server.JUPITER_API_URL}
        
        Environment Variables:
        • RPC_URL: ${System.getenv("RPC_URL") ?: "default"}
        • JUPITER_API_URL: ${System.getenv("JUPITER_API_URL") ?: "default"}
        """.trimIndent())
    }
    
    private fun getTokenPrice(args: List<String>): CommandResult {
        return try {
            val mint = args.firstOrNull() ?: com.bswap.server.service.PriceService.SOL_MINT
            
            if (priceService == null) {
                return CommandResult(false, "Price service not available")
            }
            
            runBlocking {
                val price = priceService.getTokenPrice(mint)
                if (price != null) {
                    val message = """
                    💰 Token Price Information:
                    
                    Token: ${price.symbol} (${price.mint.take(12)}...)
                    Price (USD): $${String.format("%.6f", price.priceUsd)}
                    Price (USDT): $${String.format("%.6f", price.priceUsdt)}
                    Source: ${price.source}
                    Updated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(price.timestamp))}
                    """.trimIndent()
                    CommandResult(true, message, price)
                } else {
                    CommandResult(false, "Could not fetch price for token: ${mint.take(12)}...")
                }
            }
        } catch (e: Exception) {
            CommandResult(false, "Error fetching token price: ${e.message}")
        }
    }
    
    private fun testBuyToken(args: List<String>): CommandResult {
        return try {
            val mint = args.firstOrNull() 
                ?: return CommandResult(false, "Please provide a token mint address")
            
            if (botManagementService == null) {
                return CommandResult(false, "Bot management service not available")
            }
            
            // Trigger a manual buy
            runBlocking {
                try {
                    val bot = botManagementService.bot
                    
                    logger.info("🧪 Manual buy command for token: $mint")
                    bot.singleTrade(mint)
                    
                    CommandResult(true, "✅ Buy order initiated for token: ${mint.take(12)}...\nCheck logs for transaction details.")
                } catch (e: Exception) {
                    CommandResult(false, "Error executing buy: ${e.message}")
                }
            }
        } catch (e: Exception) {
            CommandResult(false, "Error processing buy command: ${e.message}")
        }
    }
    
    private fun formatUptime(uptimeMillis: Long): String {
        val seconds = uptimeMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}