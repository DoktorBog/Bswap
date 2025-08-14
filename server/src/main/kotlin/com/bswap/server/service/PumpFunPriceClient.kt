package com.bswap.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.slf4j.LoggerFactory

@Serializable
data class PumpFunTokenInfo(
    val mint: String? = null,
    val name: String? = null,
    val symbol: String? = null,
    val description: String? = null,
    val image_uri: String? = null,
    val metadata_uri: String? = null,
    val twitter: String? = null,
    val telegram: String? = null,
    val bonding_curve: String? = null,
    val associated_bonding_curve: String? = null,
    val creator: String? = null,
    val created_timestamp: Long? = null,
    val raydium_pool: String? = null,
    val complete: Boolean? = null,
    val virtual_sol_reserves: Long? = null,
    val virtual_token_reserves: Long? = null,
    val hidden: Boolean? = null,
    val total_supply: Long? = null,
    val website: String? = null,
    val show_name: Boolean? = null,
    val last_trade_timestamp: Long? = null,
    val king_of_the_hill_timestamp: Long? = null,
    val market_cap: Double? = null,
    val reply_count: Int? = null,
    val last_reply: Long? = null,
    val nsfw: Boolean? = null,
    val market_id: String? = null,
    val inverted: Boolean? = null,
    val is_currently_live: Boolean? = null,
    val username: String? = null,
    val profile_image: String? = null,
    val usd_market_cap: Double? = null
)

class PumpFunPriceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://frontend-api.pump.fun"
) {
    private val logger = LoggerFactory.getLogger("PumpFunPriceClient")

    suspend fun getTokenInfo(mint: String): PumpFunTokenInfo? {
        return try {
            val response: PumpFunTokenInfo = httpClient.get("$baseUrl/coins/$mint").body()
            response
        } catch (e: Exception) {
            logger.debug("Pump.fun token info fetch failed for $mint: ${e.message}")
            null
        }
    }

    suspend fun getTokenPrice(mint: String): Double? {
        return try {
            val tokenInfo = getTokenInfo(mint)
            
            // Try usd_market_cap first, then market_cap
            val marketCap = tokenInfo?.usd_market_cap ?: tokenInfo?.market_cap
            val totalSupply = tokenInfo?.total_supply
            
            if (marketCap != null && totalSupply != null && totalSupply > 0) {
                // Calculate price per token: market_cap / total_supply
                val price = marketCap / totalSupply
                if (price > 0) {
                    logger.debug("Pump.fun price for $mint: $price USD (mcap: $marketCap, supply: $totalSupply)")
                    return price
                }
            }
            
            // Fallback: try to calculate from virtual reserves if available
            val virtualSolReserves = tokenInfo?.virtual_sol_reserves
            val virtualTokenReserves = tokenInfo?.virtual_token_reserves
            
            if (virtualSolReserves != null && virtualTokenReserves != null && 
                virtualSolReserves > 0 && virtualTokenReserves > 0) {
                
                // Get SOL price (you could cache this)
                val solPrice = getSolPrice() ?: 150.0 // fallback SOL price
                
                // Calculate price: (SOL_reserves * SOL_price) / token_reserves
                val price = (virtualSolReserves.toDouble() * solPrice) / virtualTokenReserves.toDouble()
                logger.debug("Pump.fun calculated price for $mint: $price USD (sol_reserves: $virtualSolReserves, token_reserves: $virtualTokenReserves)")
                return price
            }
            
            null
        } catch (e: Exception) {
            logger.debug("Pump.fun price calculation failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun getSolPrice(): Double? {
        return try {
            // Quick SOL price fetch from a reliable source
            val response = httpClient.get("https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd")
            val jsonStr = response.body<String>()
            
            // Simple JSON parsing for SOL price
            if (jsonStr.contains("\"usd\":")) {
                val start = jsonStr.indexOf("\"usd\":") + 6
                val end = jsonStr.indexOf("}", start)
                val priceStr = jsonStr.substring(start, end).trim()
                priceStr.toDoubleOrNull()
            } else null
        } catch (e: Exception) {
            logger.debug("Failed to get SOL price: ${e.message}")
            null
        }
    }

    suspend fun isTokenActive(mint: String): Boolean {
        return try {
            val tokenInfo = getTokenInfo(mint)
            tokenInfo?.is_currently_live == true && tokenInfo.hidden != true
        } catch (e: Exception) {
            false
        }
    }
}