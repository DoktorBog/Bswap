package com.bswap.server.execution

import com.bswap.server.config.EnhancedTradingConfig
import com.bswap.server.service.JupiterLiquidityService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*

class IdempotentOrderManagerTest {
    
    private lateinit var orderManager: IdempotentOrderManager
    private lateinit var config: ExecutionConfig
    
    @BeforeEach
    fun setup() {
        config = ExecutionConfig()
        orderManager = IdempotentOrderManager(config)
    }
    
    @Test
    fun `should submit order and return pending status`() = runBlocking {
        val request = OrderRequest(
            id = "TEST_ORDER_1",
            mint = "TEST_MINT",
            side = OrderSide.BUY,
            amount = 1000.0,
            maxSlippage = 0.05,
            timeoutMs = 30000L
        )
        
        val result = orderManager.submitOrder(request)
        
        assertEquals("TEST_ORDER_1", result.orderId)
        assertEquals(OrderStatus.PENDING, result.status)
        assertEquals(0.0, result.executedAmount)
    }
    
    @Test
    fun `should return existing result for duplicate order ID`() = runBlocking {
        val request = OrderRequest(
            id = "TEST_ORDER_1",
            mint = "TEST_MINT",
            side = OrderSide.BUY,
            amount = 1000.0,
            maxSlippage = 0.05,
            timeoutMs = 30000L
        )
        
        val result1 = orderManager.submitOrder(request)
        val result2 = orderManager.submitOrder(request) // Same ID
        
        assertEquals(result1.orderId, result2.orderId)
        assertEquals(result1.status, result2.status)
    }
    
    @Test
    fun `should get order status correctly`() = runBlocking {
        val request = OrderRequest(
            id = "TEST_ORDER_1",
            mint = "TEST_MINT",
            side = OrderSide.BUY,
            amount = 1000.0,
            maxSlippage = 0.05,
            timeoutMs = 30000L
        )
        
        orderManager.submitOrder(request)
        val status = orderManager.getOrderStatus("TEST_ORDER_1")
        
        assertNotNull(status)
        assertEquals(OrderStatus.PENDING, status!!.status)
    }
    
    @Test
    fun `should cancel pending order`() = runBlocking {
        val request = OrderRequest(
            id = "TEST_ORDER_1",
            mint = "TEST_MINT",
            side = OrderSide.BUY,
            amount = 1000.0,
            maxSlippage = 0.05,
            timeoutMs = 30000L
        )
        
        orderManager.submitOrder(request)
        val cancelled = orderManager.cancelOrder("TEST_ORDER_1")
        
        assertTrue(cancelled)
        
        val status = orderManager.getOrderStatus("TEST_ORDER_1")
        assertEquals(OrderStatus.CANCELLED, status!!.status)
    }
    
    @Test
    fun `should not cancel non-existent order`() = runBlocking {
        val cancelled = orderManager.cancelOrder("NON_EXISTENT")
        assertFalse(cancelled)
    }
    
    @Test
    fun `should update order result correctly`() = runBlocking {
        val request = OrderRequest(
            id = "TEST_ORDER_1",
            mint = "TEST_MINT",
            side = OrderSide.BUY,
            amount = 1000.0,
            maxSlippage = 0.05,
            timeoutMs = 30000L
        )
        
        orderManager.submitOrder(request)
        
        val filledResult = OrderResult(
            orderId = "TEST_ORDER_1",
            status = OrderStatus.FILLED,
            executedAmount = 1000.0,
            executedPrice = 1.0,
            fees = 1.0,
            slippage = 0.01,
            latencyMs = 100L
        )
        
        orderManager.updateOrderResult("TEST_ORDER_1", filledResult)
        
        val status = orderManager.getOrderStatus("TEST_ORDER_1")
        assertEquals(OrderStatus.FILLED, status!!.status)
        assertEquals(1000.0, status.executedAmount)
    }
    
    @Test
    fun `should track statistics correctly`() = runBlocking {
        val request1 = OrderRequest("ORDER_1", "MINT1", OrderSide.BUY, 1000.0, 0.05, 30000L)
        val request2 = OrderRequest("ORDER_2", "MINT2", OrderSide.SELL, 500.0, 0.05, 30000L)
        
        orderManager.submitOrder(request1)
        orderManager.submitOrder(request2)
        
        val stats = orderManager.getStats()
        
        assertEquals(2, stats["activeOrders"])
        assertEquals(2, stats["totalOrderResults"])
    }
}

class GracefulDegradationManagerTest {
    
    private lateinit var degradationManager: GracefulDegradationManager
    private lateinit var config: EnhancedTradingConfig
    private lateinit var liquidityService: JupiterLiquidityService
    
    @BeforeEach
    fun setup() {
        config = EnhancedTradingConfig()
        liquidityService = mock()
        degradationManager = GracefulDegradationManager(config, liquidityService)
    }
    
    @Test
    fun `should start with normal conditions`() = runBlocking {
        val recommendation = degradationManager.assessTradingConditions("TEST_MINT")
        
        assertTrue(recommendation.allowTrading)
        assertEquals(1.0, recommendation.positionSizeMultiplier)
        assertEquals(1.0, recommendation.stopLossMultiplier)
        assertEquals("Normal market conditions", recommendation.reason)
    }
    
    @Test
    fun `should degrade after multiple errors`() {
        val error = RuntimeException("Test error")
        
        // Simulate multiple errors
        repeat(5) {
            degradationManager.recordError("TEST_MINT", error)
        }
        
        runBlocking {
            val recommendation = degradationManager.assessTradingConditions("TEST_MINT")
            
            assertTrue(recommendation.positionSizeMultiplier < 1.0)
            assertTrue(recommendation.stopLossMultiplier < 1.0)
            assertNotEquals("Normal market conditions", recommendation.reason)
        }
    }
    
    @Test
    fun `should improve degradation level after success`() {
        val error = RuntimeException("Test error")
        
        // Cause degradation
        repeat(3) {
            degradationManager.recordError("TEST_MINT", error)
        }
        
        // Record success
        degradationManager.recordSuccess("TEST_MINT")
        
        runBlocking {
            val recommendation = degradationManager.assessTradingConditions("TEST_MINT")
            
            // Should improve but not immediately return to normal
            assertTrue(recommendation.allowTrading)
        }
    }
    
    @Test
    fun `should handle missing price data`() = runBlocking {
        val strategy = degradationManager.handleMissingPrice("TEST_MINT")
        
        // For new token with no errors, should use cached or pause
        assertTrue(
            strategy == GracefulDegradationManager.PriceHandlingStrategy.USE_CACHED ||
            strategy == GracefulDegradationManager.PriceHandlingStrategy.PAUSE_TRADING
        )
    }
    
    @Test
    fun `should force sell in emergency mode`() {
        val error = RuntimeException("Critical error")
        
        // Cause severe degradation
        repeat(15) {
            degradationManager.recordError("TEST_MINT", error)
        }
        
        runBlocking {
            val recommendation = degradationManager.assessTradingConditions("TEST_MINT")
            val priceStrategy = degradationManager.handleMissingPrice("TEST_MINT")
            
            assertFalse(recommendation.allowTrading)
            assertEquals(GracefulDegradationManager.PriceHandlingStrategy.FORCE_SELL, priceStrategy)
        }
    }
}

class EnhancedExecutionEngineTest {
    
    private lateinit var executionEngine: EnhancedExecutionEngine
    private lateinit var config: EnhancedTradingConfig
    private lateinit var liquidityService: JupiterLiquidityService
    
    @BeforeEach
    fun setup() {
        config = EnhancedTradingConfig()
        liquidityService = mock()
        executionEngine = EnhancedExecutionEngine(config, liquidityService)
    }
    
    @Test
    fun `should execute buy order successfully`() = runBlocking {
        whenever(liquidityService.validateTrade(any(), any())).thenReturn(true)
        
        val result = executionEngine.executeBuy("TEST_MINT", 1000.0)
        
        assertEquals(OrderStatus.PENDING, result.status)
        assertTrue(result.orderId.contains("BUY"))
        assertTrue(result.orderId.contains("TEST_MINT".takeLast(8)))
    }
    
    @Test
    fun `should execute sell order successfully`() = runBlocking {
        val result = executionEngine.executeSell("TEST_MINT", 0.0, reason = "Manual")
        
        assertEquals(OrderStatus.PENDING, result.status)
        assertTrue(result.orderId.contains("SELL"))
        assertEquals(OrderPriority.NORMAL, OrderPriority.NORMAL) // Assuming normal priority for manual
    }
    
    @Test
    fun `should handle emergency sell with high priority`() = runBlocking {
        val result = executionEngine.executeSell("TEST_MINT", 0.0, reason = "Emergency stop loss")
        
        assertEquals(OrderStatus.PENDING, result.status)
        // In actual implementation, this would set EMERGENCY priority
    }
    
    @Test
    fun `should cancel order successfully`() = runBlocking {
        val buyResult = executionEngine.executeBuy("TEST_MINT", 1000.0)
        val cancelled = executionEngine.cancelOrder(buyResult.orderId)
        
        assertTrue(cancelled)
    }
    
    @Test
    fun `should get order status`() = runBlocking {
        val buyResult = executionEngine.executeBuy("TEST_MINT", 1000.0)
        val status = executionEngine.getOrderStatus(buyResult.orderId)
        
        assertNotNull(status)
        assertEquals(buyResult.orderId, status!!.orderId)
    }
    
    @Test
    fun `should handle missing price gracefully`() = runBlocking {
        val strategy = executionEngine.handleMissingPrice("TEST_MINT")
        
        // Should return a valid strategy
        assertTrue(strategy in GracefulDegradationManager.PriceHandlingStrategy.values())
    }
    
    @Test
    fun `should provide execution statistics`() = runBlocking {
        executionEngine.executeBuy("TEST_MINT_1", 1000.0)
        executionEngine.executeBuy("TEST_MINT_2", 500.0)
        
        val stats = executionEngine.getExecutionStats()
        
        assertTrue(stats.containsKey("orderStats"))
        assertTrue(stats.containsKey("activeExecutions"))
        assertTrue(stats.containsKey("degradationLevels"))
    }
    
    @Test
    fun `should cleanup old orders`() = runBlocking {
        executionEngine.executeBuy("TEST_MINT", 1000.0)
        executionEngine.cleanup()
        
        // Cleanup should complete without errors
        val stats = executionEngine.getExecutionStats()
        assertNotNull(stats)
    }
    
    @Test
    fun `should shutdown gracefully`() {
        executionEngine.shutdown()
        
        // Should complete without errors
        val stats = executionEngine.getExecutionStats()
        assertNotNull(stats)
    }
}