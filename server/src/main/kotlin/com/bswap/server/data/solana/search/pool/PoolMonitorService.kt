package com.bswap.server.data.solana.search.pool

import com.bswap.server.config.ServerConfig
import com.bswap.server.data.solana.search.pool.model.Pool
import com.bswap.server.data.solana.search.pool.model.PoolResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

// Service class to fetch and monitor pools
class PoolMonitorService(private val client: HttpClient) {
    private val knownPools = ConcurrentHashMap.newKeySet<String>()
    private val newPools = mutableSetOf<Pool>()
    private val logger = LoggerFactory.getLogger(PoolMonitorService::class.java)

    suspend fun fetchPools(): List<Pool> {
        val url = ServerConfig.poolMonitorUrl
        val response: HttpResponse = client.get(url)
        val parsedResponse = response.body<PoolResponse>()
        if (parsedResponse.success) {
            return parsedResponse.data.data
        } else {
            throw Exception("Failed to fetch pools from API")
        }
    }

    suspend fun monitorPools() {
        while (true) {
            try {
                val pools = fetchPools()
                val detectedPools = pools//.filter { it.id !in knownPools }

                synchronized(newPools) {
                    newPools.addAll(detectedPools)
                    // Limit memory usage by clearing old pools
                    if (newPools.size > 1000) {
                        val toRemove = newPools.take(500)
                        newPools.removeAll(toRemove.toSet())
                    }
                }

                detectedPools.forEach { pool ->
                    knownPools.add(pool.id)
                    logger.info("New pool detected: ID=${pool.id}, Name=${pool.name}, Price=${pool.price}, TVL=${pool.tvl}, TokenA=${pool.mintA.symbol}, TokenB=${pool.mintB.symbol}")
                }
                
                // Limit knownPools size to prevent memory leaks
                if (knownPools.size > 5000) {
                    val toRemove = knownPools.take(2500)
                    knownPools.removeAll(toRemove.toSet())
                }

                delay(30_000) // Monitor every 30 seconds
            } catch (e: Exception) {
                logger.error("Error during pool monitoring: ${e.message}", e)
                delay(5_000) // Wait before retry on error
            }
        }
    }

    fun getNewPools(): List<Pool> = synchronized(newPools) { newPools.toList() }
}
