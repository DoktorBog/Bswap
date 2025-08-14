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
import java.util.LinkedList

class JitoBundlerService(
    private val client: HttpClient,
    private val jitoFeeLamports: Long,
    private val tipAccounts: List<String>,
) {
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
            txQueue.add(tx.encodeToBase58String())
            // Always flush immediately to ensure transactions are sent
            // This is especially important for sell-all strategies with few tokens
            true // Always flush after enqueue
        }
        
        // Flush outside the mutex to prevent deadlock
        if (shouldFlush) {
            flush()
        }
    }

    suspend fun stop() {
        flush()
    }

    private suspend fun flush() {
        
        val bundleData = mutex.withLock {
            
            if (txQueue.isEmpty()) {
                return@withLock null
            }
            
            val list = mutableListOf<String>()
            while (txQueue.isNotEmpty() && list.size < 4) {
                list.add(txQueue.removeFirst())
            }

            try {
                val tipTx = JitoFeeTxBuilder.buildJitoFeeTx(jitoFeeLamports, tipAccounts.random())
                list.add(0, tipTx)

                val req = JitoBundleRequest("2.0", 1, "sendBundle", listOf(list))
                
                // Return data to send outside the lock
                Pair(list, req)
            } catch (e: Exception) {
                null
            }
        }
        
        
        // Send HTTP requests outside the mutex lock
        bundleData?.let { (list, req) ->
            
            endpoints.forEach { url ->
                try {
                    
                    val response = client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(req)
                    }
                    
                    
                    response.bodyAsText()
                    
                } catch (e: Throwable) {
                }
            }
            
        }
    }
}

object JitoFeeTxBuilder {
    suspend fun buildJitoFeeTx(lamports: Long, toAccount: String): String {
        return try {
            val tipTx = JitoTxCreator.createTipTx(lamports, toAccount)
            tipTx
        } catch (e: Exception) {
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
