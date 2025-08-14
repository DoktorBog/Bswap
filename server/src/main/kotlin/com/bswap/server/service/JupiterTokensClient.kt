package com.bswap.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

@Serializable
data class JupToken(
    val address: String,
    val symbol: String,
    val name: String? = null,
    val decimals: Int = 0,
    val tags: List<String> = emptyList()
)

class JupiterTokensClient(
    private val http: HttpClient,
    private val base: String = "https://tokens.jup.ag"
) {
    /**
     * Get only verified tokens from Jupiter Token List
     */
    suspend fun getVerifiedTokens(): List<JupToken> =
        http.get("$base/tokens?tags=verified").body()

    /**
     * Get all tokens from Jupiter Token List
     */
    suspend fun getAllTokens(): List<JupToken> =
        http.get("$base/tokens").body()
}