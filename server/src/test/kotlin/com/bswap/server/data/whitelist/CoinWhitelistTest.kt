package com.bswap.server.data.whitelist

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

class CoinWhitelistTest {

    @Test
    fun `test coin whitelist source initialization`() = runTest {
        val source = CoinWhitelistSource()
        val whitelist = source.whitelist.first()
        
        assertTrue(whitelist.coins.isNotEmpty())
        assertTrue(whitelist.coins.any { it.symbol == "SOL" })
        assertTrue(whitelist.coins.any { it.symbol == "USDC" })
    }

    @Test
    fun `test adding and removing coins`() = runTest {
        val source = CoinWhitelistSource()
        
        // Add a new coin
        val newCoin = WhitelistCoin(
            symbol = "TEST",
            priority = 100,
            enabled = true,
            tags = listOf("test")
        )
        source.updateCoin(newCoin)
        
        val updatedWhitelist = source.whitelist.first()
        assertTrue(updatedWhitelist.coins.any { it.symbol == "TEST" })
        assertTrue(source.isSymbolAllowed("TEST"))
        
        // Remove the coin
        source.removeCoin("TEST")
        val finalWhitelist = source.whitelist.first()
        assertFalse(finalWhitelist.coins.any { it.symbol == "TEST" })
        assertFalse(source.isSymbolAllowed("TEST"))
    }

    @Test
    fun `test enabling and disabling coins`() = runTest {
        val source = CoinWhitelistSource()
        
        // Add a coin and disable it
        val coin = WhitelistCoin(
            symbol = "TESTCOIN",
            priority = 50,
            enabled = true
        )
        source.updateCoin(coin)
        
        assertTrue(source.isSymbolAllowed("TESTCOIN"))
        
        // Disable the coin
        source.setCoinEnabled("TESTCOIN", false)
        assertFalse(source.isSymbolAllowed("TESTCOIN"))
        
        // Re-enable the coin
        source.setCoinEnabled("TESTCOIN", true)
        assertTrue(source.isSymbolAllowed("TESTCOIN"))
    }

    @Test
    fun `test priority sorting`() = runTest {
        val source = CoinWhitelistSource()
        
        // Add coins with different priorities
        source.updateCoin(WhitelistCoin("LOW", priority = 10, enabled = true))
        source.updateCoin(WhitelistCoin("HIGH", priority = 100, enabled = true))
        source.updateCoin(WhitelistCoin("MID", priority = 50, enabled = true))
        
        val enabledCoins = source.getEnabledCoins()
        val testCoins = enabledCoins.filter { it.symbol in setOf("LOW", "HIGH", "MID") }
        
        // Should be sorted by priority descending
        assertTrue(testCoins[0].priority > testCoins[1].priority)
        assertTrue(testCoins[1].priority > testCoins[2].priority)
    }

    @Test
    fun `test whitelist loader`() {
        val loader = CoinWhitelistLoader()
        
        // Test creating from symbols
        val symbols = listOf("BTC", "ETH", "SOL")
        val whitelist = loader.createFromSymbols(symbols, defaultPriority = 100)
        
        assertEquals(3, whitelist.coins.size)
        assertTrue(whitelist.coins.all { it.enabled })
        assertEquals("BTC", whitelist.coins[0].symbol)
        assertEquals(100, whitelist.coins[0].priority)
        assertEquals(99, whitelist.coins[1].priority) // Priority decreases
    }

    @Test
    fun `test loading from resources`() {
        val loader = CoinWhitelistLoader()
        val whitelist = loader.loadFromResources()
        
        assertNotNull(whitelist)
        assertTrue(whitelist!!.coins.isNotEmpty())
        assertTrue(whitelist.coins.any { it.symbol == "SOL" })
    }
}