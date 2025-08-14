package com.bswap.server.service

import com.bswap.server.data.dexscreener.DexScreenerClient
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.service.limiter.TokenBucketLimiter
import com.bswap.server.service.limiter.retrying
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Serializable
data class CoinGeckoPriceResponse(val solana: CoinGeckoPriceDetails? = null)
@Serializable
data class CoinGeckoPriceDetails(val usd: Double? = null, val usdt: Double? = null)

class SimpleCoinGeckoClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.coingecko.com/api/v3"
) {
    private val logger = LoggerFactory.getLogger("SimpleCoinGeckoClient")
    suspend fun getSolPrice(): Pair<Double, Double>? {
        return try {
            val response: CoinGeckoPriceResponse =
                httpClient.get("$baseUrl/simple/price?ids=solana&vs_currencies=usd,usdt").body()
            val sol = response.solana ?: return null
            val usd = sol.usd ?: return null
            Pair(usd, sol.usdt ?: usd)
        } catch (e: Exception) {
            logger.debug("CoinGecko SOL price fetch failed: ${e.message}")
            null
        }
    }
}

@Serializable
data class JupiterPriceResponse(val data: Map<String, JupiterTokenPrice>? = null)
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
    val priceUsdt: Double = priceUsd,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PriceMissStats(
    var consecutiveMisses: Int = 0,
    var firstMissTime: Long = System.currentTimeMillis(),
    var lastLogTime: Long = 0L,
    var totalMisses: Long = 0L
)

class PriceService(
    private val httpClient: HttpClient,
    private val dexScreenerClient: DexScreenerClient,
    private val jupiterSwapService: JupiterSwapService? = null
) {
    private val logger = LoggerFactory.getLogger(PriceService::class.java)

    // ---- NEW: rate limiters 15 requests/min each (max burst 3) ----
    private val jupLimiter = TokenBucketLimiter(ratePerMinute = 15, maxBurst = 3)
    private val cgLimiter  = TokenBucketLimiter(ratePerMinute = 15, maxBurst = 3)

    private val jupiterPriceClient = JupiterPriceClient(httpClient)
    private val coinGeckoClient = SimpleCoinGeckoClient(httpClient)
    
    // ---- NEW: Enhanced price sources for pump coins ----
    private val pumpFunPriceClient = PumpFunPriceClient(httpClient)
    private val dexAggregatorPriceClient = DexAggregatorPriceClient(httpClient)
    private val webScrapingPriceClient = WebScrapingPriceClient(httpClient)

    // Caches
    private val priceCache = ConcurrentHashMap<String, TokenPrice>()
    private val priceMissCache = ConcurrentHashMap<String, PriceMissStats>()

    // TTLs (ms)
    private val tokenCacheTtlMs = 5_000L          // tokens
    private val solCacheTtlMs   = 30_000L         // SOL can be laxer to protect CG RPM

    // Singleflight
    private val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<TokenPrice?>>()
    private val singleflightMutex = Mutex()

    companion object {
        const val SOL_MINT  = "So11111111111111111111111111111111111111112"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"

        private val WELL_KNOWN_TOKENS = mapOf(
            SOL_MINT to "SOL",
            USDC_MINT to "USDC",
            USDT_MINT to "USDT",
            "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263" to "BONK",
            "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R" to "RAY",
            "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE" to "ORCA"
        )

        private const val JUP_BATCH_SIZE = 100
    }

    // ---------------- public API ----------------

    suspend fun getTokenPrice(mint: String): TokenPrice? = coroutineScope {
        // cache
        getCachedPrice(mint)?.let { return@coroutineScope it }

        // singleflight
        inFlight[mint]?.let {
            return@coroutineScope runCatching { it.await() }.getOrNull()
        }

        val def = async { fetchPriceInternal(mint) }
        singleflightMutex.withLock {
            inFlight.putIfAbsent(mint, def)?.let { takeover ->
                def.cancel()
                return@withLock runCatching { takeover.await() }.getOrNull()
            }
        }
        try {
            def.await()
        } finally {
            inFlight.remove(mint)
        }
    }

    suspend fun getTokenPrices(mints: List<String>): Map<String, TokenPrice> = coroutineScope {
        val out = mutableMapOf<String, TokenPrice>()

        // cached
        val toFetch = mints.filter { mint ->
            getCachedPrice(mint)?.also { out[mint] = it } == null
        }
        if (toFetch.isEmpty()) return@coroutineScope out

        // handle SOL among them
        val solNeed = toFetch.any { it == SOL_MINT }
        if (solNeed) {
            getSolPrice()?.let { out[SOL_MINT] = it; priceCache[SOL_MINT] = it }
        }

        // others via Jupiter v6 batched (chunked), 1 request per chunk
        val others = toFetch.filter { it != SOL_MINT }
        if (others.isNotEmpty()) {
            val chunks = others.chunked(JUP_BATCH_SIZE)
            for (chunk in chunks) {
                if (!jupLimiter.acquire()) {
                    logger.debug("Jupiter limiter saturated (batch). Returning partial results.")
                    break
                }
                val res = runCatching {
                    retrying(attempts = 2) { getBatchPricesFromJupiterV6(chunk) }
                }.getOrElse { emptyMap() }

                res.forEach { (mint, price) ->
                    out[mint] = price
                    priceCache[mint] = price
                    priceMissCache.remove(mint)
                }
            }

            // DexScreener for leftovers (parallel but not rate-limited here)
            val leftover = others.filterNot { out.containsKey(it) }
            val jobs = leftover.map { mint ->
                async {
                    getPriceFromDexScreener(mint)?.also {
                        out[mint] = it
                        priceCache[mint] = it
                        priceMissCache.remove(mint)
                    }
                }
            }
            jobs.forEach { it.await() }
        }

        out
    }

    fun cleanupCache() {
        val now = System.currentTimeMillis()
        val expired = priceCache.entries.filter { (k, v) ->
            val ttl = if (k == SOL_MINT) solCacheTtlMs else tokenCacheTtlMs
            now - v.timestamp > ttl
        }.map { it.key }
        expired.forEach { priceCache.remove(it) }
        if (expired.isNotEmpty()) {
            logger.info("🧹 Cleaned ${expired.size} expired price cache entries")
        }
    }

    fun getPriceMissCount(mint: String): Int = priceMissCache[mint]?.consecutiveMisses ?: 0
    fun resetPriceMissCount(mint: String) { priceMissCache.remove(mint) }

    fun getCacheStats(): Map<String, Any> = mapOf(
        "cacheSize" to priceCache.size,
        "cachedTokens" to priceCache.keys.toList(),
        "priceMisses" to priceMissCache.mapValues { (_, s) ->
            mapOf("consecutive" to s.consecutiveMisses, "total" to s.totalMisses, "first" to s.firstMissTime)
        }
    )

    // ---------------- internals ----------------

    private fun getCachedPrice(mint: String): TokenPrice? {
        val cached = priceCache[mint] ?: return null
        val ttl = if (mint == SOL_MINT) solCacheTtlMs else tokenCacheTtlMs
        return if (System.currentTimeMillis() - cached.timestamp < ttl) cached else null
    }

    private suspend fun fetchPriceInternal(mint: String): TokenPrice? {
        logger.debug("💰 Fetching price for token: $mint")

        // SOL path: CG first (rate-limited), fallback to Jupiter v6
        if (mint == SOL_MINT) {
            getSolPrice()?.let { priceCache[mint] = it; recordPriceSuccess(mint); return it }
            recordPriceMiss(mint)
            return null
        }

        var final: TokenPrice? = null

        // 1) Pump.fun API (best for pump coins)
        if (final == null) {
            final = runCatching { getPriceFromPumpFun(mint) }.getOrNull()
        }

        // 2) Jupiter v6 (rate-limited & retried)
        if (final == null && jupLimiter.acquire()) {
            final = runCatching {
                retrying(attempts = 2) { getPriceFromJupiterV6(mint) }
            }.getOrNull()
        }

        // 3) DEX Aggregators (Birdeye, GeckoTerminal, 1inch, Raydium)
        if (final == null) {
            final = runCatching { getPriceFromDexAggregators(mint) }.getOrNull()
        }

        // 4) DexScreener (USD or SOL→USD conversion)
        if (final == null) {
            final = runCatching { getPriceFromDexScreenerWithSolConversion(mint) }.getOrNull()
        }

        // 5) Web scraping fallback (DexScreener, DexTools, Solscan, CoinMarketCap)
        if (final == null) {
            final = runCatching { getPriceFromWebSources(mint) }.getOrNull()
        }

        // 6) Jupiter v4 legacy as last resort (also rate-limited)
        if (final == null && jupLimiter.acquire()) {
            final = runCatching { getPriceFromJupiter(mint) }.getOrNull()
        }

        if (final != null) {
            priceCache[mint] = final
            recordPriceSuccess(mint)
            logger.info("✅ Price found for $mint: $${final.priceUsd} from ${final.source}")
        } else {
            recordPriceMiss(mint)
            logger.warn("❌ No price found for $mint from any source")
        }
        return final
    }

    /** SOL with CG (limited); fallback to Jupiter v6 price for SOL if CG unavailable/limited. */
    private suspend fun getSolPrice(): TokenPrice? {
        // Try CG within limiter
        val cgAllowed = cgLimiter.acquire()
        val cg = if (cgAllowed) {
            runCatching { coinGeckoClient.getSolPrice() }.getOrNull()
        } else null

        if (cg != null) {
            return TokenPrice(
                mint = SOL_MINT,
                symbol = "SOL",
                priceUsd = cg.first,
                priceUsdt = cg.second,
                source = "CoinGecko"
            )
        }

        // Fallback: Jupiter v6 price for SOL, still rate-limited
        if (jupLimiter.acquire()) {
            val j = runCatching { jupiterPriceClient.getPrice(SOL_MINT) }.getOrNull()
            if (j != null && j > 0) {
                return TokenPrice(
                    mint = SOL_MINT,
                    symbol = "SOL",
                    priceUsd = j,
                    priceUsdt = j,
                    source = "Jupiter-v6"
                )
            }
        }

        // If both blocked, try stale cache
        return getCachedPrice(SOL_MINT)
    }

    private suspend fun getPriceFromDexScreener(mint: String): TokenPrice? {
        return try {
            val response = dexScreenerClient.getPairsByToken(mint)
            val pair = response.pairs?.firstOrNull { it.baseToken.address == mint }
            val priceUsdStr = pair?.priceUsd
            val priceUsd = priceUsdStr?.toDoubleOrNull()
            if (priceUsd != null && priceUsd > 0.0) {
                TokenPrice(
                    mint = mint,
                    symbol = pair.baseToken.symbol.takeIf { !it.isNullOrEmpty() }
                        ?: WELL_KNOWN_TOKENS[mint]
                        ?: mint.take(8),
                    priceUsd = priceUsd,
                    source = "DexScreener"
                )
            } else null
        } catch (e: Exception) {
            logger.debug("DexScreener price fetch failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun getPriceFromDexScreenerWithSolConversion(mint: String): TokenPrice? {
        return try {
            val response = dexScreenerClient.getPairsByToken(mint)
            val pair = response.pairs?.firstOrNull { it.baseToken.address == mint }

            // 1) Direct USD price
            val directUsd = pair?.priceUsd?.toDoubleOrNull()
            if (directUsd != null && directUsd > 0.0) {
                return TokenPrice(
                    mint = mint,
                    symbol = pair.baseToken.symbol.takeIf { !it.isNullOrEmpty() }
                        ?: WELL_KNOWN_TOKENS[mint]
                        ?: mint.take(8),
                    priceUsd = directUsd,
                    source = "DexScreener-USD"
                )
            }

            // 2) SOL pair conversion
            val solPair = response.pairs?.firstOrNull {
                it.baseToken.address == mint &&
                        (it.quoteToken.address == SOL_MINT || it.quoteToken.symbol.equals("SOL", true))
            }
            val solQuote = solPair?.priceUsd?.toDoubleOrNull()
                ?: solPair?.priceNative?.toDoubleOrNull()

            val solUsd = getSolPrice()?.priceUsd
                ?: getCachedPrice(SOL_MINT)?.priceUsd

            if (solQuote != null && solQuote > 0.0 && solUsd != null && solUsd > 0.0) {
                val usd = solQuote * solUsd
                return TokenPrice(
                    mint = mint,
                    symbol = solPair?.baseToken?.symbol?.takeIf { !it.isNullOrEmpty() }
                        ?: WELL_KNOWN_TOKENS[mint]
                        ?: mint.take(8),
                    priceUsd = usd,
                    source = "DexScreener-SOL"
                )
            }
            null
        } catch (e: Exception) {
            logger.debug("DexScreener SOL conversion failed for $mint: ${e.message}")
            null
        }
    }

    private suspend fun getPriceFromJupiterV6(mint: String): TokenPrice? {
        val price = jupiterPriceClient.getPrice(mint) ?: return null
        if (price <= 0.0) return null
        return TokenPrice(
            mint = mint,
            symbol = WELL_KNOWN_TOKENS[mint] ?: mint.take(8),
            priceUsd = price,
            source = "Jupiter-v6"
        )
    }

    private suspend fun getBatchPricesFromJupiterV6(mints: List<String>): Map<String, TokenPrice> {
        val raw = jupiterPriceClient.getPrices(mints)
        return raw.mapValues { (mint, p) ->
            TokenPrice(
                mint = mint,
                symbol = WELL_KNOWN_TOKENS[mint] ?: mint.take(8),
                priceUsd = p,
                source = "Jupiter-v6"
            )
        }
    }

    private suspend fun getPriceFromJupiter(mint: String): TokenPrice? {
        return try {
            val response: JupiterPriceResponse =
                httpClient.get("https://price.jup.ag/v4/price?ids=$mint").body()
            val td = response.data?.get(mint) ?: return null
            if (td.price <= 0) return null
            TokenPrice(
                mint = mint,
                symbol = td.mintSymbol.ifEmpty { WELL_KNOWN_TOKENS[mint] ?: mint.take(8) },
                priceUsd = td.price,
                source = "Jupiter"
            )
        } catch (e: Exception) {
            logger.debug("Jupiter v4 price fetch failed for $mint: ${e.message}")
            null
        }
    }

    private fun recordPriceSuccess(mint: String) { priceMissCache.remove(mint) }

    private fun recordPriceMiss(mint: String) {
        val now = System.currentTimeMillis()
        val stats = priceMissCache.computeIfAbsent(mint) { PriceMissStats() }
        stats.consecutiveMisses++
        stats.totalMisses++
        val shouldLog = now - stats.lastLogTime > 60_000L || stats.consecutiveMisses <= 3
        if (shouldLog) {
            logger.warn("PRICE_MISS $mint strikes=${stats.consecutiveMisses}, total=${stats.totalMisses}")
            stats.lastLogTime = now
        }
    }

    // ---- NEW: Enhanced price fetching methods ----
    
    private suspend fun getPriceFromPumpFun(mint: String): TokenPrice? {
        val price = pumpFunPriceClient.getTokenPrice(mint) ?: return null
        if (price <= 0.0) return null
        return TokenPrice(
            mint = mint,
            symbol = WELL_KNOWN_TOKENS[mint] ?: mint.take(8),
            priceUsd = price,
            source = "Pump.fun"
        )
    }

    private suspend fun getPriceFromDexAggregators(mint: String): TokenPrice? {
        val price = dexAggregatorPriceClient.getTokenPriceFromMultipleSources(mint) ?: return null
        if (price <= 0.0) return null
        return TokenPrice(
            mint = mint,
            symbol = WELL_KNOWN_TOKENS[mint] ?: mint.take(8),
            priceUsd = price,
            source = "DEX-Aggregators"
        )
    }

    private suspend fun getPriceFromWebSources(mint: String): TokenPrice? {
        val price = webScrapingPriceClient.getTokenPriceFromWebSources(mint) ?: return null
        if (price <= 0.0) return null
        return TokenPrice(
            mint = mint,
            symbol = WELL_KNOWN_TOKENS[mint] ?: mint.take(8),
            priceUsd = price,
            source = "Web-Scraping"
        )
    }

    // Enhanced token price with multiple fallbacks
    suspend fun getTokenPriceWithAllSources(mint: String): TokenPrice? {
        // Use the existing singleflight and caching mechanism
        return getTokenPrice(mint)
    }

    // Get token info from web sources for better metadata
    suspend fun getTokenInfoFromWeb(mint: String): Map<String, String?> {
        return webScrapingPriceClient.getTokenInfoFromWeb(mint)
    }

    // Check if token is active on Pump.fun
    suspend fun isTokenActiveOnPumpFun(mint: String): Boolean {
        return pumpFunPriceClient.isTokenActive(mint)
    }

    // Convenience for USD valuation (unchanged)
    suspend fun calculateUsdValue(mint: String, amount: String?, decimals: Int?): Double? {
        if (amount == null || decimals == null) return null
        val price = getTokenPrice(mint) ?: return null
        val ui = runCatching { amount.toDouble() / Math.pow(10.0, decimals.toDouble()) }.getOrNull() ?: return null
        return ui * price.priceUsd
    }
}
