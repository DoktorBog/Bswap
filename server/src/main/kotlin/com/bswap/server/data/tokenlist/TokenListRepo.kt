package com.bswap.server.data.tokenlist

import com.bswap.shared.model.TokenInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

class TokenListRepo(
    private val client: HttpClient,
    private val url: String = "https://token.jup.ag/all",
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mutex = Mutex()
    @Volatile
    private var cache: List<TokenInfo>? = null
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAll(): List<TokenInfo> {
        cache?.let { return it }
        return refresh()
    }

    suspend fun search(query: String): List<TokenInfo> {
        val tokens = getAll()
        return tokens.filter {
            it.symbol?.contains(query, ignoreCase = true) == true ||
                it.name?.contains(query, ignoreCase = true) == true
        }
    }

    private suspend fun refresh(): List<TokenInfo> = mutex.withLock {
        cache?.let { return it }
        var data: List<TokenInfo> = emptyList()
        val time = measureTimeMillis {
            data = client.get(url).body()
        }
        logger.debug("token list refresh latency=${time}ms")
        cache = data
        return data
    }
}
