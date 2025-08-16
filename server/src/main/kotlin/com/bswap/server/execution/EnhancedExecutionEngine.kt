package com.bswap.server.execution

import com.bswap.server.config.*
import com.bswap.server.service.JupiterLiquidityService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Enhanced execution engine with idempotent operations and graceful degradation
 * Preserves all original log message texts while adding robust error handling
 */

// =================================================================================================
// IDEMPOTENT ORDER MANAGEMENT
// =================================================================================================

data class OrderRequest(
    val id: String,
    val mint: String,
    val side: OrderSide,
    val amount: Double,
    val maxSlippage: Double,
    val timeoutMs: Long,
    val retryCount: Int = 3,
    val priority: OrderPriority = OrderPriority.NORMAL,
    val createdAt: Long = System.currentTimeMillis()
)

enum class OrderSide { BUY, SELL }

enum class OrderPriority { LOW, NORMAL, HIGH, EMERGENCY }

enum class OrderStatus {
    PENDING,        // Order created but not yet submitted
    SUBMITTED,      // Order submitted to execution
    PARTIAL,        // Partially filled
    FILLED,         // Completely filled
    CANCELLED,      // Cancelled by user/system
    FAILED,         // Failed to execute
    EXPIRED         // Expired due to timeout
}

data class OrderResult(
    val orderId: String,
    val status: OrderStatus,
    val executedAmount: Double,
    val executedPrice: Double,
    val fees: Double,
    val slippage: Double,
    val latencyMs: Long,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class IdempotentOrderManager(
    private val config: ExecutionConfig
) {
    companion object {
        private val logger = LoggerFactory.getLogger(IdempotentOrderManager::class.java)
    }

    private val activeOrders = ConcurrentHashMap<String, OrderRequest>()
    private val orderResults = ConcurrentHashMap<String, OrderResult>()
    private val orderMutexes = ConcurrentHashMap<String, Mutex>()
    private val executionCount = AtomicLong(0)

    /**
     * Submit order with idempotent behavior - same ID will return existing result
     */
    suspend fun submitOrder(request: OrderRequest): OrderResult {
        val mutex = orderMutexes.computeIfAbsent(request.id) { Mutex() }
        
        return mutex.withLock {
            // Check if order already exists
            val existingResult = orderResults[request.id]
            if (existingResult != null) {
                logger.debug("📋 IDEMPOTENT ORDER: ${request.id} already exists with status ${existingResult.status}")
                return@withLock existingResult
            }

            // Check if order is currently being processed
            val activeOrder = activeOrders[request.id]
            if (activeOrder != null) {
                logger.debug("⏳ ORDER PROCESSING: ${request.id} already in progress")
                return@withLock OrderResult(
                    orderId = request.id,
                    status = OrderStatus.PENDING,
                    executedAmount = 0.0,
                    executedPrice = 0.0,
                    fees = 0.0,
                    slippage = 0.0,
                    latencyMs = 0L
                )
            }

            // Add to active orders
            activeOrders[request.id] = request
            logger.info("📝 ORDER SUBMITTED: ${request.id} - ${request.side} ${request.mint}")

            // Return pending status immediately
            val pendingResult = OrderResult(
                orderId = request.id,
                status = OrderStatus.PENDING,
                executedAmount = 0.0,
                executedPrice = 0.0,
                fees = 0.0,
                slippage = 0.0,
                latencyMs = 0L
            )
            
            orderResults[request.id] = pendingResult
            pendingResult
        }
    }

    /**
     * Get order status (idempotent)
     */
    fun getOrderStatus(orderId: String): OrderResult? {
        return orderResults[orderId]
    }

    /**
     * Cancel order if still pending/submitted
     */
    suspend fun cancelOrder(orderId: String): Boolean {
        val mutex = orderMutexes[orderId] ?: return false
        
        return mutex.withLock {
            val order = activeOrders[orderId]
            val result = orderResults[orderId]
            
            if (order != null && result?.status in listOf(OrderStatus.PENDING, OrderStatus.SUBMITTED)) {
                val cancelledResult = result?.copy(
                    status = OrderStatus.CANCELLED,
                    timestamp = System.currentTimeMillis()
                )
                if (cancelledResult != null) {
                    orderResults[orderId] = cancelledResult
                }
                activeOrders.remove(orderId)
                logger.info("❌ ORDER CANCELLED: $orderId")
                true
            } else {
                false
            }
        }
    }

    /**
     * Update order result (internal use)
     */
    internal suspend fun updateOrderResult(orderId: String, result: OrderResult) {
        val mutex = orderMutexes[orderId] ?: return
        
        mutex.withLock {
            orderResults[orderId] = result
            if (result.status in listOf(OrderStatus.FILLED, OrderStatus.FAILED, OrderStatus.CANCELLED, OrderStatus.EXPIRED)) {
                activeOrders.remove(orderId)
            }
        }
    }

    /**
     * Cleanup old orders
     */
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - 3600_000L // 1 hour
        val toRemove = orderResults.filter { it.value.timestamp < cutoff }.keys
        
        toRemove.forEach { orderId ->
            orderResults.remove(orderId)
            orderMutexes.remove(orderId)
        }
        
        if (toRemove.isNotEmpty()) {
            logger.debug("🧹 CLEANUP: Removed ${toRemove.size} old orders")
        }
    }

    fun getStats(): Map<String, Any> {
        return mapOf(
            "activeOrders" to activeOrders.size,
            "totalOrderResults" to orderResults.size,
            "executionCount" to executionCount.get(),
            "statusBreakdown" to orderResults.values.groupingBy { it.status }.eachCount()
        )
    }
}

// =================================================================================================
// GRACEFUL DEGRADATION MANAGER
// =================================================================================================

class GracefulDegradationManager(
    private val config: EnhancedTradingConfig,
    private val liquidityService: JupiterLiquidityService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(GracefulDegradationManager::class.java)
    }

    private val degradationLevel = ConcurrentHashMap<String, DegradationLevel>()
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    private val lastSuccess = ConcurrentHashMap<String, Long>()

    enum class DegradationLevel {
        NORMAL,         // Full functionality
        CAUTIOUS,       // Reduced position sizes, tighter stops
        CONSERVATIVE,   // Only high-confidence trades
        MINIMAL,        // Emergency mode - sell only
        EMERGENCY       // Force sell everything
    }

    data class TradingRecommendation(
        val allowTrading: Boolean,
        val positionSizeMultiplier: Double,
        val stopLossMultiplier: Double,
        val confidenceThreshold: Double,
        val reason: String
    )

    /**
     * Assess current market conditions and recommend trading approach
     */
    suspend fun assessTradingConditions(mint: String): TradingRecommendation {
        val currentLevel = getCurrentDegradationLevel(mint)
        
        return when (currentLevel) {
            DegradationLevel.NORMAL -> TradingRecommendation(
                allowTrading = true,
                positionSizeMultiplier = 1.0,
                stopLossMultiplier = 1.0,
                confidenceThreshold = 0.6,
                reason = "Normal market conditions"
            )
            DegradationLevel.CAUTIOUS -> TradingRecommendation(
                allowTrading = true,
                positionSizeMultiplier = 0.7,
                stopLossMultiplier = 0.8,
                confidenceThreshold = 0.7,
                reason = "Cautious mode - some errors detected"
            )
            DegradationLevel.CONSERVATIVE -> TradingRecommendation(
                allowTrading = true,
                positionSizeMultiplier = 0.4,
                stopLossMultiplier = 0.6,
                confidenceThreshold = 0.8,
                reason = "Conservative mode - elevated risk"
            )
            DegradationLevel.MINIMAL -> TradingRecommendation(
                allowTrading = false,
                positionSizeMultiplier = 0.0,
                stopLossMultiplier = 0.5,
                confidenceThreshold = 0.9,
                reason = "Minimal mode - sell only"
            )
            DegradationLevel.EMERGENCY -> TradingRecommendation(
                allowTrading = false,
                positionSizeMultiplier = 0.0,
                stopLossMultiplier = 0.0,
                confidenceThreshold = 1.0,
                reason = "Emergency mode - force sell all positions"
            )
        }
    }

    /**
     * Record successful operation
     */
    fun recordSuccess(mint: String) {
        lastSuccess[mint] = System.currentTimeMillis()
        errorCounts[mint]?.set(0) // Reset error count on success
        
        // Potentially improve degradation level
        val current = degradationLevel[mint]
        if (current != null && current != DegradationLevel.NORMAL) {
            val newLevel = improveDegradationLevel(current)
            if (newLevel != current) {
                degradationLevel[mint] = newLevel
                logger.info("📈 DEGRADATION IMPROVED: $mint -> $newLevel")
            }
        }
    }

    /**
     * Record error and potentially degrade service level
     */
    fun recordError(mint: String, error: Exception) {
        val errorCount = errorCounts.computeIfAbsent(mint) { AtomicLong(0) }
        val newCount = errorCount.incrementAndGet()
        
        logger.warn("⚠️ ERROR RECORDED: $mint - Count: $newCount, Error: ${error.message}")
        
        // Determine if degradation is needed
        val newLevel = calculateDegradationLevel(mint, newCount)
        val currentLevel = degradationLevel[mint] ?: DegradationLevel.NORMAL
        
        if (newLevel != currentLevel) {
            degradationLevel[mint] = newLevel
            logger.warn("📉 DEGRADATION LEVEL CHANGED: $mint -> $newLevel (${error.javaClass.simpleName})")
        }
    }

    /**
     * Handle missing price data gracefully
     */
    suspend fun handleMissingPrice(mint: String): PriceHandlingStrategy {
        val degradationLevel = getCurrentDegradationLevel(mint)
        val timeSinceLastPrice = getTimeSinceLastSuccess(mint)
        
        return when {
            // If we have recent price data, use cached value
            timeSinceLastPrice < 30_000L && degradationLevel == DegradationLevel.NORMAL -> {
                PriceHandlingStrategy.USE_CACHED
            }
            // If degradation level is high, force sell
            degradationLevel in listOf(DegradationLevel.MINIMAL, DegradationLevel.EMERGENCY) -> {
                logger.warn("⚠️ MISSING PRICE: $mint - Force sell due to degradation level")
                PriceHandlingStrategy.FORCE_SELL
            }
            // If we have backup price sources, try them
            config.enableGracefulDegradation -> {
                val backupPrice = tryBackupPriceSources(mint)
                if (backupPrice != null) {
                    PriceHandlingStrategy.USE_BACKUP
                } else {
                    PriceHandlingStrategy.PAUSE_TRADING
                }
            }
            // Otherwise pause trading
            else -> {
                logger.warn("⚠️ MISSING PRICE: $mint - Pausing trading")
                PriceHandlingStrategy.PAUSE_TRADING
            }
        }
    }

    enum class PriceHandlingStrategy {
        USE_CACHED,     // Use last known price
        USE_BACKUP,     // Use backup price source
        FORCE_SELL,     // Sell at any price
        PAUSE_TRADING   // Stop trading this token
    }

    private fun getCurrentDegradationLevel(mint: String): DegradationLevel {
        return degradationLevel[mint] ?: DegradationLevel.NORMAL
    }

    private fun calculateDegradationLevel(mint: String, errorCount: Long): DegradationLevel {
        val timeSinceLastSuccess = getTimeSinceLastSuccess(mint)
        
        return when {
            errorCount >= 10 || timeSinceLastSuccess > 300_000L -> DegradationLevel.EMERGENCY
            errorCount >= 7 || timeSinceLastSuccess > 180_000L -> DegradationLevel.MINIMAL
            errorCount >= 5 || timeSinceLastSuccess > 120_000L -> DegradationLevel.CONSERVATIVE
            errorCount >= 3 || timeSinceLastSuccess > 60_000L -> DegradationLevel.CAUTIOUS
            else -> DegradationLevel.NORMAL
        }
    }

    private fun improveDegradationLevel(current: DegradationLevel): DegradationLevel {
        return when (current) {
            DegradationLevel.EMERGENCY -> DegradationLevel.MINIMAL
            DegradationLevel.MINIMAL -> DegradationLevel.CONSERVATIVE
            DegradationLevel.CONSERVATIVE -> DegradationLevel.CAUTIOUS
            DegradationLevel.CAUTIOUS -> DegradationLevel.NORMAL
            DegradationLevel.NORMAL -> DegradationLevel.NORMAL
        }
    }

    private fun getTimeSinceLastSuccess(mint: String): Long {
        val lastSuccessTime = lastSuccess[mint] ?: 0L
        return System.currentTimeMillis() - lastSuccessTime
    }

    private suspend fun tryBackupPriceSources(mint: String): Double? {
        return try {
            // Try to get price from liquidity service
            val analysis = liquidityService.analyzeLiquidity(mint)
            // This is a placeholder - in reality you'd extract price from the analysis
            null
        } catch (e: Exception) {
            logger.debug("Backup price source failed: ${e.message}")
            null
        }
    }

    fun getDegradationLevelCount(): Int {
        return degradationLevel.size
    }
}

// =================================================================================================
// ENHANCED EXECUTION ENGINE
// =================================================================================================

class EnhancedExecutionEngine(
    private val config: EnhancedTradingConfig,
    private val liquidityService: JupiterLiquidityService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(EnhancedExecutionEngine::class.java)
    }

    private val orderManager = IdempotentOrderManager(config.execution)
    private val degradationManager = GracefulDegradationManager(config, liquidityService)
    private val executionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeExecutions = ConcurrentHashMap<String, Job>()

    /**
     * Execute buy order with full protection and idempotency
     */
    suspend fun executeBuy(
        mint: String,
        amountUsd: Double,
        maxSlippage: Double = config.slippage.maxSlippagePercent / 100.0
    ): OrderResult {
        val orderId = generateOrderId("BUY", mint)
        val request = OrderRequest(
            id = orderId,
            mint = mint,
            side = OrderSide.BUY,
            amount = amountUsd,
            maxSlippage = maxSlippage,
            timeoutMs = config.execution.rpcTimeoutMs,
            priority = OrderPriority.NORMAL
        )

        // Submit order (idempotent)
        val initialResult = orderManager.submitOrder(request)
        if (initialResult.status != OrderStatus.PENDING) {
            return initialResult
        }

        // Start execution asynchronously
        val executionJob = executionScope.launch {
            executeOrderWithProtection(request)
        }
        
        activeExecutions[orderId] = executionJob

        return initialResult
    }

    /**
     * Execute sell order with full protection and idempotency
     */
    suspend fun executeSell(
        mint: String,
        amountTokens: Double = 0.0, // 0 means sell all
        maxSlippage: Double = config.slippage.maxSlippagePercent / 100.0,
        reason: String = "Manual"
    ): OrderResult {
        val orderId = generateOrderId("SELL", mint)
        val request = OrderRequest(
            id = orderId,
            mint = mint,
            side = OrderSide.SELL,
            amount = amountTokens,
            maxSlippage = maxSlippage,
            timeoutMs = config.execution.rpcTimeoutMs,
            priority = if (reason.contains("emergency", true)) OrderPriority.EMERGENCY else OrderPriority.NORMAL
        )

        // Submit order (idempotent)
        val initialResult = orderManager.submitOrder(request)
        if (initialResult.status != OrderStatus.PENDING) {
            return initialResult
        }

        // Start execution asynchronously
        val executionJob = executionScope.launch {
            executeOrderWithProtection(request)
        }
        
        activeExecutions[orderId] = executionJob

        return initialResult
    }

    /**
     * Execute order with comprehensive protection mechanisms
     */
    private suspend fun executeOrderWithProtection(request: OrderRequest) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Check trading conditions
            val recommendation = degradationManager.assessTradingConditions(request.mint)
            
            if (!recommendation.allowTrading && request.side == OrderSide.BUY) {
                val result = OrderResult(
                    orderId = request.id,
                    status = OrderStatus.CANCELLED,
                    executedAmount = 0.0,
                    executedPrice = 0.0,
                    fees = 0.0,
                    slippage = 0.0,
                    latencyMs = System.currentTimeMillis() - startTime,
                    errorMessage = "Trading not allowed: ${recommendation.reason}"
                )
                orderManager.updateOrderResult(request.id, result)
                logger.warn("❌ ORDER CANCELLED: ${request.id} - ${recommendation.reason}")
                return
            }

            // Pre-trade liquidity validation
            if (config.liquidityProtection.enablePreTradeValidation && request.side == OrderSide.BUY) {
                val liquidityValid = liquidityService.validateTrade(request.mint, request.amount)
                if (!liquidityValid) {
                    val result = OrderResult(
                        orderId = request.id,
                        status = OrderStatus.FAILED,
                        executedAmount = 0.0,
                        executedPrice = 0.0,
                        fees = 0.0,
                        slippage = 0.0,
                        latencyMs = System.currentTimeMillis() - startTime,
                        errorMessage = "Liquidity validation failed"
                    )
                    orderManager.updateOrderResult(request.id, result)
                    logger.error("❌ LIQUIDITY CHECK: ${request.id} failed validation")
                    return
                }
            }

            // Update order status to submitted
            orderManager.updateOrderResult(request.id, OrderResult(
                orderId = request.id,
                status = OrderStatus.SUBMITTED,
                executedAmount = 0.0,
                executedPrice = 0.0,
                fees = 0.0,
                slippage = 0.0,
                latencyMs = 0L
            ))

            // Execute the actual trade
            val result = executeTradeWithRetry(request, recommendation)
            
            // Update final result
            orderManager.updateOrderResult(request.id, result)
            
            if (result.status == OrderStatus.FILLED) {
                degradationManager.recordSuccess(request.mint)
                logger.info("✅ ORDER FILLED: ${request.id} - Amount: ${result.executedAmount}, Price: ${result.executedPrice}")
            } else {
                logger.error("❌ ORDER FAILED: ${request.id} - ${result.errorMessage}")
            }

        } catch (e: Exception) {
            degradationManager.recordError(request.mint, e)
            
            val result = OrderResult(
                orderId = request.id,
                status = OrderStatus.FAILED,
                executedAmount = 0.0,
                executedPrice = 0.0,
                fees = 0.0,
                slippage = 0.0,
                latencyMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
            orderManager.updateOrderResult(request.id, result)
            logger.error("❌ ORDER EXCEPTION: ${request.id} - ${e.message}", e)
            
        } finally {
            activeExecutions.remove(request.id)
        }
    }

    private suspend fun executeTradeWithRetry(
        request: OrderRequest,
        recommendation: GracefulDegradationManager.TradingRecommendation
    ): OrderResult {
        var lastException: Exception? = null
        
        repeat(request.retryCount) { attempt ->
            try {
                // Simulate trade execution (in real implementation, this would call Jupiter/Jito)
                delay(100) // Simulate network latency
                
                // Calculate adjusted amount based on degradation
                val adjustedAmount = request.amount * recommendation.positionSizeMultiplier
                
                // Simulate execution result
                val executedPrice = 1.0 // Placeholder
                val slippage = 0.01 // Placeholder
                val fees = adjustedAmount * 0.001 // 0.1% fee
                
                return OrderResult(
                    orderId = request.id,
                    status = OrderStatus.FILLED,
                    executedAmount = adjustedAmount,
                    executedPrice = executedPrice,
                    fees = fees,
                    slippage = slippage,
                    latencyMs = 100L + attempt * 50L
                )
                
            } catch (e: Exception) {
                lastException = e
                logger.warn("⚠️ RETRY ATTEMPT: ${request.id} attempt ${attempt + 1}/${request.retryCount} failed: ${e.message}")
                
                if (attempt < request.retryCount - 1) {
                    delay(config.execution.retryDelayMs * (attempt + 1)) // Exponential backoff
                }
            }
        }
        
        // All retries failed
        return OrderResult(
            orderId = request.id,
            status = OrderStatus.FAILED,
            executedAmount = 0.0,
            executedPrice = 0.0,
            fees = 0.0,
            slippage = 0.0,
            latencyMs = 0L,
            errorMessage = "All retries failed: ${lastException?.message}"
        )
    }

    private fun generateOrderId(side: String, mint: String): String {
        val timestamp = System.currentTimeMillis()
        val shortMint = mint.takeLast(8)
        return "${side}_${shortMint}_$timestamp"
    }

    /**
     * Get order status (idempotent)
     */
    fun getOrderStatus(orderId: String): OrderResult? {
        return orderManager.getOrderStatus(orderId)
    }

    /**
     * Cancel order
     */
    suspend fun cancelOrder(orderId: String): Boolean {
        // Cancel the execution job if still running
        activeExecutions[orderId]?.cancel()
        activeExecutions.remove(orderId)
        
        return orderManager.cancelOrder(orderId)
    }

    /**
     * Handle missing price data according to degradation policy
     */
    suspend fun handleMissingPrice(mint: String): GracefulDegradationManager.PriceHandlingStrategy {
        return degradationManager.handleMissingPrice(mint)
    }

    /**
     * Get execution statistics
     */
    fun getExecutionStats(): Map<String, Any> {
        return mapOf(
            "orderStats" to orderManager.getStats(),
            "activeExecutions" to activeExecutions.size,
            "degradationLevels" to degradationManager.getDegradationLevelCount()
        )
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        orderManager.cleanup()
    }

    /**
     * Shutdown execution engine
     */
    fun shutdown() {
        executionScope.cancel()
        activeExecutions.clear()
    }
}