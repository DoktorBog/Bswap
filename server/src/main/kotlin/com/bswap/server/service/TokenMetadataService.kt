package com.bswap.server.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Duration

data class TokenMetadata(
    val name: String,
    val symbol: String,
    val logoUri: String? = null
)

class TokenMetadataService(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }
    
    private val metadataCache: Cache<String, TokenMetadata> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofHours(24))
        .build()
    
    private val knownTokens = mapOf(
        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" to TokenMetadata("USD Coin", "USDC", "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v/logo.png"),
        "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263" to TokenMetadata("Bonk", "BONK", "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263/logo.png"),
        "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R" to TokenMetadata("Raydium", "RAY", "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R/logo.png"),
        "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE" to TokenMetadata("Orca", "ORCA", "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE/logo.png"),
        "So11111111111111111111111111111111111111112" to TokenMetadata("Solana", "SOL", "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/So11111111111111111111111111111111111111112/logo.png"),
        "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB" to TokenMetadata("Tether USD", "USDT", "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB/logo.png"),
        "mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So" to TokenMetadata("Marinade staked SOL", "mSOL", "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So/logo.png")
    )
    
    suspend fun getTokenMetadata(mint: String): TokenMetadata = withContext(Dispatchers.IO) {
        metadataCache.getIfPresent(mint)?.let { return@withContext it }
        
        knownTokens[mint]?.let { metadata ->
            metadataCache.put(mint, metadata)
            return@withContext metadata
        }
        
        val metadata = try {
            fetchTokenMetadataFromRegistry(mint) ?: createFallbackMetadata(mint)
        } catch (e: Exception) {
            logger.warn("Failed to fetch metadata for token $mint: ${e.message}")
            createFallbackMetadata(mint)
        }
        
        metadataCache.put(mint, metadata)
        metadata
    }
    
    private suspend fun fetchTokenMetadataFromRegistry(mint: String): TokenMetadata? {
        return try {
            val response = httpClient.get("https://token-list-api.solana.cloud/v1/map")
            val body = response.bodyAsText()
            val jsonElement = json.parseToJsonElement(body)
            val tokenMap = jsonElement.jsonObject
            
            tokenMap[mint]?.jsonObject?.let { tokenInfo ->
                val name = tokenInfo["name"]?.jsonPrimitive?.content ?: return null
                val symbol = tokenInfo["symbol"]?.jsonPrimitive?.content ?: return null
                val logoUri = tokenInfo["logoURI"]?.jsonPrimitive?.content
                
                TokenMetadata(name, symbol, logoUri)
            }
        } catch (e: Exception) {
            logger.debug("Failed to fetch from token registry: ${e.message}")
            null
        }
    }
    
    private fun createFallbackMetadata(mint: String): TokenMetadata {
        return TokenMetadata(
            name = "Unknown Token",
            symbol = mint.take(8) + "...",
            logoUri = null
        )
    }
    
    suspend fun batchGetTokenMetadata(mints: List<String>): Map<String, TokenMetadata> {
        return mints.associateWith { mint -> getTokenMetadata(mint) }
    }
}