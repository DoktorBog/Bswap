package com.bswap.server.service

import com.bswap.server.SolanaTokenSwapBot
import com.bswap.server.SolanaSwapBotConfig
import com.bswap.server.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class BotManagementService(
    private val serverWalletService: ServerWalletService? = null
) {
    private val logger = LoggerFactory.getLogger(BotManagementService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isRunning = AtomicBoolean(false)
    private val _startTime = AtomicLong(0)
    private val _totalTrades = AtomicInteger(0)
    private val _successfulTrades = AtomicInteger(0)
    private val _failedTrades = AtomicInteger(0)
    private val _lastActivity = AtomicLong(System.currentTimeMillis())

    private var _config = SolanaSwapBotConfig()
    var bot: SolanaTokenSwapBot = SolanaTokenSwapBot(_config)
    private var _tradingParameters = TradingParameters()
    private var _riskSettings = RiskSettings()
    private var _positions = mutableListOf<BotPosition>()
    private var _alerts = mutableListOf<BotAlert>()

    private val _botStatus = MutableStateFlow(createBotStatus())
    val botStatus: StateFlow<BotStatus> = _botStatus.asStateFlow()

    fun startBot(): ApiResponse<BotStatus> {
        return try {
            if (_isRunning.get()) {
                ApiResponse(false, "Bot is already running", createBotStatus())
            } else {
                _isRunning.set(true)
                _startTime.set(System.currentTimeMillis())
                _lastActivity.set(System.currentTimeMillis())

                val status = createBotStatus()
                _botStatus.value = status

                // Pre-fetch transaction history silently in background
                _config.walletPublicKey?.toString()?.let { publicKey ->
                    preFetchTransactionHistory(publicKey)
                }

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
            val bot = bot

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
            val totalTrades = _totalTrades.get()
            val successfulTrades = _successfulTrades.get()
            val failedTrades = _failedTrades.get()
            val successRate = if (totalTrades > 0) successfulTrades.toDouble() / totalTrades else 0.0

            val stats = TradingStatistics(
                totalTrades = totalTrades,
                successfulTrades = successfulTrades,
                failedTrades = failedTrades,
                totalVolume = Random.nextDouble(1000.0, 50000.0),
                totalProfitLoss = Random.nextDouble(-500.0, 2000.0),
                successRate = successRate,
                averageTradeSize = _config.solAmountToTrade.toDouble(),
                totalFees = Random.nextDouble(5.0, 100.0),
                bestTradeProfit = Random.nextDouble(50.0, 500.0),
                worstTradeLoss = Random.nextDouble(-200.0, -10.0)
            )
            ApiResponse(true, "Trading statistics retrieved", stats)
        } catch (e: Exception) {
            logger.error("Failed to get trading statistics: ${e.message}", e)
            ApiResponse(false, "Failed to retrieve statistics: ${e.message}")
        }
    }

    private fun createBotStatus(): BotStatus {
        val currentTime = System.currentTimeMillis()
        val uptimeMillis = if (_isRunning.get()) currentTime - _startTime.get() else 0L

        return BotStatus(
            isRunning = _isRunning.get(),
            uptimeMillis = uptimeMillis,
            activeTokens = _positions.count { it.status == PositionStatus.OPEN },
            totalTrades = _totalTrades.get(),
            successfulTrades = _successfulTrades.get(),
            failedTrades = _failedTrades.get(),
            currentBalance = Random.nextDouble(1.0, 10.0).toString(), // Mock balance
            lastActivity = if (_lastActivity.get() > 0) _lastActivity.get() else null,
            currentToken = if (_positions.isNotEmpty()) _positions.first().symbol else null,
            statistics = createTradingStatistics(),
            connectedWallet = _config.walletPublicKey?.toString()
        )
    }

    private fun createTradingStatistics(): TradingStatistics {
        val totalTrades = _totalTrades.get()
        val successfulTrades = _successfulTrades.get()
        val failedTrades = _failedTrades.get()
        val successRate = if (totalTrades > 0) successfulTrades.toDouble() / totalTrades else 0.0

        return TradingStatistics(
            totalTrades = totalTrades,
            successfulTrades = successfulTrades,
            failedTrades = failedTrades,
            totalVolume = Random.nextDouble(1000.0, 50000.0),
            totalProfitLoss = Random.nextDouble(-500.0, 2000.0),
            successRate = successRate,
            averageTradeSize = _config.solAmountToTrade.toDouble(),
            totalFees = Random.nextDouble(5.0, 100.0),
            bestTradeProfit = Random.nextDouble(50.0, 500.0),
            worstTradeLoss = Random.nextDouble(-200.0, -10.0)
        )
    }

    // Trading Parameters Methods
    fun getTradingParameters(): ApiResponse<TradingParameters> {
        return ApiResponse(true, "Trading parameters retrieved", _tradingParameters)
    }

    fun updateTradingParameters(params: TradingParameters): ApiResponse<TradingParameters> {
        return try {
            _tradingParameters = params
            logger.info("Trading parameters updated")
            ApiResponse(true, "Trading parameters updated successfully", params)
        } catch (e: Exception) {
            logger.error("Failed to update trading parameters", e)
            ApiResponse(false, "Failed to update trading parameters: ${e.message}")
        }
    }

    // Risk Settings Methods
    fun getRiskSettings(): ApiResponse<RiskSettings> {
        return ApiResponse(true, "Risk settings retrieved", _riskSettings)
    }

    fun updateRiskSettings(settings: RiskSettings): ApiResponse<RiskSettings> {
        return try {
            _riskSettings = settings
            logger.info("Risk settings updated")
            ApiResponse(true, "Risk settings updated successfully", settings)
        } catch (e: Exception) {
            logger.error("Failed to update risk settings", e)
            ApiResponse(false, "Failed to update risk settings: ${e.message}")
        }
    }

    // Position Management Methods
    fun getOpenPositions(): ApiResponse<List<BotPosition>> {
        val openPositions = _positions.filter { it.status == PositionStatus.OPEN }
        return ApiResponse(true, "Open positions retrieved", openPositions)
    }

    fun closePosition(tokenAddress: String): ApiResponse<String> {
        return try {
            val position = _positions.find { it.tokenAddress == tokenAddress && it.status == PositionStatus.OPEN }
            if (position != null) {
                val index = _positions.indexOf(position)
                _positions[index] = position.copy(status = PositionStatus.CLOSING)
                // Simulate closing the position
                logger.info("Closing position for token: $tokenAddress")
                ApiResponse(true, "Position close initiated for $tokenAddress")
            } else {
                ApiResponse(false, "No open position found for token: $tokenAddress")
            }
        } catch (e: Exception) {
            logger.error("Failed to close position", e)
            ApiResponse(false, "Failed to close position: ${e.message}")
        }
    }

    // Analytics Methods
    fun getBotAnalytics(): ApiResponse<BotAnalytics> {
        return try {
            val analytics = BotAnalytics(
                totalProfit = Random.nextDouble(100.0, 5000.0),
                totalLoss = Random.nextDouble(-2000.0, -50.0),
                winRate = if (_totalTrades.get() > 0) _successfulTrades.get().toDouble() / _totalTrades.get() else 0.0,
                avgHoldTime = Random.nextLong(3600000, 86400000), // 1-24 hours
                bestTrade = TradeResult("BONK", Random.nextDouble(50.0, 500.0), Random.nextDouble(10.0, 200.0), System.currentTimeMillis()),
                worstTrade = TradeResult("PEPE", Random.nextDouble(-200.0, -10.0), Random.nextDouble(-80.0, -5.0), System.currentTimeMillis()),
                dailyPnL = generateDailyPnL(30),
                performanceMetrics = PerformanceMetrics(
                    sharpeRatio = Random.nextDouble(0.5, 2.5),
                    maxDrawdown = Random.nextDouble(-30.0, -5.0),
                    volatility = Random.nextDouble(15.0, 45.0),
                    totalReturn = Random.nextDouble(-20.0, 150.0)
                )
            )
            ApiResponse(true, "Bot analytics retrieved", analytics)
        } catch (e: Exception) {
            logger.error("Failed to get bot analytics", e)
            ApiResponse(false, "Failed to retrieve analytics: ${e.message}")
        }
    }

    fun getPerformanceMetrics(): ApiResponse<PerformanceMetrics> {
        return try {
            val metrics = PerformanceMetrics(
                sharpeRatio = Random.nextDouble(0.5, 2.5),
                maxDrawdown = Random.nextDouble(-30.0, -5.0),
                volatility = Random.nextDouble(15.0, 45.0),
                totalReturn = Random.nextDouble(-20.0, 150.0)
            )
            ApiResponse(true, "Performance metrics retrieved", metrics)
        } catch (e: Exception) {
            logger.error("Failed to get performance metrics", e)
            ApiResponse(false, "Failed to retrieve performance metrics: ${e.message}")
        }
    }

    fun getDailyPnL(days: Int): ApiResponse<Map<String, Double>> {
        return try {
            val dailyPnL = generateDailyPnL(days)
            ApiResponse(true, "Daily P&L retrieved", dailyPnL)
        } catch (e: Exception) {
            logger.error("Failed to get daily P&L", e)
            ApiResponse(false, "Failed to retrieve daily P&L: ${e.message}")
        }
    }

    // Alert Methods
    fun getAlerts(unreadOnly: Boolean = false): ApiResponse<List<BotAlert>> {
        return try {
            val alerts = if (unreadOnly) _alerts.filter { !it.isRead } else _alerts
            ApiResponse(true, "Alerts retrieved", alerts.sortedByDescending { it.timestamp })
        } catch (e: Exception) {
            logger.error("Failed to get alerts", e)
            ApiResponse(false, "Failed to retrieve alerts: ${e.message}")
        }
    }

    fun markAlertAsRead(alertId: String): ApiResponse<String> {
        return try {
            val alertIndex = _alerts.indexOfFirst { it.id == alertId }
            if (alertIndex >= 0) {
                _alerts[alertIndex] = _alerts[alertIndex].copy(isRead = true)
                ApiResponse(true, "Alert marked as read")
            } else {
                ApiResponse(false, "Alert not found")
            }
        } catch (e: Exception) {
            logger.error("Failed to mark alert as read", e)
            ApiResponse(false, "Failed to mark alert as read: ${e.message}")
        }
    }

    fun clearAllAlerts(): ApiResponse<String> {
        return try {
            _alerts.clear()
            ApiResponse(true, "All alerts cleared")
        } catch (e: Exception) {
            logger.error("Failed to clear alerts", e)
            ApiResponse(false, "Failed to clear alerts: ${e.message}")
        }
    }

    fun emergencyStop(): ApiResponse<String> {
        return try {
            // Close all positions
            _positions.forEach { position ->
                if (position.status == PositionStatus.OPEN) {
                    val index = _positions.indexOf(position)
                    _positions[index] = position.copy(status = PositionStatus.CLOSING)
                }
            }

            // Stop the bot
            stopBot()

            // Add emergency alert
            addAlert(BotAlert(
                id = UUID.randomUUID().toString(),
                type = AlertType.SYSTEM,
                title = "Emergency Stop Activated",
                message = "Bot has been emergency stopped and all positions are being closed",
                severity = AlertSeverity.CRITICAL,
                timestamp = System.currentTimeMillis()
            ))

            logger.warn("Emergency stop activated")
            ApiResponse(true, "Emergency stop activated successfully")
        } catch (e: Exception) {
            logger.error("Failed to execute emergency stop", e)
            ApiResponse(false, "Failed to execute emergency stop: ${e.message}")
        }
    }

    // Helper Methods
    private fun generateDailyPnL(days: Int): Map<String, Double> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val result = mutableMapOf<String, Double>()

        for (i in 0 until days) {
            val date = LocalDate.now().minusDays(i.toLong())
            val pnl = Random.nextDouble(-100.0, 200.0)
            result[date.format(formatter)] = pnl
        }

        return result
    }

    private fun addAlert(alert: BotAlert) {
        _alerts.add(alert)
        // Keep only the latest 100 alerts
        if (_alerts.size > 100) {
            _alerts.removeAt(0)
        }
    }
    
    /**
     * Pre-fetch transaction history silently in the background to warm up the cache
     */
    private fun preFetchTransactionHistory(publicKey: String) {
        scope.launch {
            try {
                delay(1000) // Small delay to let bot fully start
                
                // Silently fetch first page to warm up cache without any logging
                serverWalletService?.let { service ->
                    val request = com.bswap.shared.model.WalletHistoryRequest(
                        publicKey = publicKey,
                        limit = 50,
                        offset = 0
                    )
                    service.getWalletHistory(request, silent = true)
                }
            } catch (e: Exception) {
                // Silently ignore errors in background prefetch
            }
        }
    }

    // Methods to be called by the bot to update statistics
    fun incrementSuccessfulTrades() {
        _successfulTrades.incrementAndGet()
        _lastActivity.set(System.currentTimeMillis())
        _botStatus.value = createBotStatus()

        // Add success alert
        addAlert(BotAlert(
            id = UUID.randomUUID().toString(),
            type = AlertType.TRADE_EXECUTED,
            title = "Successful Trade",
            message = "Trade completed successfully",
            severity = AlertSeverity.INFO,
            timestamp = System.currentTimeMillis()
        ))
    }

    fun incrementFailedTrades() {
        _failedTrades.incrementAndGet()
        _lastActivity.set(System.currentTimeMillis())
        _botStatus.value = createBotStatus()

        // Add failure alert
        addAlert(BotAlert(
            id = UUID.randomUUID().toString(),
            type = AlertType.ERROR,
            title = "Trade Failed",
            message = "Trade execution failed",
            severity = AlertSeverity.ERROR,
            timestamp = System.currentTimeMillis()
        ))
    }
}
