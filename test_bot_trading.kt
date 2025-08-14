import com.bswap.server.*
import com.bswap.server.service.*
import com.bswap.server.data.dexscreener.DexScreenerClientImpl
import com.bswap.server.data.dexscreener.models.TokenProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.flowOf

suspend fun main() {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    
    println("🤖 Testing Bot Trading Functionality")
    println("=" * 50)
    
    // Create bot components
    val dexScreenerClient = DexScreenerClientImpl(client)
    val priceService = PriceService(client, dexScreenerClient)
    
    // Test price discovery first
    println("\n🔍 Testing Price Discovery:")
    val testMints = listOf(
        "So11111111111111111111111111111111111111112", // SOL
        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", // USDC
        "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263"  // BONK
    )
    
    for (mint in testMints) {
        try {
            val price = priceService.getTokenPrice(mint)
            if (price != null) {
                println("✅ $mint: $${price.priceUsd} (${price.source})")
            } else {
                println("❌ $mint: No price found")
            }
        } catch (e: Exception) {
            println("❌ $mint: Error - ${e.message}")
        }
    }
    
    // Test manual token discovery simulation
    println("\n🎯 Simulating Token Discovery:")
    
    // Create a test token profile
    val testProfile = TokenProfile(
        tokenAddress = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263", // BONK
        chainId = "solana",
        dexId = "raydium",
        url = "https://dexscreener.com/solana/test",
        pairAddress = "test-pair",
        labels = emptyList(),
        baseToken = null,
        quoteToken = null,
        priceNative = "0.001",
        priceUsd = "0.00002",
        txns = null,
        volume = null,
        priceChange = null,
        liquidity = null,
        pairCreatedAt = System.currentTimeMillis()
    )
    
    println("📋 Created test profile for ${testProfile.tokenAddress}")
    println("💡 In a real scenario, this would trigger the RSI strategy")
    println("💡 Check the bot logs when running to see if tokens are being discovered")
    
    println("\n🎯 Manual Buy Test:")
    println("💡 Use the bot's singleTrade() function to manually test buying:")
    println("💡 Example: bot.singleTrade(\"DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263\")")
    
    client.close()
    println("\n✅ Test completed! Check the bot logs for token discovery and trading activity.")
}

if (args.isNotEmpty() && args[0] == "test") {
    runBlocking {
        main()
    }
}