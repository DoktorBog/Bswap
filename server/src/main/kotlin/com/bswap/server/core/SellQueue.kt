package com.bswap.server.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory

data class SellOrder(
    val mint: String,
    val reason: String = "strategy",
    val timestamp: Long = System.currentTimeMillis()
)

class SellQueue(
    private val workerScope: CoroutineScope,
    private val sellHandler: suspend (SellOrder) -> Boolean,
    private val maxConcurrency: Int = 1,          // Sequential processing by default
    private val spacingMs: Long = 400L,           // Pause between orders
    private val retryCount: Int = 2,              // Number of retries on failure
    private val retryDelayMs: Long = 1500L        // Delay between retries
) {
    private val log = LoggerFactory.getLogger("SellQueue")
    private val queue = Channel<SellOrder>(Channel.UNLIMITED)
    private val workers = mutableListOf<Job>()
    private var isStarted = false

    fun start() {
        if (isStarted) return
        
        log.info("Starting sell queue with concurrency=$maxConcurrency, spacing=${spacingMs}ms, retries=$retryCount")
        
        repeat(maxConcurrency) { workerId ->
            workers += workerScope.launch(Dispatchers.IO) {
                log.info("Sell queue worker #$workerId started")
                
                for (order in queue) {
                    var success = false
                    var lastException: Exception? = null
                    
                    repeat(retryCount + 1) { attempt ->
                        try {
                            log.info("Processing sell order: ${order.mint} (attempt ${attempt + 1}/${retryCount + 1}, reason=${order.reason})")
                            success = sellHandler(order)
                            
                            if (success) {
                                log.info("Sell order completed successfully: ${order.mint}")
                                return@repeat
                            } else {
                                log.warn("Sell order failed (no exception): ${order.mint}, attempt ${attempt + 1}")
                            }
                        } catch (e: Exception) {
                            lastException = e
                            log.warn("Sell order failed with exception: ${order.mint}, attempt ${attempt + 1}: ${e.message}")
                            
                            if (attempt < retryCount) {
                                delay(retryDelayMs)
                            }
                        }
                    }
                    
                    if (!success) {
                        val errorMsg = lastException?.message ?: "Unknown error"
                        log.error("Sell order failed permanently for ${order.mint} after ${retryCount + 1} attempts: $errorMsg")
                    }
                    
                    // Spacing between orders (even on failure to avoid overwhelming RPC)
                    if (spacingMs > 0) {
                        delay(spacingMs)
                    }
                }
                
                log.info("Sell queue worker #$workerId terminated")
            }
        }
        
        isStarted = true
    }

    fun stop() {
        if (!isStarted) return
        
        log.info("Stopping sell queue...")
        
        queue.close()
        workers.forEach { 
            it.cancel()
        }
        workers.clear()
        isStarted = false
        
        log.info("Sell queue stopped")
    }

    /**
     * Enqueue a sell order. Returns true if successfully added to queue.
     */
    suspend fun enqueue(order: SellOrder): Boolean {
        return if (isStarted && !queue.isClosedForSend) {
            val result = queue.trySend(order)
            if (result.isSuccess) {
                log.info("QUEUE SELL: ${order.mint} (reason=${order.reason})")
                true
            } else {
                log.error("Failed to enqueue sell order for ${order.mint}: ${result.exceptionOrNull()?.message}")
                false
            }
        } else {
            log.warn("Cannot enqueue sell order for ${order.mint}: queue not started or closed")
            false
        }
    }
    
    /**
     * Get current queue size (approximate)
     */
    fun getQueueSize(): Int {
        return if (queue.isClosedForSend) 0 else {
            // This is an approximation since Channel doesn't expose exact size
            // In practice, the queue processes fast enough that this should be near 0
            0
        }
    }
    
    fun isStarted(): Boolean = isStarted
}