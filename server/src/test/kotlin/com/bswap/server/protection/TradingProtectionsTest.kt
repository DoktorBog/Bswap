package com.bswap.server.protection

import com.bswap.server.config.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlin.math.*

class PositionManagerTest {
    
    private lateinit var positionManager: PositionManager
    private lateinit var config: RiskManagementConfig
    
    @BeforeEach
    fun setup() {
        config = RiskManagementConfig()
        positionManager = PositionManager(config)
    }
    
    @Test
    fun `should create position with correct initial values`() {
        val position = positionManager.addPosition("TEST_MINT", 1.0, 1000.0)
        
        assertEquals("TEST_MINT", position.mint)
        assertEquals(1.0, position.entryPrice)
        assertEquals(1000.0, position.amountUsd)
        assertEquals(1.0, position.currentPrice)
        assertEquals(1.0, position.peak)
        assertFalse(position.trailingStopEnabled)
        assertEquals(1, positionManager.getPositionCount())
    }
    
    @Test
    fun `should update position price and track peak`() {
        val position = positionManager.addPosition("TEST_MINT", 1.0, 1000.0)
        
        // Update to higher price
        positionManager.updatePosition("TEST_MINT", 1.5)
        assertEquals(1.5, position.currentPrice)
        assertEquals(1.5, position.peak)
        
        // Update to lower price - peak should remain
        positionManager.updatePosition("TEST_MINT", 1.2)
        assertEquals(1.2, position.currentPrice)
        assertEquals(1.5, position.peak)
    }
    
    @Test
    fun `should calculate PnL correctly`() {
        val position = positionManager.addPosition("TEST_MINT", 1.0, 1000.0)
        
        // 50% profit
        positionManager.updatePosition("TEST_MINT", 1.5)
        assertEquals(0.5, position.unrealizedPnLPercent, 0.001)
        
        // 20% loss
        positionManager.updatePosition("TEST_MINT", 0.8)
        assertEquals(-0.2, position.unrealizedPnLPercent, 0.001)
    }
    
    @Test
    fun `should track price history`() {
        val position = positionManager.addPosition("TEST_MINT", 1.0, 1000.0)
        
        positionManager.updatePosition("TEST_MINT", 1.1)
        positionManager.updatePosition("TEST_MINT", 1.2)
        positionManager.updatePosition("TEST_MINT", 1.15)
        
        assertEquals(4, position.priceHistory.size) // Including entry price
        assertEquals(listOf(1.0, 1.1, 1.2, 1.15), position.priceHistory)
    }
    
    @Test
    fun `should calculate volatility correctly`() {
        val position = positionManager.addPosition("TEST_MINT", 1.0, 1000.0)
        
        // Add some price movements
        positionManager.updatePosition("TEST_MINT", 1.1)
        positionManager.updatePosition("TEST_MINT", 0.9)
        positionManager.updatePosition("TEST_MINT", 1.05)
        positionManager.updatePosition("TEST_MINT", 0.95)
        
        assertTrue(position.volatility > 0.0)
    }
    
    @Test
    fun `should remove position correctly`() {
        positionManager.addPosition("TEST_MINT", 1.0, 1000.0)
        assertEquals(1, positionManager.getPositionCount())
        
        val removed = positionManager.removePosition("TEST_MINT")
        assertNotNull(removed)
        assertEquals(0, positionManager.getPositionCount())
        
        val notFound = positionManager.removePosition("NON_EXISTENT")
        assertNull(notFound)
    }
}

class RugDetectorTest {
    
    private lateinit var rugDetector: RugDetector
    private lateinit var config: RugDetectionConfig
    
    @BeforeEach
    fun setup() {
        config = RugDetectionConfig()
        rugDetector = RugDetector(config)
    }
    
    @Test
    fun `should detect normal market conditions`() {
        val analysis = rugDetector.analyzeTick("TEST_MINT", 1.0, 1000.0)
        
        assertFalse(analysis.isRugPull)
        assertEquals(RugDetector.RugUrgency.LOW, analysis.urgency)
        assertTrue(analysis.confidence < 0.5)
    }
    
    @Test
    fun `should detect price drop rug pull`() {
        // Add some normal price movements first
        rugDetector.analyzeTick("TEST_MINT", 1.0, 1000.0)
        rugDetector.analyzeTick("TEST_MINT", 1.05, 1100.0)
        rugDetector.analyzeTick("TEST_MINT", 1.02, 1050.0)
        
        // Simulate massive price drop (50% in one tick)
        val analysis = rugDetector.analyzeTick("TEST_MINT", 0.5, 800.0)
        
        assertTrue(analysis.isRugPull)
        assertTrue(analysis.confidence > 0.7)
        assertEquals(RugDetector.RugUrgency.HIGH, analysis.urgency)
        assertTrue(analysis.reasons.contains("Extreme price drop"))
    }
    
    @Test
    fun `should detect low volume rug pull`() {
        // Add normal trading first
        rugDetector.analyzeTick("TEST_MINT", 1.0, 1000.0)
        rugDetector.analyzeTick("TEST_MINT", 1.0, 1000.0)
        
        // Simulate volume drop
        val analysis = rugDetector.analyzeTick("TEST_MINT", 1.0, 50.0) // 95% volume drop
        
        assertTrue(analysis.isRugPull)
        assertTrue(analysis.reasons.contains("Volume collapse"))
    }
    
    @Test
    fun `should handle cleanup correctly`() {
        // Add old data
        rugDetector.analyzeTick("TEST_MINT", 1.0, 1000.0)
        
        // Force cleanup by manipulating time (this is a simplification)
        rugDetector.cleanup()
        
        // Should still function normally
        val analysis = rugDetector.analyzeTick("TEST_MINT", 1.0, 1000.0)
        assertFalse(analysis.isRugPull)
    }
}

class AntiChopFilterTest {
    
    private lateinit var antiChopFilter: AntiChopFilter
    private lateinit var config: AntiChopConfig
    
    @BeforeEach
    fun setup() {
        config = AntiChopConfig()
        antiChopFilter = AntiChopFilter(config)
    }
    
    @Test
    fun `should detect trending market`() {
        val trendingPrices = listOf(1.0, 1.02, 1.04, 1.06, 1.08, 1.10)
        val marketState = antiChopFilter.analyzeMarket("TEST_MINT", trendingPrices)
        
        assertEquals(AntiChopFilter.MarketState.TRENDING, marketState)
        assertTrue(antiChopFilter.shouldAllowTrade("TEST_MINT"))
    }
    
    @Test
    fun `should detect choppy market`() {
        val choppyPrices = listOf(1.0, 1.05, 0.98, 1.03, 0.97, 1.02, 0.99, 1.01)
        val marketState = antiChopFilter.analyzeMarket("TEST_MINT", choppyPrices)
        
        assertEquals(AntiChopFilter.MarketState.CHOPPY, marketState)
        // Depending on config, may or may not allow trading
    }
    
    @Test
    fun `should calculate trend strength correctly`() {
        val strongTrend = listOf(1.0, 1.1, 1.2, 1.3, 1.4)
        val trendStrength = antiChopFilter.calculateTrendStrength(strongTrend)
        
        assertTrue(trendStrength > 0.8)
    }
    
    @Test
    fun `should handle insufficient data`() {
        val shortPrices = listOf(1.0, 1.01)
        val marketState = antiChopFilter.analyzeMarket("TEST_MINT", shortPrices)
        
        assertEquals(AntiChopFilter.MarketState.UNKNOWN, marketState)
        assertTrue(antiChopFilter.shouldAllowTrade("TEST_MINT")) // Default to allow
    }
}

class TimeBasedExitManagerTest {
    
    private lateinit var timeBasedExitManager: TimeBasedExitManager
    private lateinit var config: TimeBasedExitConfig
    
    @BeforeEach
    fun setup() {
        config = TimeBasedExitConfig()
        timeBasedExitManager = TimeBasedExitManager(config)
    }
    
    @Test
    fun `should not recommend exit for new position`() {
        val position = Position("TEST_MINT", 1.0, 1000.0)
        val recommendation = timeBasedExitManager.analyzeTimeBasedExit(position)
        
        assertFalse(recommendation.shouldExit)
        assertEquals("No time-based exit needed", recommendation.reason)
    }
    
    @Test
    fun `should recommend exit for old unprofitable position`() {
        val position = Position("TEST_MINT", 1.0, 1000.0)
        position.currentPrice = 0.9 // 10% loss
        // Simulate old position
        val oldTime = System.currentTimeMillis() - (config.maxHoldTimeUnprofitableMs + 1000)
        position.entryTime = oldTime
        
        val recommendation = timeBasedExitManager.analyzeTimeBasedExit(position)
        
        assertTrue(recommendation.shouldExit)
        assertTrue(recommendation.reason.contains("unprofitable"))
    }
    
    @Test
    fun `should handle profitable position differently`() {
        val position = Position("TEST_MINT", 1.0, 1000.0)
        position.currentPrice = 1.1 // 10% profit
        // Simulate old position
        val oldTime = System.currentTimeMillis() - (config.maxHoldTimeUnprofitableMs + 1000)
        position.entryTime = oldTime
        
        val recommendation = timeBasedExitManager.analyzeTimeBasedExit(position)
        
        // Should not exit profitable positions based on time alone
        assertFalse(recommendation.shouldExit)
    }
}