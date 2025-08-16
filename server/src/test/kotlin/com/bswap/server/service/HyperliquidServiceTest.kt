package com.bswap.server.service

import com.bswap.server.config.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Test
import kotlin.test.*

/**
 * Test suite for Hyperliquid Service integration
 * Note: These are integration tests that require API credentials for full testing
 */
class HyperliquidServiceTest {

    // Create test configuration
    private fun createTestConfig(testnet: Boolean = true): HyperliquidConfig {
        return HyperliquidConfig(
            enabled = true,
            exchangeType = ExchangeType.HYPERLIQUID,
            apiKey = System.getenv("HYPERLIQUID_API_KEY") ?: "",
            apiSecret = System.getenv("HYPERLIQUID_API_SECRET") ?: "",
            walletAddress = System.getenv("HYPERLIQUID_WALLET") ?: "",
            privateKey = System.getenv("HYPERLIQUID_PRIVATE_KEY") ?: "",
            testnet = testnet,
            maxRequestsPerSecond = 5,
            logLevel = LogLevel.DEBUG
        )
    }

    @Test
    fun `test service initialization`() {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        assertNotNull(service)
    }

    @Test
    fun `test market loading`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank()) {
            println("Skipping test - no API credentials")
            return@runBlocking
        }
        
        service.loadMarkets()
        // Markets should be loaded
        val stats = service.getStats()
        assertTrue(stats.isNotEmpty())
    }

    @Test
    fun `test balance fetching`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank()) {
            println("Skipping test - no API credentials")
            return@runBlocking
        }
        
        val balances = service.fetchBalances()
        assertNotNull(balances)
        
        // Check USDC balance exists
        val usdcBalance = service.getBalance("USDC")
        if (usdcBalance != null) {
            assertTrue(usdcBalance.asset == "USDC")
            assertTrue(usdcBalance.total >= 0)
        }
    }

    @Test
    fun `test order book fetching`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank()) {
            println("Skipping test - no API credentials")
            return@runBlocking
        }
        
        service.loadMarkets()
        val orderBook = service.getOrderBook("BTC-PERP", 10)
        
        if (orderBook != null) {
            assertNotNull(orderBook["bids"])
            assertNotNull(orderBook["asks"])
        }
    }

    @Test
    fun `test ticker fetching`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank()) {
            println("Skipping test - no API credentials")
            return@runBlocking
        }
        
        val ticker = service.getTicker("BTC-PERP")
        
        if (ticker != null) {
            assertNotNull(ticker["last"])
            assertNotNull(ticker["bid"])
            assertNotNull(ticker["ask"])
        }
    }

    @Test
    fun `test position fetching`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank()) {
            println("Skipping test - no API credentials")
            return@runBlocking
        }
        
        val positions = service.fetchPositions()
        assertNotNull(positions)
        
        // Positions list should exist (may be empty)
        assertTrue(positions is List)
    }

    @Test
    fun `test open orders fetching`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank()) {
            println("Skipping test - no API credentials")
            return@runBlocking
        }
        
        val orders = service.fetchOpenOrders()
        assertNotNull(orders)
        
        // Orders list should exist (may be empty)
        assertTrue(orders is List)
    }

    @Test
    fun `test funding rate fetching`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank()) {
            println("Skipping test - no API credentials")
            return@runBlocking
        }
        
        val fundingRate = service.getFundingRate("BTC-PERP")
        
        if (fundingRate != null) {
            assertNotNull(fundingRate.fundingRate)
            assertNotNull(fundingRate.markPrice)
            assertTrue(fundingRate.symbol == "BTC-PERP")
        }
    }

    @Test
    fun `test OHLCV data fetching`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank()) {
            println("Skipping test - no API credentials")
            return@runBlocking
        }
        
        val ohlcv = service.getOHLCV("BTC-PERP", "1h", 24)
        
        if (ohlcv != null) {
            assertTrue(ohlcv.isNotEmpty())
            // Each candle should have 6 values: [timestamp, open, high, low, close, volume]
            ohlcv.forEach { candle ->
                assertEquals(6, candle.size)
            }
        }
    }

    @Test
    fun `test create and cancel limit order on testnet`() = runBlocking {
        val config = createTestConfig(testnet = true)
        val service = HyperliquidService(config)
        
        // Skip if no credentials
        if (config.apiKey.isBlank() || !config.testnet) {
            println("Skipping test - no API credentials or not on testnet")
            return@runBlocking
        }
        
        // Get current price
        val ticker = service.getTicker("BTC-PERP")
        val currentPrice = ticker?.get("last") as? Double ?: 50000.0
        
        // Create a limit buy order far from market price
        val orderPrice = currentPrice * 0.8 // 20% below market
        val result = service.createOrder(
            symbol = "BTC-PERP",
            side = OrderSide.BUY,
            amount = 0.001, // Small amount
            price = orderPrice,
            type = OrderType.LIMIT
        )
        
        if (result.success && result.orderId != null) {
            println("Order created: ${result.orderId}")
            
            // Wait a bit
            delay(1000)
            
            // Cancel the order
            val cancelled = service.cancelOrder(result.orderId, "BTC-PERP")
            assertTrue(cancelled)
            println("Order cancelled successfully")
        }
    }

    @Test
    fun `test service statistics`() = runBlocking {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        val stats = service.getStats()
        assertNotNull(stats)
        assertTrue(stats.containsKey("positions"))
        assertTrue(stats.containsKey("openOrders"))
        assertTrue(stats.containsKey("requestCount"))
    }

    @Test
    fun `test service shutdown`() {
        val config = createTestConfig()
        val service = HyperliquidService(config)
        
        // Should not throw
        service.shutdown()
    }
}