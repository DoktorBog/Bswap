package com.bswap.server.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Global rate limiter supporting multiple buckets (RPC, HTTP, etc.)
 * with Prometheus metrics integration and configurable rate limits
 */
class GlobalRateLimiter(
    private val buckets: Map<String, BucketConfig> = mapOf(
        "rpc" to BucketConfig(maxRps = 14, burstCapacity = 28),
        "dex" to BucketConfig(maxRps = 10, burstCapacity = 20),
        "jupiter" to BucketConfig(maxRps = 10, burstCapacity = 20)
    )
) {
    private val log = LoggerFactory.getLogger("GlobalRateLimiter")
    private val tokenBuckets = ConcurrentHashMap<String, TokenBucket>()
    private val metrics = RateLimiterMetrics()
    
    init {
        // Initialize all configured buckets
        buckets.forEach { (bucket, config) ->
            tokenBuckets[bucket] = TokenBucket(bucket, config)
        }
        log.info("Initialized GlobalRateLimiter with buckets: ${buckets.keys}")
    }
    
    /**
     * Acquire permit from specified bucket with blocking
     * @param bucket The rate limit bucket (e.g., "rpc", "dex")
     * @param operation Optional operation name for metrics
     */
    suspend fun acquirePermit(bucket: String, operation: String = "unknown"): Boolean {
        val tokenBucket = tokenBuckets[bucket] ?: run {
            log.warn("Unknown rate limit bucket: $bucket, allowing request")
            return true
        }
        
        val startTime = System.nanoTime()
        val acquired = tokenBucket.acquire()
        val waitTimeNs = System.nanoTime() - startTime
        
        metrics.recordRequest(bucket, operation, acquired, waitTimeNs)
        
        if (!acquired) {
            log.debug("Rate limit exceeded for bucket: $bucket, operation: $operation")
        }
        
        return acquired
    }
    
    /**
     * Try to acquire permit without blocking
     */
    suspend fun tryAcquirePermit(bucket: String, operation: String = "unknown"): Boolean {
        val tokenBucket = tokenBuckets[bucket] ?: return true
        
        val acquired = tokenBucket.tryAcquire()
        metrics.recordRequest(bucket, operation, acquired, 0L)
        
        return acquired
    }
    
    /**
     * Get statistics for all buckets
     */
    suspend fun getStats(): Map<String, BucketStats> {
        return tokenBuckets.mapValues { (_, bucket) -> bucket.getStats() }
    }
    
    /**
     * Get Prometheus metrics
     */
    fun getMetrics(): RateLimiterMetrics = metrics
    
    /**
     * Reset all statistics
     */
    suspend fun resetStats() {
        tokenBuckets.values.forEach { it.resetStats() }
        metrics.reset()
    }
    
    /**
     * Update bucket configuration at runtime
     */
    suspend fun updateBucketConfig(bucket: String, config: BucketConfig) {
        tokenBuckets[bucket]?.let { tokenBucket ->
            tokenBucket.updateConfig(config)
            log.info("Updated rate limit for bucket $bucket: maxRps=${config.maxRps}, burst=${config.burstCapacity}")
        }
    }
}

data class BucketConfig(
    val maxRps: Int,
    val burstCapacity: Int = maxRps * 2,
    val maxWaitMs: Long = 50L // Max time to wait for permit
)

data class BucketStats(
    val bucket: String,
    val maxRps: Int,
    val currentTokens: Double,
    val totalRequests: Long,
    val deniedRequests: Long,
    val totalWaitTimeMs: Long,
    val maxWaitTimeMs: Long,
    val averageWaitTimeMs: Double
)

/**
 * Individual token bucket implementation with circuit-breaking behavior
 */
private class TokenBucket(
    private val name: String,
    private var config: BucketConfig
) {
    private val mutex = Mutex()
    private var tokens = config.burstCapacity.toDouble()
    private var lastRefillTime = System.currentTimeMillis()
    
    // Statistics
    private var totalRequests = 0L
    private var deniedRequests = 0L
    private var totalWaitTimeMs = 0L
    private var maxWaitTimeMs = 0L
    
    suspend fun acquire(): Boolean {
        val startTime = System.currentTimeMillis()
        
        mutex.withLock {
            refillTokens()
            totalRequests++
            
            if (tokens >= 1.0) {
                tokens -= 1.0
                return true
            }
            
            // Calculate wait time
            val tokensNeeded = 1.0 - tokens
            val waitTimeMs = (tokensNeeded * 1000.0 / config.maxRps).toLong()
            
            if (waitTimeMs > config.maxWaitMs) {
                deniedRequests++
                return false
            }
            
            totalWaitTimeMs += waitTimeMs
            maxWaitTimeMs = max(maxWaitTimeMs, waitTimeMs)
        }
        
        // Wait outside the mutex with jitter
        val endTime = System.currentTimeMillis()
        val actualWaitTime = endTime - startTime
        if (actualWaitTime > 0) {
            val jitter = Random.nextLong(0, min(actualWaitTime / 4, 10))
            delay(actualWaitTime + jitter)
        }
        
        // Try to acquire after wait
        return mutex.withLock {
            refillTokens()
            if (tokens >= 1.0) {
                tokens -= 1.0
                true
            } else {
                deniedRequests++
                false
            }
        }
    }
    
    suspend fun tryAcquire(): Boolean {
        return mutex.withLock {
            refillTokens()
            totalRequests++
            
            if (tokens >= 1.0) {
                tokens -= 1.0
                true
            } else {
                deniedRequests++
                false
            }
        }
    }
    
    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val timePassed = now - lastRefillTime
        
        if (timePassed > 0) {
            val tokensToAdd = (timePassed * config.maxRps) / 1000.0
            tokens = min(config.burstCapacity.toDouble(), tokens + tokensToAdd)
            lastRefillTime = now
        }
    }
    
    suspend fun getStats(): BucketStats {
        return mutex.withLock {
            refillTokens()
            BucketStats(
                bucket = name,
                maxRps = config.maxRps,
                currentTokens = tokens,
                totalRequests = totalRequests,
                deniedRequests = deniedRequests,
                totalWaitTimeMs = totalWaitTimeMs,
                maxWaitTimeMs = maxWaitTimeMs,
                averageWaitTimeMs = if (totalRequests > 0) totalWaitTimeMs.toDouble() / totalRequests else 0.0
            )
        }
    }
    
    suspend fun resetStats() {
        mutex.withLock {
            totalRequests = 0
            deniedRequests = 0
            totalWaitTimeMs = 0
            maxWaitTimeMs = 0
        }
    }
    
    suspend fun updateConfig(newConfig: BucketConfig) {
        mutex.withLock {
            config = newConfig
            // Adjust current tokens if capacity changed
            tokens = min(tokens, newConfig.burstCapacity.toDouble())
        }
    }
}

/**
 * Metrics collection for Prometheus integration
 */
class RateLimiterMetrics {
    private val requestsTotal = ConcurrentHashMap<String, AtomicLong>()
    private val requestsDenied = ConcurrentHashMap<String, AtomicLong>()
    private val waitTimeHistogram = ConcurrentHashMap<String, MutableList<Long>>()
    
    fun recordRequest(bucket: String, operation: String, acquired: Boolean, waitTimeNs: Long) {
        val key = "$bucket:$operation"
        
        requestsTotal.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        
        if (!acquired) {
            requestsDenied.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        }
        
        if (waitTimeNs > 0) {
            waitTimeHistogram.computeIfAbsent(key) { mutableListOf() }
                .add(waitTimeNs / 1_000_000) // Convert to milliseconds
        }
    }
    
    fun getPrometheusMetrics(): String {
        val sb = StringBuilder()
        
        // Total requests counter
        sb.appendLine("# HELP rate_limiter_requests_total Total number of rate limit requests")
        sb.appendLine("# TYPE rate_limiter_requests_total counter")
        requestsTotal.forEach { (key, count) ->
            val (bucket, operation) = key.split(":", limit = 2)
            sb.appendLine("rate_limiter_requests_total{bucket=\"$bucket\",operation=\"$operation\"} ${count.get()}")
        }
        
        // Denied requests counter
        sb.appendLine("# HELP rate_limiter_requests_denied_total Total number of denied rate limit requests")
        sb.appendLine("# TYPE rate_limiter_requests_denied_total counter")
        requestsDenied.forEach { (key, count) ->
            val (bucket, operation) = key.split(":", limit = 2)
            sb.appendLine("rate_limiter_requests_denied_total{bucket=\"$bucket\",operation=\"$operation\"} ${count.get()}")
        }
        
        // Wait time histogram
        sb.appendLine("# HELP rate_limiter_wait_time_ms Rate limiter wait time in milliseconds")
        sb.appendLine("# TYPE rate_limiter_wait_time_ms histogram")
        waitTimeHistogram.forEach { (key, times) ->
            val (bucket, operation) = key.split(":", limit = 2)
            if (times.isNotEmpty()) {
                val sorted = times.sorted()
                val count = times.size
                val sum = times.sum()
                
                sb.appendLine("rate_limiter_wait_time_ms_bucket{bucket=\"$bucket\",operation=\"$operation\",le=\"1\"} ${sorted.count { it <= 1 }}")
                sb.appendLine("rate_limiter_wait_time_ms_bucket{bucket=\"$bucket\",operation=\"$operation\",le=\"5\"} ${sorted.count { it <= 5 }}")
                sb.appendLine("rate_limiter_wait_time_ms_bucket{bucket=\"$bucket\",operation=\"$operation\",le=\"10\"} ${sorted.count { it <= 10 }}")
                sb.appendLine("rate_limiter_wait_time_ms_bucket{bucket=\"$bucket\",operation=\"$operation\",le=\"25\"} ${sorted.count { it <= 25 }}")
                sb.appendLine("rate_limiter_wait_time_ms_bucket{bucket=\"$bucket\",operation=\"$operation\",le=\"50\"} ${sorted.count { it <= 50 }}")
                sb.appendLine("rate_limiter_wait_time_ms_bucket{bucket=\"$bucket\",operation=\"$operation\",le=\"+Inf\"} $count")
                sb.appendLine("rate_limiter_wait_time_ms_sum{bucket=\"$bucket\",operation=\"$operation\"} $sum")
                sb.appendLine("rate_limiter_wait_time_ms_count{bucket=\"$bucket\",operation=\"$operation\"} $count")
            }
        }
        
        return sb.toString()
    }
    
    fun reset() {
        requestsTotal.clear()
        requestsDenied.clear()
        waitTimeHistogram.clear()
    }
}