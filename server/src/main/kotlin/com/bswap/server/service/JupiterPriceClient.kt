package com.bswap.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

@Serializable
data class JupPriceResp(val data: Map<String, PriceEntry>) {
    @Serializable
    data class PriceEntry(val price: Double)
}

class JupiterPriceClient(
    private val http: HttpClient,
    private val base: String = "https://price.jup.ag/v6"
) {
    private val logger = org.slf4j.LoggerFactory.getLogger("JupiterPriceClient")
    
    suspend fun getPrice(mint: String): Double? {
        return try {
            val resp: JupPriceResp = http.get("$base/price?ids=$mint").body()
            resp.data[mint]?.price?.takeIf { it > 0 }
        } catch (e: Exception) {
            logger.debug("Jupiter price fetch failed for $mint on $base: ${e.message}")
            // Try an alternate host (quote-api) as a fallback for occasional DNS / host issues
            return try {
                val alt = http.get("https://quote-api.jup.ag/v6/price?ids=$mint").body<JupPriceResp>()
                alt.data[mint]?.price?.takeIf { it > 0 }
            } catch (ex: Exception) {
                logger.debug("Alternate Jupiter price fetch failed for $mint: ${ex.message}")
                null
            }
        }
    }

    suspend fun getPrices(mints: List<String>): Map<String, Double> {
        return try {
            if (mints.isEmpty()) return emptyMap()
            
            val idsParam = mints.joinToString(",")
            val resp: JupPriceResp = http.get("$base/price?ids=$idsParam").body()
            resp.data.mapNotNull { (mint, entry) ->
                if (entry.price > 0) mint to entry.price else null
            }.toMap()
        } catch (e: Exception) {
            logger.debug("Jupiter batch price fetch failed on $base: ${e.message}")
            // Fallback to alternate host
            try {
                val idsParam = mints.joinToString(",")
                val altResp: JupPriceResp = http.get("https://quote-api.jup.ag/v6/price?ids=$idsParam").body()
                altResp.data.mapNotNull { (mint, entry) ->
                    if (entry.price > 0) mint to entry.price else null
                }.toMap()
            } catch (ex: Exception) {
                logger.debug("Alternate Jupiter batch fetch failed: ${ex.message}")
                emptyMap()
            }
        }
    }
    
    /**
     * Check if Jupiter has pricing data for a mint
     */
    suspend fun hasPrice(mint: String): Boolean {
        return getPrice(mint) != null
    }
}