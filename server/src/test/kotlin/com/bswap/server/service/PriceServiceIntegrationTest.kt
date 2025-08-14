package com.bswap.server.service

import com.bswap.server.data.dexscreener.DexScreenerClientImpl
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Integration tests for PriceService and related pricing sources.
 * These tests perform real network calls to external services (CoinGecko, Jupiter, DexScreener)
 * to validate the end-to-end price discovery pipeline.
 *
 * These are integration tests and may be flaky depending on network conditions and rate limits.
 */
class PriceServiceIntegrationTest {
    private lateinit var httpClient: HttpClient
    private lateinit var dexClient: DexScreenerClientImpl
    private var jupiterSwap: JupiterSwapService? = null
    private lateinit var priceService: PriceService
    private lateinit var coinGeckoClient: SimpleCoinGeckoClient

    @BeforeTest
    fun setup() {
        httpClient = HttpClient(CIO) {
            engine {
                requestTimeout = 30_000
            }
            install(io.ktor.client.plugins.websocket.WebSockets)
            install(ClientContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        dexClient = DexScreenerClientImpl(httpClient)
        // JupiterSwapService requires a URL from ServerConfig; using default is fine for integration
        jupiterSwap = try {
            JupiterSwapService(httpClient)
        } catch (e: Exception) {
            null
        }

        priceService = PriceService(httpClient, dexClient, jupiterSwap)
        coinGeckoClient = SimpleCoinGeckoClient(httpClient)
    }

    private suspend fun isUrlAvailable(url: String): Boolean {
        return try {
            val resp: HttpResponse = httpClient.get(url)
            val code = resp.status.value
            // Treat 200-299 as available; 429 = rate-limited -> not available
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun testSolPriceIsAvailable() = runBlocking {
        val sol = PriceService.SOL_MINT
        // Only assert SOL price if CoinGecko is reachable (CoinGecko is primary SOL source)
        val cgOk = isUrlAvailable("https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd,usdt")
        Assume.assumeTrue(cgOk)

        // Sanity-check CoinGecko via direct raw GET
        val raw = try {
            httpClient.get("https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd,usdt").bodyAsText()
        } catch (e: Exception) {
            "<err:${e.message}>"
        }
        println("CoinGecko raw -> $raw")
        val cgDirect = coinGeckoClient.getSolPrice()
        println("CoinGecko parsed -> $cgDirect")

        val tokenPrice = priceService.getTokenPrice(sol)
        if (tokenPrice == null) {
            println("SOL price was null. CacheStats=${priceService.getCacheStats()}")
        } else {
            println("SOL price discovered: ${tokenPrice.priceUsd} source=${tokenPrice.source}")
        }
        assertNotNull(tokenPrice, "SOL price should be discovered via CoinGecko or other sources")
        assertTrue(tokenPrice!!.priceUsd > 0.0, "SOL price must be > 0")
    }

    @Test
    fun testUsdcPriceIsAvailable() = runBlocking {
        val usdc = PriceService.USDC_MINT
        // USDC price fetching relies on Jupiter/DexScreener availability — if both are unavailable, skip
        val jupOk = isUrlAvailable("https://quote-api.jup.ag/v6/price?ids=$usdc")
        val dsOk = isUrlAvailable("https://api.dexscreener.com/latest/dex/tokens/$usdc")
        Assume.assumeTrue(jupOk || dsOk)

        val tokenPrice = priceService.getTokenPrice(usdc)
        if (tokenPrice == null) {
            println("USDC price was null. CacheStats=${priceService.getCacheStats()}")
        } else {
            println("USDC price discovered: ${tokenPrice.priceUsd} source=${tokenPrice.source}")
        }
        assertNotNull(tokenPrice, "USDC price should be discovered via Jupiter or DexScreener")
        assertTrue(tokenPrice!!.priceUsd > 0.0, "USDC price must be > 0")
    }

    @Test
    fun testBatchPriceFetch() = runBlocking {
        val mints = listOf(PriceService.SOL_MINT, PriceService.USDC_MINT)
        val map = priceService.getTokenPrices(mints)
        if (!map.containsKey(PriceService.SOL_MINT) || !map.containsKey(PriceService.USDC_MINT)) {
            println("Batch result keys=${map.keys}. CacheStats=${priceService.getCacheStats()}")
        } else {
            map.forEach { (k, v) -> println("batch price: $k => ${v.priceUsd} source=${v.source}") }
        }
        assertTrue(map.containsKey(PriceService.SOL_MINT), "Batch fetch should include SOL")
        assertTrue(map.containsKey(PriceService.USDC_MINT), "Batch fetch should include USDC")
        assertTrue(map.values.all { it.priceUsd > 0.0 }, "All returned prices must be > 0")
    }
}
