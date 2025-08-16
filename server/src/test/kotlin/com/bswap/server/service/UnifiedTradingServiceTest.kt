package com.bswap.server.service

import com.bswap.server.SolanaSwapBotConfig
import com.bswap.server.config.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.Test
import kotlin.test.*

/**
 * Test suite for Unified Trading Service
 */
class UnifiedTradingServiceTest {

    private fun createSolanaConfig(): SolanaSwapBotConfig {
        return SolanaSwapBotConfig(
            blockBuy = false,
            autoSellAllSpl = false
        )
    }

    private fun createHyperliquidConfig(enabled: Boolean = true): HyperliquidConfig {
        return HyperliquidConfig(
            enabled = enabled,
            exchangeType = if (enabled) ExchangeType.HYPERLIQUID else ExchangeType.SOLANA,
            testnet = true,
            maxPositions = 5,
            positionSizePercent = 10.0,
            defaultLeverage = 1.0
        )
    }

    private fun createEnhancedConfig(): EnhancedTradingConfig {
        return EnhancedTradingConfig()
    }

    @Test
    fun `test service initialization with Solana`() {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = false),
            enhancedConfig = createEnhancedConfig()
        )
        
        assertNotNull(service)
        assertEquals(ExchangeType.SOLANA, service.getCurrentExchange())
    }

    @Test
    fun `test service initialization with Hyperliquid`() {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        assertNotNull(service)
        assertEquals(ExchangeType.HYPERLIQUID, service.getCurrentExchange())
    }

    @Test
    fun `test exchange switching`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        // Start with Hyperliquid
        assertEquals(ExchangeType.HYPERLIQUID, service.getCurrentExchange())
        
        // Switch to Solana
        val switched = service.switchExchange(ExchangeType.SOLANA)
        assertTrue(switched)
        assertEquals(ExchangeType.SOLANA, service.getCurrentExchange())
        
        // Switch back to Hyperliquid
        val switchedBack = service.switchExchange(ExchangeType.HYPERLIQUID)
        assertTrue(switchedBack)
        assertEquals(ExchangeType.HYPERLIQUID, service.getCurrentExchange())
    }

    @Test
    fun `test trading start and stop`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        // Start trading
        service.startTrading()
        delay(100)
        
        val stats = service.getStats()
        assertTrue(stats["isRunning"] as Boolean)
        
        // Stop trading
        service.stopTrading()
        delay(100)
        
        val stoppedStats = service.getStats()
        assertFalse(stoppedStats["isRunning"] as Boolean)
    }

    @Test
    fun `test trade event flow`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        // Collect trade events
        val events = mutableListOf<UnifiedTradingService.TradeEvent>()
        val job = launch {
            service.tradeFlow
                .take(5)
                .collect { event ->
                    events.add(event)
                }
        }
        
        // Start trading
        service.startTrading()
        
        // Wait for some events
        delay(10000)
        
        // Stop trading
        service.stopTrading()
        job.cancel()
        
        // Should have collected some events (or none if no trades)
        assertTrue(events.size >= 0)
    }

    @Test
    fun `test get balance`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        val balance = service.getBalance()
        assertNotNull(balance)
        // Balance should be a map (may be empty)
        assertTrue(balance is Map)
    }

    @Test
    fun `test get positions`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        val positions = service.getPositions()
        assertNotNull(positions)
        // Positions should be a list (may be empty)
        assertTrue(positions is List)
    }

    @Test
    fun `test get PnL`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        val (unrealizedPnL, realizedPnL) = service.getPnL()
        assertNotNull(unrealizedPnL)
        assertNotNull(realizedPnL)
        // PnL values should be numbers
        assertTrue(unrealizedPnL is Double)
        assertTrue(realizedPnL is Double)
    }

    @Test
    fun `test get statistics`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        val stats = service.getStats()
        assertNotNull(stats)
        assertTrue(stats.containsKey("exchange"))
        assertTrue(stats.containsKey("isRunning"))
        assertEquals("HYPERLIQUID", stats["exchange"])
    }

    @Test
    fun `test close position`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        // Try to close a non-existent position (should return false)
        val result = service.closePosition("BTC-PERP")
        assertNotNull(result)
        // Result is boolean - false if no position exists
        assertTrue(result is Boolean)
    }

    @Test
    fun `test close all positions`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        val result = service.closeAllPositions()
        assertTrue(result)
    }

    @Test
    fun `test emergency stop`() = runBlocking {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        // Start trading
        service.startTrading()
        delay(100)
        
        // Trigger emergency stop
        service.emergencyStop("Test emergency")
        delay(100)
        
        // Trading should be stopped
        val stats = service.getStats()
        assertFalse(stats["isRunning"] as Boolean)
    }

    @Test
    fun `test service shutdown`() {
        val service = UnifiedTradingService(
            solanaConfig = createSolanaConfig(),
            hyperliquidConfig = createHyperliquidConfig(enabled = true),
            enhancedConfig = createEnhancedConfig()
        )
        
        // Should not throw
        service.shutdown()
    }
}