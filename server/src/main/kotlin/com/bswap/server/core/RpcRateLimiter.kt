package com.bswap.server.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import kotlin.math.max

/**
 * Token bucket rate limiter for RPC requests
 * Ensures no more than maxRps requests per second are allowed
 */
class RpcRateLimiter(
    private val maxRps: Int = 14,
    private val bucketSize: Int = maxRps * 2 // Allow some burst capacity
) {
    private val log = LoggerFactory.getLogger("RpcRateLimiter")
    private val mutex = Mutex()
    
    private var tokens = bucketSize.toDouble()
    private var lastRefillTime = System.currentTimeMillis()
    
    // Statistics
    private var totalRequests = 0L
    private var totalWaitTimeMs = 0L
    private var maxWaitTimeMs = 0L
    
    /**
     * Acquire permission for one RPC request
     * This method will block until a token is available
     */
    suspend fun acquirePermit() {
        val startTime = System.currentTimeMillis()
        
        mutex.withLock {
            refillTokens()
            
            totalRequests++
            
            if (tokens >= 1.0) {
                tokens -= 1.0
                return
            }
            
            // Calculate how long to wait for next token
            val tokensNeeded = 1.0 - tokens
            val waitTimeMs = (tokensNeeded * 1000.0 / maxRps).toLong()
            
            if (waitTimeMs > 0) {
                val actualWaitTime = max(waitTimeMs, 10L) // Minimum 10ms wait
                totalWaitTimeMs += actualWaitTime
                maxWaitTimeMs = max(maxWaitTimeMs, actualWaitTime)
                
                if (actualWaitTime > 100) {
                    log.debug("RPC_WAIT: waiting ${actualWaitTime}ms for next request")
                }
            }
        }
        
        // Wait outside the mutex to avoid blocking other threads
        val endTime = System.currentTimeMillis()
        val waitTime = endTime - startTime
        
        if (waitTime > 0) {
            delay(waitTime)
        }
        
        // Acquire token after waiting
        mutex.withLock {
            refillTokens()
            tokens = max(0.0, tokens - 1.0)
        }
    }
    
    /**
     * Try to acquire permit without blocking
     * Returns true if permit was acquired, false otherwise
     */
    suspend fun tryAcquirePermit(): Boolean {
        return mutex.withLock {
            refillTokens()
            
            if (tokens >= 1.0) {
                tokens -= 1.0
                totalRequests++
                true
            } else {
                false
            }
        }
    }
    
    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val timePassed = now - lastRefillTime
        
        if (timePassed > 0) {
            val tokensToAdd = (timePassed * maxRps) / 1000.0
            tokens = minOf(bucketSize.toDouble(), tokens + tokensToAdd)
            lastRefillTime = now
        }
    }
    
    /**
     * Get current rate limiter statistics
     */
    suspend fun getStats(): RateLimiterStats {
        return mutex.withLock {
            refillTokens()
            RateLimiterStats(
                maxRps = maxRps,
                currentTokens = tokens,
                totalRequests = totalRequests,
                totalWaitTimeMs = totalWaitTimeMs,
                maxWaitTimeMs = maxWaitTimeMs,
                averageWaitTimeMs = if (totalRequests > 0) totalWaitTimeMs.toDouble() / totalRequests else 0.0
            )
        }
    }
    
    /**
     * Reset statistics
     */
    suspend fun resetStats() {
        mutex.withLock {
            totalRequests = 0
            totalWaitTimeMs = 0
            maxWaitTimeMs = 0
        }
    }
}

data class RateLimiterStats(
    val maxRps: Int,
    val currentTokens: Double,
    val totalRequests: Long,
    val totalWaitTimeMs: Long,
    val maxWaitTimeMs: Long,
    val averageWaitTimeMs: Double
)