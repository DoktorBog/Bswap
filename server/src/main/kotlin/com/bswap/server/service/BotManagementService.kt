package com.bswap.server.service

import com.bswap.server.SolanaTokenSwapBot
import com.bswap.server.SolanaSwapBotConfig
import com.bswap.server.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class BotManagementService {
    private val logger = LoggerFactory.getLogger(BotManagementService::class.java)
    
    private val _isRunning = AtomicBoolean(false)
    private val _startTime = AtomicLong(0)
    private val _totalTrades = AtomicInteger(0)
    private val _successfulTrades = AtomicInteger(0)
    private val _failedTrades = AtomicInteger(0)
    private val _lastActivity = AtomicLong(System.currentTimeMillis())
    
    private var _bot: SolanaTokenSwapBot? = null
    private var _config = SolanaSwapBotConfig()
    
    private val _botStatus = MutableStateFlow(createBotStatus())
    val botStatus: StateFlow<BotStatus> = _botStatus.asStateFlow()
    
    fun startBot(): ApiResponse<BotStatus> {
        return try {
            if (_isRunning.get()) {
                ApiResponse(false, "Bot is already running", createBotStatus())
            } else {
                _bot = SolanaTokenSwapBot(_config)
                _isRunning.set(true)
                _startTime.set(System.currentTimeMillis())
                _lastActivity.set(System.currentTimeMillis())
                
                val status = createBotStatus()
                _botStatus.value = status
                
                logger.info("Trading bot started successfully")
                ApiResponse(true, "Bot started successfully", status)
            }
        } catch (e: Exception) {
            logger.error("Failed to start bot: ${e.message}", e)
            ApiResponse(false, "Failed to start bot: ${e.message}")
        }
    }
    
    fun stopBot(): ApiResponse<BotStatus> {
        return try {
            if (!_isRunning.get()) {
                ApiResponse(false, "Bot is not running", createBotStatus())
            } else {
                _bot = null
                _isRunning.set(false)
                _startTime.set(0)
                
                val status = createBotStatus()
                _botStatus.value = status
                
                logger.info("Trading bot stopped successfully")
                ApiResponse(true, "Bot stopped successfully", status)
            }
        } catch (e: Exception) {
            logger.error("Failed to stop bot: ${e.message}", e)
            ApiResponse(false, "Failed to stop bot: ${e.message}")
        }
    }
    
    fun getBotStatus(): ApiResponse<BotStatus> {
        val status = createBotStatus()
        _botStatus.value = status
        return ApiResponse(true, "Bot status retrieved", status)
    }
    
    fun updateBotConfig(newConfig: BotConfig): ApiResponse<BotConfig> {
        return try {
            val wasRunning = _isRunning.get()
            if (wasRunning) {
                stopBot()
            }
            
            _config = SolanaSwapBotConfig(
                walletPublicKey = _config.walletPublicKey,
                swapMint = _config.swapMint,
                solAmountToTrade = newConfig.solAmountToTrade.toBigDecimal(),
                autoSellAllSpl = newConfig.autoSellAllSpl,
                maxKnownTokens = newConfig.maxKnownTokens,
                sellWaitMs = newConfig.sellWaitMs,
                zeroBalanceCloseBatch = newConfig.zeroBalanceCloseBatch,
                splSellBatch = newConfig.splSellBatch,
                closeAccountsIntervalMs = newConfig.closeAccountsIntervalMs,
                sellAllSplIntervalMs = newConfig.sellAllSplIntervalMs,
                useJito = newConfig.useJito
            )
            
            if (wasRunning) {
                startBot()
            }
            
            logger.info("Bot configuration updated successfully")
            ApiResponse(true, "Configuration updated successfully", newConfig)
        } catch (e: Exception) {
            logger.error("Failed to update bot config: ${e.message}", e)
            ApiResponse(false, "Failed to update configuration: ${e.message}")
        }
    }
    
    fun getBotConfig(): ApiResponse<BotConfig> {
        val config = BotConfig(
            solAmountToTrade = _config.solAmountToTrade.toString(),
            autoSellAllSpl = _config.autoSellAllSpl,
            maxKnownTokens = _config.maxKnownTokens,
            sellWaitMs = _config.sellWaitMs,
            zeroBalanceCloseBatch = _config.zeroBalanceCloseBatch,
            splSellBatch = _config.splSellBatch,
            closeAccountsIntervalMs = _config.closeAccountsIntervalMs,
            sellAllSplIntervalMs = _config.sellAllSplIntervalMs,
            useJito = _config.useJito
        )
        return ApiResponse(true, "Configuration retrieved", config)
    }
    
    fun executeManualTrade(request: ManualTradeRequest): ApiResponse<String> {
        return try {
            val bot = _bot ?: return ApiResponse(false, "Bot is not running")
            
            when (request.action.lowercase()) {
                "buy" -> {
                    bot.singleTrade(request.tokenAddress)
                    _totalTrades.incrementAndGet()
                    _lastActivity.set(System.currentTimeMillis())
                    ApiResponse(true, "Buy order initiated for ${request.tokenAddress}")
                }
                "sell" -> {
                    bot.sellOneToken(request.tokenAddress)
                    _totalTrades.incrementAndGet()
                    _lastActivity.set(System.currentTimeMillis())
                    ApiResponse(true, "Sell order initiated for ${request.tokenAddress}")
                }
                else -> ApiResponse(false, "Invalid action. Use 'buy' or 'sell'")
            }
        } catch (e: Exception) {
            logger.error("Failed to execute manual trade: ${e.message}", e)
            _failedTrades.incrementAndGet()
            ApiResponse(false, "Failed to execute trade: ${e.message}")
        }
    }
    
    fun getActiveTokens(): ApiResponse<List<TokenTradeInfo>> {
        return try {
            // For now, return empty list - we'll implement this when we refactor the bot
            ApiResponse(true, "Active tokens retrieved", emptyList())
        } catch (e: Exception) {
            logger.error("Failed to get active tokens: ${e.message}", e)
            ApiResponse(false, "Failed to retrieve active tokens: ${e.message}")
        }
    }
    
    fun getTradingStatistics(): ApiResponse<TradingStatistics> {
        return try {
            val stats = TradingStatistics(
                totalVolume = "0.0", // TODO: Calculate from trading history
                profitLoss = "0.0",
                averageTradeSize = _config.solAmountToTrade.toString(),
                winRate = if (_totalTrades.get() > 0) _successfulTrades.get().toDouble() / _totalTrades.get() else 0.0,
                bestTrade = "0.0",
                worstTrade = "0.0",
                totalFees = "0.0"
            )
            ApiResponse(true, "Trading statistics retrieved", stats)
        } catch (e: Exception) {
            logger.error("Failed to get trading statistics: ${e.message}", e)
            ApiResponse(false, "Failed to retrieve statistics: ${e.message}")
        }
    }
    
    private fun createBotStatus(): BotStatus {
        val currentTime = System.currentTimeMillis()
        val uptime = if (_isRunning.get()) currentTime - _startTime.get() else 0L
        
        return BotStatus(
            isRunning = _isRunning.get(),
            uptime = uptime,
            activeTokens = 0, // TODO: Get from bot state
            totalTrades = _totalTrades.get(),
            successfulTrades = _successfulTrades.get(),
            failedTrades = _failedTrades.get(),
            currentBalance = "0.0", // TODO: Get from wallet service
            lastActivity = if (_lastActivity.get() > 0) _lastActivity.get() else null
        )
    }
    
    // Methods to be called by the bot to update statistics
    fun incrementSuccessfulTrades() {
        _successfulTrades.incrementAndGet()
        _lastActivity.set(System.currentTimeMillis())
        _botStatus.value = createBotStatus()
    }
    
    fun incrementFailedTrades() {
        _failedTrades.incrementAndGet()
        _lastActivity.set(System.currentTimeMillis())
        _botStatus.value = createBotStatus()
    }
}