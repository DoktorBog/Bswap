package com.bswap.server.data.solana.search.pool

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
        val url =
            "https://api-v3.raydium.io/pools/info/list?poolType=all&poolSortField=default&sortType=desc&pageSize=10&page=1"
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
                }

                detectedPools.forEach { pool ->
                    knownPools.add(pool.id)
                    logger.info("New pool detected: ID=${pool.id}, Name=${pool.name}, Price=${pool.price}, TVL=${pool.tvl}, TokenA=${pool.mintA.symbol}, TokenB=${pool.mintB.symbol}")
                }

                delay(30_000) // Monitor every 60 seconds
            } catch (e: Exception) {
                logger.error("Error during pool monitoring: ${e.message}", e)
            }
        }
    }

    fun getNewPools(): List<Pool> = synchronized(newPools) { newPools.toList() }
}
