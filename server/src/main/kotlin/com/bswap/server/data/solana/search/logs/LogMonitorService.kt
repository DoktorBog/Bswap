package com.bswap.server.data.solana.search.logs

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import com.bswap.server.config.ServerConfig
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

// Data classes to model parsed logs
data class Certificate(val id: String, val owner: String)
data class LogPool(val id: String, val tokenA: String, val tokenB: String)

// Log monitoring service
class LogMonitorService(private val client: HttpClient) {
    private val logger = LoggerFactory.getLogger(LogMonitorService::class.java)
    private val newCertificates = mutableSetOf<Certificate>()
    private val newPools = mutableSetOf<LogPool>()

    suspend fun fetchLogs(): List<String> {
        // Replace this URL with the appropriate RPC or log-fetching API
        val url = ServerConfig.logMonitorUrl
        val response: HttpResponse = client.get(url) {
            // Additional request parameters or authentication if needed
        }
        // Parse the response body (mock implementation for logs)
        return response.body<List<String>>()
    }

    suspend fun monitorLogs() {
        while (true) {
            try {
                val logs = fetchLogs()

                logs.forEach { log ->
                    parseLog(log)?.let { event ->
                        when (event) {
                            is Certificate -> {
                               synchronized(newCertificates) {
                                   newCertificates.add(event)
                                   // Limit memory usage by clearing old certificates
                                   if (newCertificates.size > 1000) {
                                       val toRemove = newCertificates.take(500)
                                       newCertificates.removeAll(toRemove.toSet())
                                   }
                               }
                                logger.info("New certificate detected: ID=${event.id}, Owner=${event.owner}")
                            }
                            is LogPool -> {
                                synchronized(newPools) {
                                    newPools.add(event)
                                    // Limit memory usage by clearing old pools
                                    if (newPools.size > 1000) {
                                        val toRemove = newPools.take(500)
                                        newPools.removeAll(toRemove.toSet())
                                    }
                                }
                                logger.info("New pool detected: ID=${event.id}, TokenA=${event.tokenA}, TokenB=${event.tokenB}")
                            }
                        }
                    }
                }

                delay(30_000) // Monitor every 30 seconds
            } catch (e: Exception) {
                logger.error("Error during log monitoring: ${e.message}", e)
                delay(5_000) // Wait before retry on error
            }
        }
    }

    private fun parseLog(log: String): Any? {
        return when {
            log.contains("CertificateIssued") -> {
                val parts = log.split(",") // Example log parsing
                Certificate(id = parts[1], owner = parts[2])
            }
            log.contains("NewPoolCreated") -> {
                val parts = log.split(",")
                LogPool(id = parts[1], tokenA = parts[2], tokenB = parts[3])
            }
            else -> null
        }
    }

    fun getNewCertificates(): List<Certificate> = synchronized(newCertificates) { newCertificates.toList() }
    fun getNewPools(): List<LogPool> = synchronized(newPools) { newPools.toList() }
}