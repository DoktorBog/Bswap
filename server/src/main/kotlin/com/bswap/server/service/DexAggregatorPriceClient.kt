package com.bswap.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Serializable
data class BirdeyePriceResponse(
    val data: BirdeyePriceData? = null,
    val success: Boolean = false
)

@Serializable 
data class BirdeyePriceData(
    val value: Double? = null,
    val updateUnixTime: Long? = null,
    val updateHumanTime: String? = null,
    val priceChange24h: Double? = null
)

@Serializable
data class GeckoterminalTokenData(
    val data: GeckoterminalAttributes? = null
)

@Serializable
data class GeckoterminalAttributes(
    val attributes: GeckoterminalPriceInfo? = null
)

@Serializable
data class GeckoterminalPriceInfo(
    val base_token_price_usd: String? = null,
    val quote_token_price_usd: String? = null,
    val base_token_price_native_currency: String? = null,
    val quote_token_price_native_currency: String? = null,
    val address: String? = null
)

class DexAggregatorPriceClient(
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger("DexAggregatorPriceClient")

    suspend fun getTokenPriceFromMultipleSources(mint: String): Double? = coroutineScope {
        // Launch all price fetches concurrently
        val birdeyeDeferred = async { getBirdeyePrice(mint) }
        val geckoterminalDeferred = async { getGeckoterminalPrice(mint) }
        val jup1inchDeferred = async { get1inchPrice(mint) }
        val raydiumDeferred = async { getRaydiumPrice(mint) }

        // Collect all results
        val prices = listOfNotNull(
            runCatching { birdeyeDeferred.await() }.getOrNull(),
            runCatching { geckoterminalDeferred.await() }.getOrNull(),
            runCatching { jup1inchDeferred.await() }.getOrNull(),
            runCatching { raydiumDeferred.await() }.getOrNull()
        ).filter { it > 0 }

        when {
            prices.isEmpty() -> {
                logger.debug("No prices found for $mint from any DEX aggregator")
                null
            }
            prices.size == 1 -> {
                logger.debug("Single price found for $mint: ${prices.first()}")
                prices.first()
            }
            else -> {
                // Use median price to filter out outliers
                val sortedPrices = prices.sorted()
                val median = if (sortedPrices.size % 2 == 0) {
                    (sortedPrices[sortedPrices.size / 2 - 1] + sortedPrices[sortedPrices.size / 2]) / 2.0
                } else {
                    sortedPrices[sortedPrices.size / 2]
                }
                logger.debug("Multiple prices for $mint: $prices, using median: $median")
                median
            }
        }
    }

    private suspend fun getBirdeyePrice(mint: String): Double? {
        return try {
            val response: BirdeyePriceResponse = httpClient.get("https://public-api.birdeye.so/defi/price") {
                parameter("address", mint)
                parameter("check_liquidity", "true")
            }.body()
            
            val price = response.data?.value
            if (price != null && price > 0) {
                logger.debug("Birdeye price for $mint: $price")
                price
            } else null
        } catch (e: Exception) {
            logger.debug("Birdeye price fetch failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun getGeckoterminalPrice(mint: String): Double? {
        return try {
            val response: GeckoterminalTokenData = httpClient.get("https://api.geckoterminal.com/api/v2/networks/solana/tokens/$mint").body()
            
            val priceStr = response.data?.attributes?.base_token_price_usd
            val price = priceStr?.toDoubleOrNull()
            
            if (price != null && price > 0) {
                logger.debug("GeckoTerminal price for $mint: $price")
                price
            } else null
        } catch (e: Exception) {
            logger.debug("GeckoTerminal price fetch failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun get1inchPrice(mint: String): Double? {
        return try {
            // 1inch doesn't have direct price API, but we can get quote
            val solAmount = 1000000L // 0.001 SOL in lamports
            val response = httpClient.get("https://api.1inch.dev/swap/v6.0/1/quote") {
                parameter("src", "So11111111111111111111111111111111111111112") // SOL
                parameter("dst", mint)
                parameter("amount", solAmount.toString())
            }
            
            val jsonStr = response.body<String>()
            val jsonElement = Json.parseToJsonElement(jsonStr)
            
            val dstAmount = jsonElement.jsonObject["dstAmount"]?.jsonPrimitive?.content?.toLongOrNull()
            if (dstAmount != null && dstAmount > 0) {
                // Get SOL price to calculate token price
                val solPrice = getSolPriceQuick() ?: 150.0
                val tokenPrice = (solAmount.toDouble() * solPrice) / dstAmount.toDouble()
                
                if (tokenPrice > 0) {
                    logger.debug("1inch derived price for $mint: $tokenPrice")
                    tokenPrice
                } else null
            } else null
        } catch (e: Exception) {
            logger.debug("1inch price derivation failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun getRaydiumPrice(mint: String): Double? {
        return try {
            val response = httpClient.get("https://api.raydium.io/v2/sdk/liquidity/mainnet.json")
            val jsonStr = response.body<String>()
            val jsonElement = Json.parseToJsonElement(jsonStr)
            
            // Search for pools containing our mint
            val official = jsonElement.jsonObject["official"]?.jsonArray ?: JsonArray(emptyList())
            val unofficial = jsonElement.jsonObject["unOfficial"]?.jsonArray ?: JsonArray(emptyList())
            
            for (poolArray in listOf(official, unofficial)) {
                for (poolElement in poolArray) {
                    val pool = poolElement.jsonObject
                    val baseMint = pool["baseMint"]?.jsonPrimitive?.content
                    val quoteMint = pool["quoteMint"]?.jsonPrimitive?.content
                    
                    if (baseMint == mint || quoteMint == mint) {
                        val baseReserve = pool["baseReserve"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        val quoteReserve = pool["quoteReserve"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        
                        if (baseReserve != null && quoteReserve != null && baseReserve > 0 && quoteReserve > 0) {
                            val price = if (baseMint == mint) {
                                // Token is base, calculate price in quote terms
                                quoteReserve / baseReserve
                            } else {
                                // Token is quote, calculate price in base terms  
                                baseReserve / quoteReserve
                            }
                            
                            // If paired with SOL, convert to USD
                            val solMint = "So11111111111111111111111111111111111111112"
                            val finalPrice = if (quoteMint == solMint || baseMint == solMint) {
                                val solPrice = getSolPriceQuick() ?: 150.0
                                price * solPrice
                            } else {
                                price
                            }
                            
                            if (finalPrice > 0) {
                                logger.debug("Raydium price for $mint: $finalPrice")
                                return finalPrice
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            logger.debug("Raydium price fetch failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun getSolPriceQuick(): Double? {
        return try {
            val response = httpClient.get("https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd")
            val jsonStr = response.body<String>()
            val jsonElement = Json.parseToJsonElement(jsonStr)
            
            jsonElement.jsonObject["solana"]?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull
        } catch (e: Exception) {
            logger.debug("Quick SOL price fetch failed: ${e.message}")
            null
        }
    }
}