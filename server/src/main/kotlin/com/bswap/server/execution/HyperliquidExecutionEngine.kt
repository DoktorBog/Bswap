package com.bswap.server.execution

import com.bswap.server.config.*
import com.bswap.server.service.HyperliquidService
import com.bswap.server.stratagy.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Hyperliquid Execution Engine
 * Manages trade execution, position management, and risk control for Hyperliquid exchange
 */
class HyperliquidExecutionEngine(
    private val config: HyperliquidConfig,
    private val tradingConfig: EnhancedTradingConfig,
    private val service: HyperliquidService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(HyperliquidExecutionEngine::class.java)
        private const val POSITION_CHECK_INTERVAL = 5000L
        private const val BALANCE_UPDATE_INTERVAL = 10000L
        private const val PNL_CHECK_INTERVAL = 2000L
    }

    private val executionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activePositions = ConcurrentHashMap<String, HyperliquidPosition>()
    private val positionPnL = ConcurrentHashMap<String, PositionPnL>()
    private val executionMutex = Mutex()
    private val isRunning = AtomicBoolean(false)
    
    private var monitoringJob: Job? = null
    private var balanceJob: Job? = null
    private var pnlJob: Job? = null
    
    data class PositionPnL(
        val symbol: String,
        val entryPrice: Double,
        val currentPrice: Double,
        val unrealizedPnl: Double,
        val realizedPnl: Double,
        val pnlPercent: Double,
        val highestPrice: Double,
        val lowestPrice: Double,
        val updateTime: Long
    )

    data class ExecutionContext(
        val symbol: String,
        val strategy: StrategySignal,
        val leverage: Double,
        val positionSize: Double,
        val stopLoss: Double?,
        val takeProfit: Double?,
        val metadata: Map<String, Any> = emptyMap()
    )

    init {
        logger.info("🚀 Initializing Hyperliquid Execution Engine")
        logger.info("📊 Max Positions: ${config.maxPositions}")
        logger.info("💰 Position Size: ${config.positionSizePercent}%")
        logger.info("🎯 Default Leverage: ${config.defaultLeverage}x")
        
        if (config.enabled && config.exchangeType == ExchangeType.HYPERLIQUID) {
            start()
        }
    }

    // =================================================================================================
    // ENGINE LIFECYCLE
    // =================================================================================================

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("▶️ Starting Hyperliquid Execution Engine")
            
            // Start position monitoring
            monitoringJob = executionScope.launch {
                monitorPositions()
            }
            
            // Start balance monitoring
            balanceJob = executionScope.launch {
                monitorBalances()
            }
            
            // Start PnL monitoring
            pnlJob = executionScope.launch {
                monitorPnL()
            }
            
            // Subscribe to position updates
            executionScope.launch {
                service.getPositionFlow().collect { position ->
                    handlePositionUpdate(position)
                }
            }
            
            // Subscribe to order updates
            executionScope.launch {
                service.getOrderFlow().collect { order ->
                    handleOrderUpdate(order)
                }
            }
            
            // Subscribe to trade results
            executionScope.launch {
                service.getTradeFlow().collect { trade ->
                    handleTradeResult(trade)
                }
            }
            
            logger.info("✅ Hyperliquid Execution Engine started")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("⏹️ Stopping Hyperliquid Execution Engine")
            
            runBlocking {
                // Close all positions
                closeAllPositions("Engine shutdown")
            }
            
            monitoringJob?.cancel()
            balanceJob?.cancel()
            pnlJob?.cancel()
            executionScope.cancel()
            
            logger.info("✅ Hyperliquid Execution Engine stopped")
        }
    }

    // =================================================================================================
    // TRADE EXECUTION
    // =================================================================================================

    suspend fun executeTrade(signal: StrategySignal): HyperliquidTradeResult {
        logger.info("🎯 ExecuteTrade called for ${signal.symbol} - ${signal.action} signal")
        logger.debug("📊 Signal details: action=${signal.action}, confidence=${signal.confidence}, metadata=${signal.metadata}")
        
        return executionMutex.withLock {
            try {
                logger.debug("🔒 Acquired execution lock for ${signal.symbol}")
                
                logger.info("📊 Executing trade signal: ${signal.action} ${signal.symbol}")
                
                when (signal.action) {
                    Action.BUY -> executeBuy(signal)
                    Action.SELL -> executeSell(signal)
                    Action.HOLD -> {
                        logger.info("⏸️ Holding position for ${signal.symbol}")
                        HyperliquidTradeResult(
                            success = true,
                            orderId = null,
                            executedPrice = null,
                            executedSize = null,
                            fee = null,
                            timestamp = System.currentTimeMillis(),
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("❌ Trade execution failed", e)
                HyperliquidTradeResult(
                    success = false,
                    orderId = null,
                    executedPrice = null,
                    executedSize = null,
                    fee = null,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            }
        }
    }

    private suspend fun executeBuy(signal: StrategySignal): HyperliquidTradeResult {
        logger.info("🟢 ExecuteBuy for ${signal.symbol} with confidence ${signal.confidence}")
        try {
            // Check if we can open a new position
            logger.debug("📈 Checking position limits: ${activePositions.size}/${config.maxPositions}")
            if (activePositions.size >= config.maxPositions) {
                logger.warn("⚠️ Maximum positions reached (${config.maxPositions})")
                return HyperliquidTradeResult(
                    success = false,
                    orderId = null,
                    executedPrice = null,
                    executedSize = null,
                    fee = null,
                    timestamp = System.currentTimeMillis(),
                    error = "Maximum positions reached"
                )
            }
            
            // Check if we already have a position in this symbol
            if (activePositions.containsKey(signal.symbol)) {
                logger.warn("⚠️ Position already exists for ${signal.symbol}")
                return HyperliquidTradeResult(
                    success = false,
                    orderId = null,
                    executedPrice = null,
                    executedSize = null,
                    fee = null,
                    timestamp = System.currentTimeMillis(),
                    error = "Position already exists"
                )
            }
            
            // Calculate position parameters
            val balance = service.getTotalBalanceUsd()
            val positionValue = balance * (config.positionSizePercent / 100.0)
            val leverage = calculateLeverage(signal)
            
            logger.info("💰 Opening position: ${signal.symbol}")
            logger.info("  📊 Balance: $$balance")
            logger.info("  💵 Position Value: $$positionValue")
            logger.info("  🎚️ Leverage: ${leverage}x")
            logger.info("  📈 Confidence: ${signal.confidence}")
            
            // Determine position side based on signal
            val side = if (signal.metadata["short"] == true) {
                PositionSide.SHORT
            } else {
                PositionSide.LONG
            }
            
            // Open position
            val result = service.openPosition(
                symbol = signal.symbol,
                side = side,
                size = positionValue,
                leverage = leverage
            )
            
            if (result.success) {
                logger.info("✅ Position opened: ${signal.symbol} at ${result.executedPrice}")
                
                // Set up risk management orders
                setupRiskManagement(signal.symbol, side, result.executedPrice!!, signal)
            }
            
            return result
        } catch (e: Exception) {
            logger.error("❌ Failed to execute buy for ${signal.symbol}", e)
            return HyperliquidTradeResult(
                success = false,
                orderId = null,
                executedPrice = null,
                executedSize = null,
                fee = null,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }

    private suspend fun executeSell(signal: StrategySignal): HyperliquidTradeResult {
        try {
            val position = activePositions[signal.symbol]
            if (position == null) {
                logger.warn("⚠️ No position to sell for ${signal.symbol}")
                return HyperliquidTradeResult(
                    success = false,
                    orderId = null,
                    executedPrice = null,
                    executedSize = null,
                    fee = null,
                    timestamp = System.currentTimeMillis(),
                    error = "No position found"
                )
            }
            
            logger.info("💰 Closing position: ${signal.symbol}")
            logger.info("  📊 Entry Price: ${position.entryPrice}")
            logger.info("  📈 Current Price: ${position.markPrice}")
            logger.info("  💵 Unrealized PnL: ${position.unrealizedPnl}")
            logger.info("  📉 Reason: ${signal.metadata["reason"] ?: "Strategy signal"}")
            
            // Close position
            val result = service.closePosition(
                symbol = signal.symbol,
                reason = signal.metadata["reason"] as? String ?: "Strategy signal"
            )
            
            if (result.success) {
                activePositions.remove(signal.symbol)
                positionPnL.remove(signal.symbol)
                logger.info("✅ Position closed: ${signal.symbol} at ${result.executedPrice}")
            }
            
            return result
        } catch (e: Exception) {
            logger.error("❌ Failed to execute sell for ${signal.symbol}", e)
            return HyperliquidTradeResult(
                success = false,
                orderId = null,
                executedPrice = null,
                executedSize = null,
                fee = null,
                timestamp = System.currentTimeMillis(),
                error = e.message
            )
        }
    }

    private suspend fun setupRiskManagement(
        symbol: String,
        side: PositionSide,
        entryPrice: Double,
        signal: StrategySignal
    ) {
        try {
            // Calculate stop loss price
            if (config.enableStopLoss) {
                val stopLossPercent = signal.metadata["stopLoss"] as? Double ?: config.stopLossPercent
                val stopPrice = if (side == PositionSide.LONG) {
                    entryPrice * (1 - stopLossPercent)
                } else {
                    entryPrice * (1 + stopLossPercent)
                }
                
                logger.info("🛑 Setting stop loss at $stopPrice (${stopLossPercent * 100}%)")
            }
            
            // Calculate take profit price
            if (config.enableTakeProfit) {
                val takeProfitPercent = signal.metadata["takeProfit"] as? Double ?: config.takeProfitPercent
                val tpPrice = if (side == PositionSide.LONG) {
                    entryPrice * (1 + takeProfitPercent)
                } else {
                    entryPrice * (1 - takeProfitPercent)
                }
                
                logger.info("🎯 Setting take profit at $tpPrice (${takeProfitPercent * 100}%)")
            }
            
            // Set up trailing stop if enabled
            if (config.enableTrailingStop) {
                logger.info("📉 Trailing stop enabled at ${config.trailingStopPercent * 100}%")
            }
        } catch (e: Exception) {
            logger.error("❌ Failed to setup risk management for $symbol", e)
        }
    }

    private fun calculateLeverage(signal: StrategySignal): Double {
        // Start with default leverage
        var leverage = config.defaultLeverage
        
        // Adjust based on confidence
        if (config.autoAdjustLeverage) {
            leverage = when {
                signal.confidence > 0.9 -> min(config.maxLeverage, leverage * 2.0)
                signal.confidence > 0.8 -> min(config.maxLeverage, leverage * 1.5)
                signal.confidence > 0.7 -> leverage
                signal.confidence > 0.6 -> max(1.0, leverage * 0.75)
                else -> max(1.0, leverage * 0.5)
            }
        }
        
        // Apply strategy-specific leverage
        val strategyLeverage = signal.metadata["leverage"] as? Double
        if (strategyLeverage != null) {
            leverage = min(config.maxLeverage, strategyLeverage)
        }
        
        return leverage
    }

    // =================================================================================================
    // POSITION MONITORING
    // =================================================================================================

    private suspend fun monitorPositions() {
        while (isRunning.get()) {
            try {
                // Fetch latest positions
                val positions = service.fetchPositions()
                
                activePositions.clear()
                positions.forEach { position ->
                    activePositions[position.symbol] = position
                    
                    // Check for auto-close conditions
                    checkAutoClose(position)
                    
                    // Update trailing stop if needed
                    if (config.enableTrailingStop) {
                        updateTrailingStop(position)
                    }
                }
                
                delay(POSITION_CHECK_INTERVAL)
            } catch (e: Exception) {
                logger.error("❌ Error monitoring positions", e)
                delay(POSITION_CHECK_INTERVAL * 2)
            }
        }
    }

    private suspend fun checkAutoClose(position: HyperliquidPosition) {
        try {
            val pnlPercent = position.unrealizedPnl / position.margin
            
            // Check auto-close on profit
            if (config.autoCloseOnProfit > 0 && pnlPercent >= config.autoCloseOnProfit) {
                logger.info("💰 Auto-closing position on profit: ${position.symbol} (${pnlPercent * 100}%)")
                service.closePosition(position.symbol, "Auto-close on profit")
            }
            
            // Check auto-close on loss
            if (config.autoCloseOnLoss > 0 && pnlPercent <= -config.autoCloseOnLoss) {
                logger.warn("🛑 Auto-closing position on loss: ${position.symbol} (${pnlPercent * 100}%)")
                service.closePosition(position.symbol, "Auto-close on loss")
            }
            
            // Check liquidation risk
            if (position.liquidationPrice != null) {
                val priceToLiquidation = abs(position.markPrice - position.liquidationPrice) / position.markPrice
                if (priceToLiquidation < 0.05) { // Within 5% of liquidation
                    logger.error("⚠️ LIQUIDATION RISK: ${position.symbol} - Closing position")
                    service.closePosition(position.symbol, "Liquidation risk")
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Error checking auto-close for ${position.symbol}", e)
        }
    }

    private suspend fun updateTrailingStop(position: HyperliquidPosition) {
        try {
            val pnl = positionPnL[position.symbol] ?: return
            
            // Only update trailing stop if in profit
            if (position.unrealizedPnl > 0) {
                val currentPrice = position.markPrice
                val trailingPercent = config.trailingStopPercent
                
                // Update highest price for long, lowest for short
                val updatedPnL = if (position.side == PositionSide.LONG) {
                    if (currentPrice > pnl.highestPrice) {
                        pnl.copy(
                            highestPrice = currentPrice,
                            currentPrice = currentPrice,
                            updateTime = System.currentTimeMillis()
                        )
                    } else pnl
                } else {
                    if (currentPrice < pnl.lowestPrice) {
                        pnl.copy(
                            lowestPrice = currentPrice,
                            currentPrice = currentPrice,
                            updateTime = System.currentTimeMillis()
                        )
                    } else pnl
                }
                
                positionPnL[position.symbol] = updatedPnL
                
                // Check if trailing stop should trigger
                val stopPrice = if (position.side == PositionSide.LONG) {
                    updatedPnL.highestPrice * (1 - trailingPercent)
                } else {
                    updatedPnL.lowestPrice * (1 + trailingPercent)
                }
                
                val shouldTrigger = if (position.side == PositionSide.LONG) {
                    currentPrice <= stopPrice
                } else {
                    currentPrice >= stopPrice
                }
                
                if (shouldTrigger) {
                    logger.info("📉 Trailing stop triggered for ${position.symbol} at $currentPrice")
                    service.closePosition(position.symbol, "Trailing stop")
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Error updating trailing stop for ${position.symbol}", e)
        }
    }

    // =================================================================================================
    // BALANCE MONITORING
    // =================================================================================================

    private suspend fun monitorBalances() {
        while (isRunning.get()) {
            try {
                val balances = service.fetchBalances()
                val totalUsd = service.getTotalBalanceUsd()
                
                logger.info("💰 Account Balance Update:")
                logger.info("  📊 Total USD Value: $$totalUsd")
                
                balances.forEach { (asset, balance) ->
                    if (balance.total > 0) {
                        logger.info("  💵 $asset: ${balance.total} ($$${balance.usdValue})")
                    }
                }
                
                // Check margin health
                val marginBalance = balances["USDC"]
                if (marginBalance != null) {
                    val marginUsed = marginBalance.used / marginBalance.total
                    if (marginUsed > 0.8) {
                        logger.warn("⚠️ High margin usage: ${marginUsed * 100}%")
                    }
                }
                
                delay(BALANCE_UPDATE_INTERVAL)
            } catch (e: Exception) {
                logger.error("❌ Error monitoring balances", e)
                delay(BALANCE_UPDATE_INTERVAL * 2)
            }
        }
    }

    // =================================================================================================
    // PNL MONITORING
    // =================================================================================================

    private suspend fun monitorPnL() {
        while (isRunning.get()) {
            try {
                var totalUnrealizedPnL = 0.0
                var totalRealizedPnL = 0.0
                
                activePositions.values.forEach { position ->
                    val ticker = service.getTicker(position.symbol)
                    val currentPrice = ticker?.get("last") as? Double ?: position.markPrice
                    
                    val pnl = PositionPnL(
                        symbol = position.symbol,
                        entryPrice = position.entryPrice,
                        currentPrice = currentPrice,
                        unrealizedPnl = position.unrealizedPnl,
                        realizedPnl = position.realizedPnl,
                        pnlPercent = position.unrealizedPnl / position.margin,
                        highestPrice = positionPnL[position.symbol]?.highestPrice ?: currentPrice,
                        lowestPrice = positionPnL[position.symbol]?.lowestPrice ?: currentPrice,
                        updateTime = System.currentTimeMillis()
                    )
                    
                    positionPnL[position.symbol] = pnl
                    totalUnrealizedPnL += position.unrealizedPnl
                    totalRealizedPnL += position.realizedPnl
                    
                    if (config.logLevel == LogLevel.DEBUG) {
                        logger.debug("📊 ${position.symbol}: PnL ${pnl.pnlPercent * 100}% ($$${position.unrealizedPnl})")
                    }
                }
                
                if (activePositions.isNotEmpty()) {
                    logger.info("💹 Total PnL - Unrealized: $$totalUnrealizedPnL, Realized: $$totalRealizedPnL")
                }
                
                delay(PNL_CHECK_INTERVAL)
            } catch (e: Exception) {
                logger.error("❌ Error monitoring PnL", e)
                delay(PNL_CHECK_INTERVAL * 2)
            }
        }
    }

    // =================================================================================================
    // EVENT HANDLERS
    // =================================================================================================

    private fun handlePositionUpdate(position: HyperliquidPosition) {
        try {
            activePositions[position.symbol] = position
            logger.debug("📊 Position updated: ${position.symbol}")
        } catch (e: Exception) {
            logger.error("❌ Error handling position update", e)
        }
    }

    private fun handleOrderUpdate(order: HyperliquidOrder) {
        try {
            when (order.status) {
                com.bswap.server.config.OrderStatus.FILLED -> {
                    logger.info("✅ Order filled: ${order.symbol} ${order.side} ${order.filled} @ ${order.averagePrice}")
                }
                com.bswap.server.config.OrderStatus.PARTIALLY_FILLED -> {
                    logger.info("⏳ Order partially filled: ${order.symbol} ${order.filled}/${order.size}")
                }
                com.bswap.server.config.OrderStatus.CANCELLED -> {
                    logger.info("❌ Order cancelled: ${order.symbol}")
                }
                com.bswap.server.config.OrderStatus.REJECTED -> {
                    logger.error("🚫 Order rejected: ${order.symbol}")
                }
                else -> {
                    logger.debug("📋 Order update: ${order.symbol} - ${order.status}")
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Error handling order update", e)
        }
    }

    private fun handleTradeResult(trade: HyperliquidTradeResult) {
        try {
            if (trade.success) {
                logger.info("✅ Trade executed: Order ${trade.orderId} - ${trade.executedSize} @ ${trade.executedPrice}")
            } else {
                logger.error("❌ Trade failed: ${trade.error}")
            }
        } catch (e: Exception) {
            logger.error("❌ Error handling trade result", e)
        }
    }

    // =================================================================================================
    // PUBLIC API
    // =================================================================================================

    suspend fun getActivePositions(): List<HyperliquidPosition> {
        return activePositions.values.toList()
    }

    suspend fun getPositionPnL(symbol: String): PositionPnL? {
        return positionPnL[symbol]
    }

    suspend fun getTotalPnL(): Pair<Double, Double> {
        val unrealized = activePositions.values.sumOf { it.unrealizedPnl }
        val realized = activePositions.values.sumOf { it.realizedPnl }
        return Pair(unrealized, realized)
    }

    suspend fun closePosition(symbol: String, reason: String = "Manual"): HyperliquidTradeResult {
        return service.closePosition(symbol, reason)
    }

    suspend fun closeAllPositions(reason: String = "Manual"): List<HyperliquidTradeResult> {
        logger.info("🔒 Closing all positions: $reason")
        return service.closeAllPositions(reason)
    }

    suspend fun emergencyStop(reason: String = "Emergency") {
        logger.error("🚨 EMERGENCY STOP TRIGGERED: $reason")
        
        // Cancel all orders
        service.cancelAllOrders()
        
        // Close all positions
        closeAllPositions(reason)
        
        // Stop the engine
        stop()
    }

    suspend fun getStats(): Map<String, Any> {
        val (unrealizedPnL, realizedPnL) = getTotalPnL()
        return mapOf(
            "isRunning" to isRunning.get(),
            "activePositions" to activePositions.size,
            "maxPositions" to config.maxPositions,
            "totalUnrealizedPnL" to unrealizedPnL,
            "totalRealizedPnL" to realizedPnL,
            "accountBalance" to service.getTotalBalanceUsd(),
            "serviceStats" to service.getStats()
        )
    }

    fun shutdown() {
        stop()
        service.shutdown()
    }
}