package com.bswap.server.service

import com.bswap.server.data.dexscreener.DexScreenerClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@Serializable
data class CoinGeckoPriceResponse(
    val solana: CoinGeckoPriceDetails? = null
)

@Serializable
data class CoinGeckoPriceDetails(
    val usd: Double? = null,
    val usdt: Double? = null
)

@Serializable
data class JupiterPriceResponse(
    val data: Map<String, JupiterTokenPrice>? = null
)

@Serializable
data class JupiterTokenPrice(
    val id: String,
    val mintSymbol: String,
    val vsToken: String,
    val vsTokenSymbol: String,
    val price: Double
)

data class TokenPrice(
    val mint: String,
    val symbol: String,
    val priceUsd: Double,
    val priceUsdt: Double = priceUsd, // Default USDT to USD if not available
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PriceService(
    private val httpClient: HttpClient,
    private val dexScreenerClient: DexScreenerClient
) {
    private val logger = LoggerFactory.getLogger(PriceService::class.java)
    private val priceCache = ConcurrentHashMap<String, TokenPrice>()
    private val cacheTtlMs = 60_000L // 1 minute cache
    
    companion object {
        const val SOL_MINT = "So11111111111111111111111111111111111111112"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
        
        // Well-known token mints for better symbol mapping
        private val WELL_KNOWN_TOKENS = mapOf(
            SOL_MINT to "SOL",
            USDC_MINT to "USDC", 
            USDT_MINT to "USDT",
            "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263" to "BONK",
            "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R" to "RAY",
            "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE" to "ORCA"
        )
    }
    
    /**
     * Get price for a single token mint address
     */
    suspend fun getTokenPrice(mint: String): TokenPrice? = coroutineScope {
        val cachedPrice = getCachedPrice(mint)
        if (cachedPrice != null) {
            return@coroutineScope cachedPrice
        }
        
        logger.info("💰 Fetching price for token: $mint")
        
        // Special handling for SOL
        if (mint == SOL_MINT) {
            val solPrice = getSolPrice()
            if (solPrice != null) {
                priceCache[mint] = solPrice
                return@coroutineScope solPrice
            }
        }
        
        // Try multiple price sources concurrently
        val dexScreenerDeferred = async { getPriceFromDexScreener(mint) }
        val jupiterDeferred = async { getPriceFromJupiter(mint) }
        
        // Get results and return the first successful one
        val dexPrice = try { dexScreenerDeferred.await() } catch (e: Exception) { 
            logger.warn("DexScreener price fetch failed for $mint: ${e.message}")
            null 
        }
        val jupiterPrice = try { jupiterDeferred.await() } catch (e: Exception) { 
            logger.warn("Jupiter price fetch failed for $mint: ${e.message}")
            null 
        }
        
        val finalPrice = dexPrice ?: jupiterPrice
        finalPrice?.let { priceCache[mint] = it }
        
        if (finalPrice == null) {
            logger.warn("Could not fetch price for token $mint from any source")
        }
        
        return@coroutineScope finalPrice
    }
    
    /**
     * Get prices for multiple token mints efficiently
     */
    suspend fun getTokenPrices(mints: List<String>): Map<String, TokenPrice> = coroutineScope {
        val results = mutableMapOf<String, TokenPrice>()
        
        // First, get cached prices
        val uncachedMints = mints.filter { mint ->
            val cached = getCachedPrice(mint)
            if (cached != null) {
                results[mint] = cached
                false
            } else {
                true
            }
        }
        
        if (uncachedMints.isEmpty()) {
            return@coroutineScope results
        }
        
        logger.info("💰 Fetching prices for ${uncachedMints.size} tokens")
        
        // Handle SOL separately
        val solMints = uncachedMints.filter { it == SOL_MINT }
        val otherMints = uncachedMints.filter { it != SOL_MINT }
        
        // Fetch SOL price if needed
        if (solMints.isNotEmpty()) {
            val solPrice = getSolPrice()
            if (solPrice != null) {
                solMints.forEach { mint ->
                    results[mint] = solPrice
                    priceCache[mint] = solPrice
                }
            }
        }
        
        // Fetch other token prices concurrently
        if (otherMints.isNotEmpty()) {
            // Try batch fetching from Jupiter first
            try {
                val jupiterPrices = getBatchPricesFromJupiter(otherMints)
                jupiterPrices.forEach { (mint, price) ->
                    results[mint] = price
                    priceCache[mint] = price
                }
            } catch (e: Exception) {
                logger.warn("Batch Jupiter price fetch failed: ${e.message}")
            }
            
            // For tokens not found in Jupiter, try DexScreener individually
            val remainingMints = otherMints.filter { !results.containsKey(it) }
            val dexScreenerJobs = remainingMints.map { mint ->
                async {
                    try {
                        val price = getPriceFromDexScreener(mint)
                        if (price != null) {
                            results[mint] = price
                            priceCache[mint] = price
                        }
                    } catch (e: Exception) {
                        logger.warn("DexScreener individual price fetch failed for $mint: ${e.message}")
                    }
                }
            }
            dexScreenerJobs.forEach { it.await() }
        }
        
        return@coroutineScope results
    }
    
    /**
     * Get SOL price from CoinGecko
     */
    private suspend fun getSolPrice(): TokenPrice? {
        return try {
            val response: CoinGeckoPriceResponse = httpClient.get(
                "https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd,usdt"
            ).body()
            
            val solData = response.solana
            if (solData?.usd != null) {
                TokenPrice(
                    mint = SOL_MINT,
                    symbol = "SOL",
                    priceUsd = solData.usd,
                    priceUsdt = solData.usdt ?: solData.usd,
                    source = "CoinGecko"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch SOL price from CoinGecko: ${e.message}")
            null
        }
    }
    
    /**
     * Get token price from DexScreener
     */
    private suspend fun getPriceFromDexScreener(mint: String): TokenPrice? {
        return try {
            val response = dexScreenerClient.getPairsByToken(mint)
            val pair = response.pairs?.firstOrNull { it.baseToken.address == mint }
            
            if (pair != null && pair.priceUsd.isNotEmpty()) {
                val priceUsd = pair.priceUsd.toDoubleOrNull()
                if (priceUsd != null && priceUsd > 0) {
                    TokenPrice(
                        mint = mint,
                        symbol = pair.baseToken.symbol.takeIf { it.isNotEmpty() } 
                            ?: WELL_KNOWN_TOKENS[mint] 
                            ?: mint.take(8),
                        priceUsd = priceUsd,
                        source = "DexScreener"
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug("DexScreener price fetch failed for $mint: ${e.message}")
            null
        }
    }
    
    /**
     * Get token price from Jupiter Price API  
     */
    private suspend fun getPriceFromJupiter(mint: String): TokenPrice? {
        return try {
            val response: JupiterPriceResponse = httpClient.get(
                "https://price.jup.ag/v4/price?ids=$mint"
            ).body()
            
            val tokenData = response.data?.get(mint)
            if (tokenData != null && tokenData.price > 0) {
                TokenPrice(
                    mint = mint,
                    symbol = tokenData.mintSymbol.takeIf { it.isNotEmpty() } 
                        ?: WELL_KNOWN_TOKENS[mint] 
                        ?: mint.take(8),
                    priceUsd = tokenData.price,
                    source = "Jupiter"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug("Jupiter price fetch failed for $mint: ${e.message}")
            null
        }
    }
    
    /**
     * Get batch prices from Jupiter API
     */
    private suspend fun getBatchPricesFromJupiter(mints: List<String>): Map<String, TokenPrice> {
        return try {
            val mintsParam = mints.joinToString(",")
            val response: JupiterPriceResponse = httpClient.get(
                "https://price.jup.ag/v4/price?ids=$mintsParam"
            ).body()
            
            response.data?.mapNotNull { (mint, tokenData) ->
                if (tokenData.price > 0) {
                    mint to TokenPrice(
                        mint = mint,
                        symbol = tokenData.mintSymbol.takeIf { it.isNotEmpty() } 
                            ?: WELL_KNOWN_TOKENS[mint] 
                            ?: mint.take(8),
                        priceUsd = tokenData.price,
                        source = "Jupiter"
                    )
                } else {
                    null
                }
            }?.toMap() ?: emptyMap()
        } catch (e: Exception) {
            logger.debug("Jupiter batch price fetch failed: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Calculate USD value for a token amount
     */
    suspend fun calculateUsdValue(mint: String, amount: String?, decimals: Int?): Double? {
        if (amount == null || decimals == null) return null
        
        val price = getTokenPrice(mint) ?: return null
        val actualAmount = try {
            amount.toDouble() / Math.pow(10.0, decimals.toDouble())
        } catch (e: Exception) {
            logger.warn("Failed to parse token amount: $amount, decimals: $decimals")
            return null
        }
        
        return actualAmount * price.priceUsd
    }
    
    /**
     * Get cached price if still valid
     */
    private fun getCachedPrice(mint: String): TokenPrice? {
        val cached = priceCache[mint]
        return if (cached != null && (System.currentTimeMillis() - cached.timestamp) < cacheTtlMs) {
            cached
        } else {
            null
        }
    }
    
    /**
     * Clear expired cache entries
     */
    fun cleanupCache() {
        val now = System.currentTimeMillis()
        val expiredKeys = priceCache.entries.filter { (_, price) ->
            (now - price.timestamp) > cacheTtlMs
        }.map { it.key }
        
        expiredKeys.forEach { priceCache.remove(it) }
        
        if (expiredKeys.isNotEmpty()) {
            logger.info("🧹 Cleaned up ${expiredKeys.size} expired price cache entries")
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "cacheSize" to priceCache.size,
            "cachedTokens" to priceCache.keys.toList(),
            "cacheAgeSeconds" to priceCache.values.map { 
                (System.currentTimeMillis() - it.timestamp) / 1000 
            }
        )
    }
}