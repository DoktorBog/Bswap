package com.bswap.server.data.dexscreener

import com.bswap.server.data.dexscreener.models.TokenProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class TokenInfoService(private val client: HttpClient) {
    private val logger = LoggerFactory.getLogger(TokenInfoService::class.java)

    private val _tokenProfiles = MutableStateFlow<List<TokenProfile>>(emptyList())
    val tokenProfiles: StateFlow<List<TokenProfile>> = _tokenProfiles.asStateFlow()

    private val apiUrl = "https://api.dexscreener.com/token-profiles/latest/v1"

    private suspend fun fetchTokenProfiles(): List<TokenProfile> {
        try {
            val response: List<TokenProfile> = client.get(apiUrl).body()
            _tokenProfiles.update { response } // Update the StateFlow
            logger.info("Successfully fetched ${response.size} token profiles")
            return response
        } catch (e: Exception) {
            logger.error("Failed to fetch token profiles: ${e.message}")
            throw e
        }
    }

    suspend fun monitorTokenProfiles(interval: Long = 3_000) {
        while (true) {
            try {
                fetchTokenProfiles()
            } catch (e: Exception) {
                logger.error("Error during token profile monitoring: ${e.message}")
            }
            delay(interval)
        }
    }
}