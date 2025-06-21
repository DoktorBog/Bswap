package com.bswap.server.service

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PriceService(private val client: HttpClient) {
    private val cache = mutableMapOf<String, Pair<Long, Double>>()
    private val idMap = mapOf(
        "SOL" to "solana",
        "USDC" to "usd-coin",
        "USDT" to "tether"
    )

    suspend fun prices(symbols: List<String>): Map<String, Double> {
        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, Double>()
        val fetch = mutableListOf<String>()
        symbols.forEach { symbol ->
            val key = symbol.uppercase()
            val cached = cache[key]
            if (cached != null && now - cached.first < 14_000) {
                result[key] = cached.second
            } else {
                fetch.add(key)
            }
        }
        if (fetch.isNotEmpty()) {
            val ids = fetch.mapNotNull { idMap[it] }.joinToString(",")
            if (ids.isNotBlank()) {
                val url = "https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=usd"
                val text = client.get(url).bodyAsText()
                val json = Json.parseToJsonElement(text).jsonObject
                fetch.forEach { sym ->
                    val id = idMap[sym] ?: return@forEach
                    val price = json[id]?.jsonObject
                        ?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    cache[sym] = now to price
                    result[sym] = price
                }
            }
        }
        return result
    }
}
