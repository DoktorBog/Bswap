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
        mutex.withLock {
            logger.info("Enqueuing transaction. Current queue size=${txQueue.size}")
            txQueue.add(tx.encodeToBase58String())
            if (txQueue.size >= 4) {
                logger.info("Queue size >= 4, calling flush() immediately.")
                flush()
            }
        }
    }

    suspend fun stop() {
        logger.info("Stopping JitoBundlerService...")
        flush()
    }

    private suspend fun flush() {
        mutex.withLock {
            if (txQueue.isEmpty()) {
                logger.info("flush() called but queue is empty, doing nothing.")
                return
            }
            val list = mutableListOf<String>()
            while (txQueue.isNotEmpty() && list.size < 4) {
                list.add(txQueue.removeFirst())
            }
            logger.info("Flushing ${list.size} transaction(s). Building tip transaction...")

            val tipTx = JitoFeeTxBuilder.buildJitoFeeTx(jitoFeeLamports, tipAccounts.random())
            list.add(0, tipTx)
            logger.info("Final bundle size with tip: ${list.size} transaction(s).")

            val req = JitoBundleRequest("2.0", 1, "sendBundle", listOf(list))

            endpoints.forEach { url ->
                try {
                    logger.info("Sending bundle to $url with ${list.size} tx(s).")
                    val response = client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(req)
                    }
                    val responseText = response.bodyAsText()
                    logger.info("Response from $url => $responseText")
                } catch (e: Throwable) {
                    logger.error("Error sending to $url: ${e.message}")
                }
            }
        }
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