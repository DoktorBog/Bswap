package com.bswap.server.data.solana.jito

import foundation.metaplex.base58.encodeToBase58String
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.LinkedList

class JitoBundlerService(
    private val client: HttpClient,
    private val jitoFeeLamports: Long,
    private val tipAccounts: List<String>,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val txQueue = LinkedList<String>()
    private val endpoints = listOf(
        "https://mainnet.block-engine.jito.wtf/api/v1/bundles",
        "https://amsterdam.mainnet.block-engine.jito.wtf/api/v1/bundles",
        "https://frankfurt.mainnet.block-engine.jito.wtf/api/v1/bundles",
        "https://ny.mainnet.block-engine.jito.wtf/api/v1/bundles",
        "https://tokyo.mainnet.block-engine.jito.wtf/api/v1/bundles",
        "https://slc.mainnet.block-engine.jito.wtf/api/v1/bundles"
    )

    init {
        scope.launch {
            while (isActive) {
                delay(5000)
                flush()
            }
        }
    }

    suspend fun enqueue(tx: ByteArray) {
        val shouldFlush = mutex.withLock {
            logger.info("Enqueuing transaction. Current queue size=${txQueue.size}")
            txQueue.add(tx.encodeToBase58String())
            // Always flush immediately to ensure transactions are sent
            // This is especially important for sell-all strategies with few tokens
            logger.info("Flushing immediately after enqueue")
            true // Always flush after enqueue
        }
        
        // Flush outside the mutex to prevent deadlock
        if (shouldFlush) {
            flush()
        }
    }

    suspend fun stop() {
        logger.info("Stopping JitoBundlerService...")
        flush()
    }

    private suspend fun flush() {
        logger.info("🔄 flush() method called - attempting to acquire mutex lock")
        
        val bundleData = mutex.withLock {
            logger.info("🔒 Mutex acquired in flush() method")
            
            if (txQueue.isEmpty()) {
                logger.info("flush() called but queue is empty, doing nothing.")
                return@withLock null
            }
            
            val list = mutableListOf<String>()
            while (txQueue.isNotEmpty() && list.size < 4) {
                list.add(txQueue.removeFirst())
            }
            logger.info("Flushing ${list.size} transaction(s). Building tip transaction...")

            try {
                val tipTx = JitoFeeTxBuilder.buildJitoFeeTx(jitoFeeLamports, tipAccounts.random())
                logger.info("Tip transaction created successfully: ${tipTx.take(20)}...")
                list.add(0, tipTx)
                logger.info("Final bundle size with tip: ${list.size} transaction(s).")

                val req = JitoBundleRequest("2.0", 1, "sendBundle", listOf(list))
                logger.info("Created JitoBundleRequest with ${req.params.first().size} transactions")
                
                // Return data to send outside the lock
                Pair(list, req)
            } catch (e: Exception) {
                logger.error("❌ ERROR creating tip transaction: ${e.javaClass.simpleName} - ${e.message}")
                logger.error("Full tip transaction error:", e)
                null
            }
        }
        
        logger.info("🔓 Mutex released in flush() method")
        
        // Send HTTP requests outside the mutex lock
        bundleData?.let { (list, req) ->
            logger.info("📡 Starting HTTP requests to Jito endpoints (outside mutex)")
            
            endpoints.forEach { url ->
                try {
                    logger.info("=== ATTEMPTING HTTP POST to $url ===")
                    logger.info("Bundle contains ${list.size} transactions")
                    logger.info("HTTP Client instance: ${client::class.simpleName}")
                    
                    val response = client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(req)
                    }
                    
                    logger.info("=== HTTP RESPONSE RECEIVED from $url ===")
                    logger.info("Status: ${response.status}")
                    logger.info("Headers: ${response.headers}")
                    
                    val responseText = response.bodyAsText()
                    logger.info("Response body: $responseText")
                    
                    if (response.status.value in 200..299) {
                        logger.info("✅ Successfully sent bundle to $url")
                    } else {
                        logger.warn("⚠️ Non-success status ${response.status} from $url")
                    }
                } catch (e: Throwable) {
                    logger.error("❌ ERROR sending to $url: ${e.javaClass.simpleName} - ${e.message}")
                    logger.error("Full exception details:", e)
                }
            }
            
            logger.info("📡 Completed HTTP requests to all Jito endpoints")
        } ?: logger.warn("⚠️ No bundle data to send (tip transaction creation failed or queue was empty)")
    }
}

object JitoFeeTxBuilder {
    suspend fun buildJitoFeeTx(lamports: Long, toAccount: String): String {
        return try {
            val tipTx = JitoTxCreator.createTipTx(lamports, toAccount)
            LoggerFactory.getLogger(javaClass)
                .info("✅ Created tip tx to=$toAccount lamports=$lamports (base58 length=${tipTx.length})")
            tipTx
        } catch (e: Exception) {
            LoggerFactory.getLogger(javaClass)
                .error("❌ FAILED to create tip tx to=$toAccount lamports=$lamports: ${e.javaClass.simpleName} - ${e.message}", e)
            throw e
        }
    }
}

@Serializable
data class JitoBundleRequest(
    val jsonrpc: String,
    val id: Int,
    val method: String,
    val params: List<List<String>>
)
