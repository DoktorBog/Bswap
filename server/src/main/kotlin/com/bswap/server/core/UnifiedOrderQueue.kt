package com.bswap.server.core

import com.bswap.server.service.PriceHistoryLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Order types for unified queue
 */
enum class OrderType {
    BUY,
    SELL
}

/**
 * Order status
 */
enum class OrderStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Unified order structure for buy/sell operations
 */
@Serializable
data class UnifiedOrder(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: OrderType,
    val mint: String,
    @Contextual val amount: BigDecimal? = null, // Optional amount (for partial sells)
    val reason: String = "strategy",
    val timestamp: Long = System.currentTimeMillis(),
    val priority: Int = 0, // Higher = more urgent
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Order result
 */
sealed class OrderResult {
    data class Success(
        val orderId: String,
        val transactionId: String? = null,
        val executedAmount: BigDecimal? = null,
        val executionTime: Long = System.currentTimeMillis()
    ) : OrderResult()
    
    data class Failure(
        val orderId: String,
        val error: String,
        val isRetryable: Boolean = true,
        val executionTime: Long = System.currentTimeMillis()
    ) : OrderResult()
}

/**
 * Configuration for unified order queue
 */
data class UnifiedOrderQueueConfig(
    val buyWorkers: Int = 2,
    val sellWorkers: Int = 2,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1500L,
    val buySpacingMs: Long = 200L,
    val sellSpacingMs: Long = 400L,
    val maxQueueSize: Int = 1000,
    val priorityQueueEnabled: Boolean = true
)

/**
 * Unified order queue for managing both buy and sell operations
 * with separate worker pools and configurable processing
 */
class UnifiedOrderQueue(
    private val config: UnifiedOrderQueueConfig,
    private val buyExecutor: suspend (UnifiedOrder) -> OrderResult,
    private val sellExecutor: suspend (UnifiedOrder) -> OrderResult,
    private val priceHistoryLoader: PriceHistoryLoader? = null
) {
    private val log = LoggerFactory.getLogger("UnifiedOrderQueue")
    
    // Separate channels for buy and sell orders
    private val buyChannel = Channel<UnifiedOrder>(Channel.UNLIMITED)
    private val sellChannel = Channel<UnifiedOrder>(Channel.UNLIMITED)
    
    // Worker coroutines
    private val buyWorkers = mutableListOf<Job>()
    private val sellWorkers = mutableListOf<Job>()
    
    // State tracking
    private val isRunning = AtomicBoolean(false)
    private val activeOrders = ConcurrentHashMap<String, UnifiedOrder>()
    private val orderHistory = ConcurrentHashMap<String, OrderResult>()
    
    // Metrics
    private val metrics = OrderQueueMetrics()
    
    // Price history cache for RSI calculations
    private val priceHistoryCache = ConcurrentHashMap<String, PriceHistory>()
    
    // Separate buy/sell processing state
    private val buyProcessingState = OrderProcessingState("BUY")
    private val sellProcessingState = OrderProcessingState("SELL")
    
    /**
     * Start the order queue
     */
    suspend fun start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("Starting UnifiedOrderQueue with ${config.buyWorkers} buy workers and ${config.sellWorkers} sell workers")
            
            // Start buy workers
            repeat(config.buyWorkers) { workerId ->
                buyWorkers.add(
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        runBuyWorker(workerId)
                    }
                )
            }
            
            // Start sell workers
            repeat(config.sellWorkers) { workerId ->
                sellWorkers.add(
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        runSellWorker(workerId)
                    }
                )
            }
            
            log.info("UnifiedOrderQueue started successfully")
        }
    }
    
    /**
     * Stop the order queue gracefully
     */
    suspend fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("Stopping UnifiedOrderQueue...")
            
            // Close channels
            buyChannel.close()
            sellChannel.close()
            
            // Cancel all workers
            (buyWorkers + sellWorkers).forEach { it.cancelAndJoin() }
            buyWorkers.clear()
            sellWorkers.clear()
            
            log.info("UnifiedOrderQueue stopped")
        }
    }
    
    /**
     * Enqueue an order (buy or sell)
     */
    suspend fun enqueue(order: UnifiedOrder): Boolean {
        if (!isRunning.get()) {
            log.warn("Cannot enqueue order ${order.id}: queue not running")
            return false
        }
        
        // Check queue size limits
        val currentSize = activeOrders.size
        if (currentSize >= config.maxQueueSize) {
            log.error("Queue full (${currentSize}/${config.maxQueueSize}), rejecting order ${order.id}")
            return false
        }
        
        // Add to active orders
        activeOrders[order.id] = order
        
        // Route to appropriate channel
        val channel = when (order.type) {
            OrderType.BUY -> buyChannel
            OrderType.SELL -> sellChannel
        }
        
        return try {
            val result = channel.trySend(order)
            if (result.isSuccess) {
                metrics.recordEnqueue(order)
                log.info("📥 Enqueued ${order.type} order: ${order.mint} (id=${order.id}, reason=${order.reason})")
                
                // Load price history asynchronously after buy orders complete
                if (order.type == OrderType.BUY) {
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(1000) // Wait for buy to complete
                        loadPriceHistoryForToken(order.mint)
                    }
                }
                true
            } else {
                activeOrders.remove(order.id)
                log.error("Failed to enqueue order ${order.id}: ${result.exceptionOrNull()?.message}")
                false
            }
        } catch (e: Exception) {
            activeOrders.remove(order.id)
            log.error("Exception enqueuing order ${order.id}: ${e.message}")
            false
        }
    }
    
    /**
     * Cancel an order if it hasn't been processed yet
     */
    fun cancelOrder(orderId: String): Boolean {
        val order = activeOrders.remove(orderId)
        return if (order != null) {
            log.info("Cancelled order $orderId")
            true
        } else {
            false
        }
    }
    
    /**
     * Get buy queue specific stats
     */
    fun getBuyQueueStats(): Map<String, Any> {
        return buyProcessingState.getStats() + mapOf(
            "queueSize" to activeOrders.values.count { it.type == OrderType.BUY },
            "successRate" to if (metrics.totalBuys.get() > 0) 
                (metrics.successfulBuys.get().toDouble() / metrics.totalBuys.get()) else 0.0
        )
    }
    
    /**
     * Get sell queue specific stats
     */
    fun getSellQueueStats(): Map<String, Any> {
        return sellProcessingState.getStats() + mapOf(
            "queueSize" to activeOrders.values.count { it.type == OrderType.SELL },
            "successRate" to if (metrics.totalSells.get() > 0) 
                (metrics.successfulSells.get().toDouble() / metrics.totalSells.get()) else 0.0
        )
    }
    
    /**
     * Get queue statistics
     */
    fun getStats(): OrderQueueStats {
        val buyQueueSize = buyChannel.isEmpty.let { if (it) 0 else activeOrders.values.count { o -> o.type == OrderType.BUY } }
        val sellQueueSize = sellChannel.isEmpty.let { if (it) 0 else activeOrders.values.count { o -> o.type == OrderType.SELL } }
        
        return OrderQueueStats(
            buyQueueSize = buyQueueSize,
            sellQueueSize = sellQueueSize,
            activeOrders = activeOrders.size,
            totalEnqueued = metrics.totalEnqueued.get(),
            totalBuys = metrics.totalBuys.get(),
            totalSells = metrics.totalSells.get(),
            successfulBuys = metrics.successfulBuys.get(),
            successfulSells = metrics.successfulSells.get(),
            failedBuys = metrics.failedBuys.get(),
            failedSells = metrics.failedSells.get()
        )
    }
    
    /**
     * Get price history for a token (for RSI calculations)
     */
    fun getPriceHistory(mint: String): List<Double>? {
        return priceHistoryCache[mint]?.prices
    }
    
    /**
     * Load price history for a token using the enhanced PriceHistoryLoader
     */
    private suspend fun loadPriceHistoryForToken(mint: String) {
        if (priceHistoryLoader == null) {
            log.debug("No price history loader configured")
            return
        }
        
        try {
            // Load comprehensive price history from multiple sources
            val prices = priceHistoryLoader.loadPriceHistory(mint)
            if (prices != null && prices.isNotEmpty()) {
                priceHistoryCache[mint] = PriceHistory(
                    mint = mint,
                    prices = prices,
                    lastUpdated = System.currentTimeMillis()
                )
                log.info("📊 Loaded ${prices.size} price points for $mint for RSI calculation")
                
                // Also load OHLCV data if available for more advanced indicators
                val ohlcv = priceHistoryLoader.loadOHLCVHistory(mint)
                if (ohlcv != null && ohlcv.isNotEmpty()) {
                    log.info("📈 Loaded ${ohlcv.size} OHLCV candles for $mint")
                }
            } else {
                log.warn("No price history available for $mint")
            }
        } catch (e: Exception) {
            log.error("Failed to load price history for $mint: ${e.message}")
        }
    }
    
    private suspend fun runBuyWorker(workerId: Int) {
        log.info("Buy worker #$workerId started")
        
        try {
            for (order in buyChannel) {
                processOrder(order, workerId, OrderType.BUY)
                delay(config.buySpacingMs)
            }
        } catch (e: CancellationException) {
            log.debug("Buy worker #$workerId cancelled")
        } catch (e: Exception) {
            log.error("Buy worker #$workerId error: ${e.message}", e)
        } finally {
            log.info("Buy worker #$workerId stopped")
        }
    }
    
    private suspend fun runSellWorker(workerId: Int) {
        log.info("Sell worker #$workerId started")
        
        try {
            for (order in sellChannel) {
                processOrder(order, workerId, OrderType.SELL)
                delay(config.sellSpacingMs)
            }
        } catch (e: CancellationException) {
            log.debug("Sell worker #$workerId cancelled")
        } catch (e: Exception) {
            log.error("Sell worker #$workerId error: ${e.message}", e)
        } finally {
            log.info("Sell worker #$workerId stopped")
        }
    }
    
    private suspend fun processOrder(order: UnifiedOrder, workerId: Int, type: OrderType) {
        log.info("🔄 Worker #$workerId processing ${type} order: ${order.mint} (id=${order.id})")
        
        // Update processing state
        val state = if (type == OrderType.BUY) buyProcessingState else sellProcessingState
        state.startProcessing(order.id)
        
        var success = false
        var lastError: String? = null
        var result: OrderResult? = null
        
        // Retry logic
        repeat(config.maxRetries) { attempt ->
            try {
                result = when (type) {
                    OrderType.BUY -> buyExecutor(order)
                    OrderType.SELL -> sellExecutor(order)
                }
                
                when (result) {
                    is OrderResult.Success -> {
                        success = true
                        log.info("✅ ${type} order completed: ${order.mint} (id=${order.id})")
                        metrics.recordSuccess(order)
                        return@repeat
                    }
                    is OrderResult.Failure -> {
                        lastError = (result as OrderResult.Failure).error
                        if (!(result as OrderResult.Failure).isRetryable) {
                            log.error("❌ ${type} order failed (non-retryable): ${order.mint} - $lastError")
                            return@repeat
                        }
                        log.warn("⚠️ ${type} order failed (attempt ${attempt + 1}/${config.maxRetries}): ${order.mint} - $lastError")
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                log.error("💥 ${type} order exception (attempt ${attempt + 1}/${config.maxRetries}): ${order.mint}", e)
            }
            
            if (attempt < config.maxRetries - 1) {
                delay(config.retryDelayMs * (attempt + 1)) // Exponential backoff
            }
        }
        
        // Record final result
        if (!success) {
            result = OrderResult.Failure(order.id, lastError ?: "Max retries exceeded", false)
            metrics.recordFailure(order)
            log.error("❌ ${type} order permanently failed: ${order.mint} (id=${order.id}) - $lastError")
        }
        
        // Store result and cleanup
        result?.let { orderHistory[order.id] = it }
        activeOrders.remove(order.id)
        
        // Update processing state
        state.completeProcessing(order.id, success)
    }
    
    /**
     * Price history data
     */
    private data class PriceHistory(
        val mint: String,
        val prices: List<Double>,
        val lastUpdated: Long
    )
    
    /**
     * Order processing state for tracking buy/sell operations
     */
    private class OrderProcessingState(private val type: String) {
        private val processing = ConcurrentHashMap<String, Long>()
        private val completed = AtomicLong(0)
        private val failed = AtomicLong(0)
        
        fun startProcessing(orderId: String) {
            processing[orderId] = System.currentTimeMillis()
        }
        
        fun completeProcessing(orderId: String, success: Boolean) {
            processing.remove(orderId)
            if (success) completed.incrementAndGet() else failed.incrementAndGet()
        }
        
        fun getStats() = mapOf(
            "type" to type,
            "processing" to processing.size,
            "completed" to completed.get(),
            "failed" to failed.get()
        )
    }
    
    /**
     * Internal metrics tracking
     */
    private class OrderQueueMetrics {
        val totalEnqueued = AtomicLong(0)
        val totalBuys = AtomicLong(0)
        val totalSells = AtomicLong(0)
        val successfulBuys = AtomicLong(0)
        val successfulSells = AtomicLong(0)
        val failedBuys = AtomicLong(0)
        val failedSells = AtomicLong(0)
        
        fun recordEnqueue(order: UnifiedOrder) {
            totalEnqueued.incrementAndGet()
            when (order.type) {
                OrderType.BUY -> totalBuys.incrementAndGet()
                OrderType.SELL -> totalSells.incrementAndGet()
            }
        }
        
        fun recordSuccess(order: UnifiedOrder) {
            when (order.type) {
                OrderType.BUY -> successfulBuys.incrementAndGet()
                OrderType.SELL -> successfulSells.incrementAndGet()
            }
        }
        
        fun recordFailure(order: UnifiedOrder) {
            when (order.type) {
                OrderType.BUY -> failedBuys.incrementAndGet()
                OrderType.SELL -> failedSells.incrementAndGet()
            }
        }
    }
}

/**
 * Queue statistics
 */
data class OrderQueueStats(
    val buyQueueSize: Int,
    val sellQueueSize: Int,
    val activeOrders: Int,
    val totalEnqueued: Long,
    val totalBuys: Long,
    val totalSells: Long,
    val successfulBuys: Long,
    val successfulSells: Long,
    val failedBuys: Long,
    val failedSells: Long
)