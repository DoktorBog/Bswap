package com.bswap.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.*

class WebScrapingPriceClient(
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger("WebScrapingPriceClient")
    
    suspend fun getTokenPriceFromWebSources(mint: String): Double? = coroutineScope {
        // Launch all web scraping attempts concurrently
        val dexscreenerDeferred = async { scrapeDexScreener(mint) }
        val dextoolsDeferred = async { scrapeDexTools(mint) }
        val solscanDeferred = async { scrapeSolscan(mint) }
        val coinmarketcapDeferred = async { scrapeCoinMarketCap(mint) }
        
        // Collect all results
        val prices = listOfNotNull(
            runCatching { dexscreenerDeferred.await() }.getOrNull(),
            runCatching { dextoolsDeferred.await() }.getOrNull(),
            runCatching { solscanDeferred.await() }.getOrNull(),
            runCatching { coinmarketcapDeferred.await() }.getOrNull()
        ).filter { it > 0 }

        when {
            prices.isEmpty() -> {
                logger.debug("No prices found for $mint from web scraping")
                null
            }
            prices.size == 1 -> {
                logger.debug("Single web price found for $mint: ${prices.first()}")
                prices.first()
            }
            else -> {
                // Use median to filter outliers
                val sorted = prices.sorted()
                val median = if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                } else {
                    sorted[sorted.size / 2]
                }
                logger.debug("Multiple web prices for $mint: $prices, using median: $median")
                median
            }
        }
    }

    private suspend fun scrapeDexScreener(mint: String): Double? {
        return try {
            val response = httpClient.get("https://api.dexscreener.com/latest/dex/tokens/$mint")
            val jsonStr = response.body<String>()
            val jsonElement = Json.parseToJsonElement(jsonStr)
            
            val pairs = jsonElement.jsonObject["pairs"]?.jsonArray
            pairs?.firstOrNull()?.jsonObject?.get("priceUsd")?.jsonPrimitive?.doubleOrNull?.also {
                logger.debug("DexScreener scraped price for $mint: $it")
            }
        } catch (e: Exception) {
            logger.debug("DexScreener scraping failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun scrapeDexTools(mint: String): Double? {
        return try {
            // DexTools requires different approach - try their API endpoint
            val response = httpClient.get("https://www.dextools.io/shared/data/pair") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                header("Referer", "https://www.dextools.io/")
                url.parameters.append("chain", "solana")
                url.parameters.append("address", mint)
            }
            
            val jsonStr = response.body<String>()
            val jsonElement = Json.parseToJsonElement(jsonStr)
            
            // Try to extract price from DexTools response structure
            val price = jsonElement.jsonObject["data"]?.jsonObject?.get("price")?.jsonPrimitive?.doubleOrNull
            price?.also {
                logger.debug("DexTools scraped price for $mint: $it")
            }
        } catch (e: Exception) {
            logger.debug("DexTools scraping failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun scrapeSolscan(mint: String): Double? {
        return try {
            val response = httpClient.get("https://public-api.solscan.io/token/meta") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                url.parameters.append("tokenAddress", mint)
            }
            
            val jsonStr = response.body<String>()
            val jsonElement = Json.parseToJsonElement(jsonStr)
            
            // Extract price from Solscan response
            val price = jsonElement.jsonObject["priceUsdt"]?.jsonPrimitive?.doubleOrNull
            price?.also {
                logger.debug("Solscan scraped price for $mint: $it")
            }
        } catch (e: Exception) {
            logger.debug("Solscan scraping failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun scrapeCoinMarketCap(mint: String): Double? {
        return try {
            // CoinMarketCap search for the token
            val searchResponse = httpClient.get("https://api.coinmarketcap.com/data-api/v3/cryptocurrency/search") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                url.parameters.append("keyword", mint)
                url.parameters.append("pageSize", "1")
            }
            
            val searchJsonStr = searchResponse.body<String>()
            val searchJsonElement = Json.parseToJsonElement(searchJsonStr)
            
            val coins = searchJsonElement.jsonObject["data"]?.jsonObject?.get("coins")?.jsonArray
            val firstCoin = coins?.firstOrNull()?.jsonObject
            val cmcId = firstCoin?.get("id")?.jsonPrimitive?.content
            
            if (cmcId != null) {
                // Get detailed price info
                val priceResponse = httpClient.get("https://api.coinmarketcap.com/data-api/v3/cryptocurrency/detail") {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    url.parameters.append("id", cmcId)
                }
                
                val priceJsonStr = priceResponse.body<String>()
                val priceJsonElement = Json.parseToJsonElement(priceJsonStr)
                
                val quote = priceJsonElement.jsonObject["data"]?.jsonObject?.get("statistics")?.jsonObject
                    ?.get("price")?.jsonPrimitive?.doubleOrNull
                
                quote?.also {
                    logger.debug("CoinMarketCap scraped price for $mint: $it")
                }
            } else null
        } catch (e: Exception) {
            logger.debug("CoinMarketCap scraping failed for $mint: ${e.message}")
            null
        }
    }

    suspend fun getTokenInfoFromWeb(mint: String): Map<String, String?> = coroutineScope {
        return@coroutineScope try {
            val infoMap = mutableMapOf<String, String?>()
            
            // Try to get basic token info from multiple sources
            val dexScreenerInfo = async { getDexScreenerTokenInfo(mint) }
            val solscanInfo = async { getSolscanTokenInfo(mint) }
            
            val dexInfo = runCatching { dexScreenerInfo.await() }.getOrNull()
            val solInfo = runCatching { solscanInfo.await() }.getOrNull()
            
            infoMap["name"] = dexInfo?.get("name") ?: solInfo?.get("name")
            infoMap["symbol"] = dexInfo?.get("symbol") ?: solInfo?.get("symbol")
            infoMap["decimals"] = dexInfo?.get("decimals") ?: solInfo?.get("decimals")
            infoMap["supply"] = dexInfo?.get("supply") ?: solInfo?.get("supply")
            
            infoMap
        } catch (e: Exception) {
            logger.debug("Token info web scraping failed for $mint: ${e.message}")
            emptyMap()
        }
    }

    private suspend fun getDexScreenerTokenInfo(mint: String): Map<String, String?>? {
        return try {
            val response = httpClient.get("https://api.dexscreener.com/latest/dex/tokens/$mint")
            val jsonStr = response.body<String>()
            val jsonElement = Json.parseToJsonElement(jsonStr)
            
            val pair = jsonElement.jsonObject["pairs"]?.jsonArray?.firstOrNull()?.jsonObject
            val baseToken = pair?.get("baseToken")?.jsonObject
            
            if (baseToken != null) {
                mapOf(
                    "name" to baseToken["name"]?.jsonPrimitive?.content,
                    "symbol" to baseToken["symbol"]?.jsonPrimitive?.content,
                    "address" to baseToken["address"]?.jsonPrimitive?.content
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getSolscanTokenInfo(mint: String): Map<String, String?>? {
        return try {
            val response = httpClient.get("https://public-api.solscan.io/token/meta") {
                url.parameters.append("tokenAddress", mint)
            }
            
            val jsonStr = response.body<String>()
            val jsonElement = Json.parseToJsonElement(jsonStr)
            val tokenObj = jsonElement.jsonObject
            
            mapOf(
                "name" to tokenObj["name"]?.jsonPrimitive?.content,
                "symbol" to tokenObj["symbol"]?.jsonPrimitive?.content,
                "decimals" to tokenObj["decimals"]?.jsonPrimitive?.content,
                "supply" to tokenObj["supply"]?.jsonPrimitive?.content
            )
        } catch (e: Exception) {
            null
        }
    }
}