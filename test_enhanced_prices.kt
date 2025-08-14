import com.bswap.server.service.*
import com.bswap.server.data.dexscreener.DexScreenerClientImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

suspend fun main() {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    
    val dexScreenerClient = DexScreenerClientImpl(client)
    val priceService = PriceService(client, dexScreenerClient)
    
    // Test with some example pump.fun tokens
    val testTokens = listOf(
        "So11111111111111111111111111111111111111112", // SOL
        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", // USDC
        "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263", // BONK
        // Add some pump.fun tokens here if you know their addresses
    )
    
    println("🚀 Testing Enhanced Price Discovery for Pump Coins")
    println("=" * 60)
    
    for (token in testTokens) {
        println("\n🔍 Fetching price for: $token")
        try {
            val price = priceService.getTokenPrice(token)
            if (price != null) {
                println("✅ Price found: $${price.priceUsd} from ${price.source}")
                println("   Symbol: ${price.symbol}")
                println("   Timestamp: ${price.timestamp}")
            } else {
                println("❌ No price found")
            }
            
            // Test if it's active on Pump.fun
            val isActive = priceService.isTokenActiveOnPumpFun(token)
            println("   Pump.fun active: $isActive")
            
        } catch (e: Exception) {
            println("❌ Error fetching price: ${e.message}")
        }
        
        Thread.sleep(1000) // Rate limiting
    }
    
    println("\n🎯 Enhanced price discovery test completed!")
    client.close()
}

if (args.isNotEmpty() && args[0] == "test") {
    runBlocking {
        main()
    }
}