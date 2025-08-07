package com.bswap.server.data.solana.jito

import com.bswap.server.config.ServerConfig
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
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
    private val flushIntervalMs: Long = 10_000, // how often the flush loop runs
    private val batchSize: Int = 5 // how many transactions per bundle (excluding tip)
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    // Queue to hold base58-encoded transactions until we bundle them
    private val txQueue = LinkedList<String>()

    // Jito endpoints can be customised via the JITO_BUNDLER_ENDPOINTS env variable
    private val endpoints = ServerConfig.jitoBundlerEndpoints

    init {
        // 1) Background loop flushes the queue periodically
        scope.launch {
            while (true) {
                delay(flushIntervalMs)
                flushOnce()
            }
        }
    }

    /**
     * Public API to enqueue a single transaction (raw bytes).
     * We encode to base58, then store in our queue.
     */
    suspend fun enqueue(tx: ByteArray) {
        // Acquire the lock briefly to push into queue
        mutex.withLock {
            val encoded = tx.encodeToBase58String()
            txQueue.add(encoded)
            logger.info("Enqueued tx. Queue size=${txQueue.size}")

            // Optional: If queue hits a certain threshold, flush immediately
            // If you prefer only time-based flushes, remove this.
            if (txQueue.size >= batchSize) {
                logger.info("Queue >= $batchSize, flushing immediately.")
                // We call flushOnce() outside the lock (or after lock).
            }
        }
        // Trigger flush after releasing the lock
        flushOnce()
    }

    /**
     * Graceful stop by flushing all remaining transactions.
     */
    suspend fun stop() {
        logger.info("Stopping JitoBundlerService... Flushing remaining items.")
        flushOnce() // flush what's in the queue
    }

    /**
     * Flush ALL transactions from the queue in batches of [batchSize].
     * Each batch:
     * - Build tip transaction
     * - Send in parallel to all endpoints
     */
    private suspend fun flushOnce() {
        try {
            while (true) {
                // 1) Pull out one batch from the queue
                val chunk = mutableListOf<String>()
                mutex.withLock {
                    if (txQueue.isEmpty()) {
                        return
                    }
                    repeat(batchSize) {
                        if (txQueue.isEmpty()) return@repeat
                        chunk.add(txQueue.removeFirst())
                    }
                }

                // 2) If the chunk is empty, we're done
                if (chunk.isEmpty()) {
                    logger.info("Chunk empty, done flushing.")
                    return
                }

                // 3) Build tip transaction, put at front
                val tipTx = JitoFeeTxBuilder.buildJitoFeeTx(jitoFeeLamports, tipAccounts.random())
                val finalTxs = listOf(tipTx) + chunk

                logger.info("Flushing batch of ${chunk.size} tx(s) + tip => total=${finalTxs.size}")

                // 4) Send this batch to all endpoints in parallel
                sendToAllEndpoints(finalTxs)
            }
        } catch (e: Throwable) {
            logger.error("Error in flushOnce(): ${e.message}", e)
        }
    }

    /**
     * Sends a list of transactions to all Jito endpoints in parallel coroutines.
     */
    private suspend fun sendToAllEndpoints(txs: List<String>) {
        // Build Jito bundle request
        val req = JitoBundleRequest(
            jsonrpc = "2.0",
            id = 1,
            method = "sendBundle",
            params = listOf(txs)
        )

        // Launch an async job for each endpoint
        val jobs = endpoints.map { url ->
            scope.async {
                try {
                    val response = client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(req)
                    }
                    val responseText = response.bodyAsText()
                    logger.info("Posted ${txs.size} tx(s) to $url. Response: $responseText")
                } catch (ex: Throwable) {
                    logger.error("Error sending to $url: ${ex.message}", ex)
                }
            }
        }

        // Wait for all to complete
        jobs.joinAll()
    }
}

object JitoFeeTxBuilder {
    suspend fun buildJitoFeeTx(lamports: Long, toAccount: String): String {
        return JitoTxCreator.createTipTx(lamports, toAccount).also {
            LoggerFactory.getLogger(javaClass)
                .info("Created tip tx to=$toAccount lamports=$lamports (base64 length=${it.length})")
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
