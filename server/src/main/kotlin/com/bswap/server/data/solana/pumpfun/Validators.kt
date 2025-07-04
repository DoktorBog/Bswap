package com.bswap.server.data.solana.pumpfun

import com.bswap.server.client
import com.bswap.server.config.ServerConfig
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class PumpFunResponse(
    val mint: String,
    val created_timestamp: Long,
    val is_currently_live: Boolean,
)

suspend fun isTokenValid(mint: String): Boolean = runCatching {
    delay(10_000)
    val apiUrl = "${ServerConfig.pumpFunBaseUrl}/$mint"
    val response: PumpFunResponse = client.get(apiUrl).body()
    val currentTime = System.currentTimeMillis()
    val ageSeconds = (currentTime - response.created_timestamp) / 1000
    //if (!response.is_currently_live) {
    //    println("Token $mint is not tradable!")
    //    return false
    //}
    if (ageSeconds > 20) {
        println("Token $mint too old - creation time $ageSeconds seconds ago")
        return false
    }
    return true
}.onFailure { e ->
    println("Token is not Valid $mint $e")
    return false
}.getOrElse { false }