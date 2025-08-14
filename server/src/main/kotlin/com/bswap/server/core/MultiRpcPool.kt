package com.bswap.server.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

/**
 * Multi-RPC pool with circuit breaker pattern for automatic failover
 * Tracks health metrics and routes requests to healthy endpoints
 */
class MultiRpcPool(
    private val endpoints: List<RpcEndpoint>,
    private val circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private val log = LoggerFactory.getLogger("MultiRpcPool")
    private val endpointHealth = ConcurrentHashMap<String, EndpointHealth>()
    private val roundRobinIndex = AtomicInteger(0)
    
    init {
        endpoints.forEach { endpoint ->
            endpointHealth[endpoint.url] = EndpointHealth(endpoint.url)
        }
        log.info("Initialized MultiRpcPool with ${endpoints.size} endpoints: ${endpoints.map { it.url }}")
    }
    
    /**
     * Get the best healthy endpoint for making requests
     * @return RpcEndpoint or null if all endpoints are unhealthy
     */
    suspend fun getHealthyEndpoint(): RpcEndpoint? {
        val healthyEndpoints = getHealthyEndpoints()
        
        if (healthyEndpoints.isEmpty()) {
            log.warn("No healthy RPC endpoints available, attempting half-open probes")
            return attemptHalfOpenProbe()
        }
        
        // Use weighted round-robin based on success rate
        return selectByWeight(healthyEndpoints)
    }
    
    /**
     * Record successful request for endpoint health tracking
     */
    suspend fun recordSuccess(endpointUrl: String, latencyMs: Long) {
        endpointHealth[endpointUrl]?.let { health ->
            health.recordSuccess(latencyMs)
            
            // If endpoint was in half-open state, consider closing circuit
            if (health.getState() == CircuitState.HALF_OPEN) {
                health.closeCircuit()
                log.info("Circuit closed for endpoint: $endpointUrl")
            }
        }
    }
    
    /**
     * Record failed request for endpoint health tracking
     */
    suspend fun recordFailure(endpointUrl: String, error: Throwable) {
        endpointHealth[endpointUrl]?.let { health ->
            health.recordFailure(error)
            
            val newState = health.getState()
            if (newState == CircuitState.OPEN) {
                log.warn("Circuit opened for endpoint: $endpointUrl due to: ${error.message}")
            }
        }
    }
    
    /**
     * Get current health status for all endpoints
     */
    suspend fun getHealthStatus(): Map<String, EndpointHealthStatus> {
        return endpointHealth.mapValues { (_, health) -> health.getHealthStatus() }
    }
    
    /**
     * Force reset circuit breaker for an endpoint (admin operation)
     */
    suspend fun resetCircuitBreaker(endpointUrl: String) {
        endpointHealth[endpointUrl]?.let { health ->
            health.reset()
            log.info("Circuit breaker reset for endpoint: $endpointUrl")
        }
    }
    
    /**
     * Get list of configured endpoints
     */
    fun getEndpoints(): List<RpcEndpoint> = endpoints
    
    private suspend fun getHealthyEndpoints(): List<RpcEndpoint> {
        return endpoints.filter { endpoint ->
            val health = endpointHealth[endpoint.url]
            health?.getState() == CircuitState.CLOSED
        }
    }
    
    private suspend fun attemptHalfOpenProbe(): RpcEndpoint? {
        val probeableEndpoints = endpoints.filter { endpoint ->
            val health = endpointHealth[endpoint.url]
            health?.canAttemptHalfOpen() == true
        }
        
        return if (probeableEndpoints.isNotEmpty()) {
            val endpoint = probeableEndpoints.random()
            endpointHealth[endpoint.url]?.transitionToHalfOpen()
            log.info("Attempting half-open probe for endpoint: ${endpoint.url}")
            endpoint
        } else {
            // As last resort, return any endpoint for emergency requests
            log.error("All endpoints unhealthy, returning random endpoint as emergency fallback")
            endpoints.randomOrNull()
        }
    }
    
    private fun selectByWeight(endpoints: List<RpcEndpoint>): RpcEndpoint {
        if (endpoints.size == 1) return endpoints[0]

        // Prefer lower priority values (smaller number = higher priority)
        val grouped = endpoints.groupBy { it.priority }
        val bestPriority = grouped.keys.minOrNull() ?: grouped.keys.first()
        val candidates = grouped[bestPriority] ?: endpoints

        // Round-robin among candidates of the same priority
        val index = kotlin.math.abs(roundRobinIndex.getAndIncrement()) % candidates.size
        return candidates[index]
    }
}

data class RpcEndpoint(
    val url: String,
    val priority: Int = 1,
    val maxConnections: Int = 10
)

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,           // Number of failures to open circuit
    val successThreshold: Int = 3,           // Number of successes to close circuit
    val timeoutMs: Long = 30_000,           // Circuit open timeout
    val halfOpenMaxRequests: Int = 3,        // Max requests in half-open state
    val latencyThresholdMs: Long = 5000,     // P95 latency threshold
    val healthCheckWindowMs: Long = 60_000   // Health check window
)

enum class CircuitState {
    CLOSED,     // Normal operation
    OPEN,       // Circuit breaker tripped, requests fail fast
    HALF_OPEN   // Testing if endpoint has recovered
}

data class EndpointHealthStatus(
    val url: String,
    val state: CircuitState,
    val successRate: Double,
    val p95LatencyMs: Long,
    val totalRequests: Long,
    val failureCount: Long,
    val lastFailureTime: Instant?,
    val lastSuccessTime: Instant?
)

/**
 * Tracks health metrics for individual RPC endpoint
 */
private class EndpointHealth(
    private val url: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private val mutex = Mutex()
    private var state = CircuitState.CLOSED
    private var failureCount = 0
    private var successCount = 0
    private var totalRequests = AtomicLong(0)
    private var lastFailureTime: Instant? = null
    private var lastSuccessTime: Instant? = null
    private var circuitOpenTime: Instant? = null
    private var halfOpenRequestCount = 0
    
    private val latencyWindow = mutableListOf<Long>()
    private val maxLatencyWindowSize = 100
    
    suspend fun recordSuccess(latencyMs: Long) {
        mutex.withLock {
            totalRequests.incrementAndGet()
            successCount++
            lastSuccessTime = Instant.now()
            
            // Update latency window
            latencyWindow.add(latencyMs)
            if (latencyWindow.size > maxLatencyWindowSize) {
                latencyWindow.removeAt(0)
            }
            
            // Reset failure count on success
            if (state == CircuitState.CLOSED) {
                failureCount = 0
            }
        }
    }
    
    suspend fun recordFailure(error: Throwable) {
        mutex.withLock {
            totalRequests.incrementAndGet()
            failureCount++
            lastFailureTime = Instant.now()
            
            // Check if we should open the circuit
            if (state == CircuitState.CLOSED && failureCount >= config.failureThreshold) {
                state = CircuitState.OPEN
                circuitOpenTime = Instant.now()
            } else if (state == CircuitState.HALF_OPEN) {
                // Half-open probe failed, go back to open
                state = CircuitState.OPEN
                circuitOpenTime = Instant.now()
                halfOpenRequestCount = 0
            }
        }
    }
    
    suspend fun getState(): CircuitState {
        return mutex.withLock {
            when (state) {
                CircuitState.OPEN -> {
                    // Check if timeout has elapsed to attempt half-open
                    val openTime = circuitOpenTime
                    if (openTime != null && 
                        Instant.now().toEpochMilli() - openTime.toEpochMilli() > config.timeoutMs) {
                        state = CircuitState.HALF_OPEN
                        halfOpenRequestCount = 0
                    }
                    state
                }
                else -> state
            }
        }
    }
    
    suspend fun canAttemptHalfOpen(): Boolean {
        return mutex.withLock {
            state == CircuitState.OPEN && circuitOpenTime != null &&
                    Instant.now().toEpochMilli() - circuitOpenTime!!.toEpochMilli() > config.timeoutMs
        }
    }
    
    suspend fun transitionToHalfOpen() {
        mutex.withLock {
            if (state == CircuitState.OPEN) {
                state = CircuitState.HALF_OPEN
                halfOpenRequestCount = 0
            }
        }
    }
    
    suspend fun closeCircuit() {
        mutex.withLock {
            if (state == CircuitState.HALF_OPEN && successCount >= config.successThreshold) {
                state = CircuitState.CLOSED
                failureCount = 0
                successCount = 0
                circuitOpenTime = null
            }
        }
    }
    
    suspend fun getHealthStatus(): EndpointHealthStatus {
        return mutex.withLock {
            val total = totalRequests.get()
            val successRate = if (total > 0) {
                (total - failureCount).toDouble() / total
            } else 0.0
            
            val p95Latency = if (latencyWindow.isNotEmpty()) {
                val sorted = latencyWindow.sorted()
                val index = min(sorted.size - 1, (sorted.size * 0.95).toInt())
                sorted[index]
            } else 0L
            
            EndpointHealthStatus(
                url = url,
                state = state,
                successRate = successRate,
                p95LatencyMs = p95Latency,
                totalRequests = total,
                failureCount = failureCount.toLong(),
                lastFailureTime = lastFailureTime,
                lastSuccessTime = lastSuccessTime
            )
        }
    }
    
    suspend fun reset() {
        mutex.withLock {
            state = CircuitState.CLOSED
            failureCount = 0
            successCount = 0
            circuitOpenTime = null
            halfOpenRequestCount = 0
            latencyWindow.clear()
        }
    }
}