package com.bswap.server.core

import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.networking.NetworkDriver
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Simple RPC wrapper that uses MultiRpcPool for automatic failover
 */
class PooledRpcWrapper(
    private val rpcPool: MultiRpcPool
) {
    private val logger = LoggerFactory.getLogger("PooledRpcWrapper")
    
    /**
     * Create a traditional RPC instance for compatibility with existing code
     * Uses the best available endpoint from the pool
     */
    suspend fun createCompatibleRpc(): RPC {
        val endpoint = rpcPool.getHealthyEndpoint()
        val endpointUrl = endpoint?.url ?: run {
            // Fallback to first endpoint if no healthy ones
            logger.warn("No healthy endpoints, using fallback")
            rpcPool.getEndpoints().firstOrNull()?.url
        } ?: throw Exception("No RPC endpoints configured")
        
        logger.info("Creating RPC instance for endpoint: $endpointUrl")
        return RPC(endpointUrl, NetworkDriver(getGlobalClient()))
    }
    
    /**
     * Get health status of all endpoints
     */
    suspend fun getHealthStatus(): Map<String, EndpointHealthStatus> {
        return rpcPool.getHealthStatus()
    }
    
    /**
     * Record success for health tracking
     */
    suspend fun recordSuccess(endpointUrl: String, latencyMs: Long) {
        rpcPool.recordSuccess(endpointUrl, latencyMs)
    }
    
    /**
     * Record failure for health tracking
     */
    suspend fun recordFailure(endpointUrl: String, error: Throwable) {
        rpcPool.recordFailure(endpointUrl, error)
    }
}

// Get the global client from Application.kt
private fun getGlobalClient(): HttpClient {
    return io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}