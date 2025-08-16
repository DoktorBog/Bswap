package com.bswap.server.stratagy

import com.bswap.server.config.*
import com.bswap.server.service.JupiterLiquidityService
import com.bswap.server.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*

class EnhancedShitcoinScalperStrategyTest {
    
    private lateinit var strategy: EnhancedShitcoinScalperStrategy
    private lateinit var config: ShitcoinScalperConfig
    private lateinit var enhancedConfig: EnhancedTradingConfig
    private lateinit var liquidityService: JupiterLiquidityService
    private lateinit var mockRuntime: TradingRuntime
    
    @BeforeEach
    fun setup() {
        config = ShitcoinScalperConfig(
            maxTokensHeld = 5,
            onlyNewTokens = true,
            profitTakePercent = 0.02,
            stopLossPercent = 0.08,
            maxHoldTimeMs = 60000L
        )
        enhancedConfig = EnhancedTradingConfig()
        liquidityService = mock()
        mockRuntime = mock()
        
        strategy = EnhancedShitcoinScalperStrategy(config, enhancedConfig, liquidityService)
        
        // Setup common mock behaviors
        whenever(mockRuntime.isNew(any())).thenReturn(true)
        whenever(mockRuntime.getTokenUsdPrice(any())).thenReturn(0.001)
        whenever(mockRuntime.config).thenReturn(SolanaSwapBotConfig())
        whenever(mockRuntime.allTokens()).thenReturn(emptyList())
    }
    
    @Test
    fun `should discover new token and attempt buy`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        
        // Mock liquidity validation to pass
        val liquidityAnalysis = JupiterLiquidityService.LiquidityAnalysis(
            isLiquid = true,
            priceImpact = 2.0,
            riskScore = 0.3,
            routes = listOf(),
            warnings = emptyList()
        )
        whenever(liquidityService.analyzeLiquidity(any(), any())).thenReturn(liquidityAnalysis)
        whenever(mockRuntime.buy(any())).thenReturn(true)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        verify(mockRuntime).buy("TEST_MINT")
    }
    
    @Test
    fun `should skip discovery for non-new tokens`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        whenever(mockRuntime.isNew(any())).thenReturn(false)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        verify(mockRuntime, never()).buy(any())
    }
    
    @Test
    fun `should skip discovery when max tokens held`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        
        // Simulate max tokens already held by creating positions
        repeat(config.maxTokensHeld) { i ->
            val dummyMeta = TokenMeta("DUMMY_$i", TokenSource.PUMPFUN)
            whenever(liquidityService.analyzeLiquidity(eq("DUMMY_$i"), any())).thenReturn(
                JupiterLiquidityService.LiquidityAnalysis(true, 2.0, 0.3, emptyList(), emptyList())
            )
            whenever(mockRuntime.buy(eq("DUMMY_$i"))).thenReturn(true)
            strategy.onDiscovered(dummyMeta, mockRuntime)
        }
        
        // Now try to discover another token
        strategy.onDiscovered(meta, mockRuntime)
        
        verify(mockRuntime, never()).buy("TEST_MINT")
    }
    
    @Test
    fun `should skip discovery for illiquid tokens`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        
        // Mock liquidity validation to fail
        val liquidityAnalysis = JupiterLiquidityService.LiquidityAnalysis(
            isLiquid = false,
            priceImpact = 15.0,
            riskScore = 0.8,
            routes = emptyList(),
            warnings = listOf("High price impact", "Low liquidity")
        )
        whenever(liquidityService.analyzeLiquidity(any(), any())).thenReturn(liquidityAnalysis)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        verify(mockRuntime, never()).buy(any())
    }
    
    @Test
    fun `should handle price calculation errors gracefully`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        
        whenever(mockRuntime.getTokenUsdPrice(any())).thenThrow(RuntimeException("Price service error"))
        whenever(liquidityService.analyzeLiquidity(any(), any())).thenReturn(
            JupiterLiquidityService.LiquidityAnalysis(true, 2.0, 0.3, emptyList(), emptyList())
        )
        
        // Should not throw exception
        assertDoesNotThrow {
            runBlocking { strategy.onDiscovered(meta, mockRuntime) }
        }
    }
    
    @Test
    fun `should calculate entry confidence correctly`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        
        // Mock good liquidity
        val liquidityAnalysis = JupiterLiquidityService.LiquidityAnalysis(
            isLiquid = true,
            priceImpact = 1.0,
            riskScore = 0.1, // Very low risk
            routes = emptyList(),
            warnings = emptyList()
        )
        whenever(liquidityService.analyzeLiquidity(any(), any())).thenReturn(liquidityAnalysis)
        whenever(mockRuntime.buy(any())).thenReturn(true)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        // Should attempt buy due to high confidence
        verify(mockRuntime).buy("TEST_MINT")
    }
    
    @Test
    fun `should handle tick processing correctly`() = runBlocking {
        // First create a position
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        whenever(liquidityService.analyzeLiquidity(any(), any())).thenReturn(
            JupiterLiquidityService.LiquidityAnalysis(true, 2.0, 0.3, emptyList(), emptyList())
        )
        whenever(mockRuntime.buy(any())).thenReturn(true)
        strategy.onDiscovered(meta, mockRuntime)
        
        // Mock wallet tokens for tick processing
        val mockTokenInfo = mock<com.bswap.server.data.solana.transaction.TokenInfo>()
        whenever(mockTokenInfo.address).thenReturn("TEST_MINT")
        whenever(mockTokenInfo.tokenAmount).thenReturn(
            mock<com.bswap.server.data.solana.transaction.TokenAmount>().apply {
                whenever(uiAmount).thenReturn(1000.0)
            }
        )
        
        val mockTokenStatus = mock<TokenStatus>()
        whenever(mockTokenStatus.state).thenReturn(TokenState.Swapped)
        
        whenever(mockRuntime.allTokens()).thenReturn(listOf(mockTokenInfo))
        whenever(mockRuntime.status(any())).thenReturn(mockTokenStatus)
        whenever(mockRuntime.getTokenUsdPrice(any())).thenReturn(0.0015) // 50% profit
        
        // Should process without errors
        assertDoesNotThrow {
            runBlocking { strategy.onTick(mockRuntime) }
        }
    }
    
    @Test
    fun `should handle sell decisions correctly`() = runBlocking {
        // Create position first
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        whenever(liquidityService.analyzeLiquidity(any(), any())).thenReturn(
            JupiterLiquidityService.LiquidityAnalysis(true, 2.0, 0.3, emptyList(), emptyList())
        )
        whenever(mockRuntime.buy(any())).thenReturn(true)
        whenever(mockRuntime.sell(any())).thenReturn(true)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        // Mock high profit scenario
        val mockTokenInfo = mock<com.bswap.server.data.solana.transaction.TokenInfo>()
        whenever(mockTokenInfo.address).thenReturn("TEST_MINT")
        whenever(mockTokenInfo.tokenAmount).thenReturn(
            mock<com.bswap.server.data.solana.transaction.TokenAmount>().apply {
                whenever(uiAmount).thenReturn(1000.0)
            }
        )
        
        val mockTokenStatus = mock<TokenStatus>()
        whenever(mockTokenStatus.state).thenReturn(TokenState.Swapped)
        
        whenever(mockRuntime.allTokens()).thenReturn(listOf(mockTokenInfo))
        whenever(mockRuntime.status(any())).thenReturn(mockTokenStatus)
        whenever(mockRuntime.getTokenUsdPrice(any())).thenReturn(0.005) // Huge profit
        
        strategy.onTick(mockRuntime)
        
        // Should sell due to profit target
        verify(mockRuntime).sell("TEST_MINT")
    }
}

class EnhancedRSIStrategyTest {
    
    private lateinit var strategy: EnhancedRSIStrategy
    private lateinit var config: RsiBasedConfig
    private lateinit var enhancedConfig: EnhancedTradingConfig
    private lateinit var mockRuntime: TradingRuntime
    
    @BeforeEach
    fun setup() {
        config = RsiBasedConfig(
            period = 14,
            oversoldThreshold = 30.0,
            overboughtThreshold = 70.0,
            minHoldMs = 5000L
        )
        enhancedConfig = EnhancedTradingConfig()
        mockRuntime = mock()
        
        strategy = EnhancedRSIStrategy(config, enhancedConfig)
        
        // Setup common mock behaviors
        whenever(mockRuntime.isNew(any())).thenReturn(true)
        whenever(mockRuntime.getTokenUsdPrice(any())).thenReturn(0.001)
        whenever(mockRuntime.config).thenReturn(SolanaSwapBotConfig())
        whenever(mockRuntime.allTokens()).thenReturn(emptyList())
        whenever(mockRuntime.getPriceHistory).thenReturn { mint ->
            // Return some price history for RSI calculation
            listOf(1.0, 1.01, 0.99, 1.02, 0.98, 1.03, 0.97, 1.04, 0.96, 1.05, 
                   0.95, 1.06, 0.94, 1.07, 0.93, 1.08) // 16 prices for 14-period RSI
        }
    }
    
    @Test
    fun `should discover new token and load price history`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        // Should call getPriceHistory
        verify(mockRuntime.getPriceHistory!!)("TEST_MINT")
    }
    
    @Test
    fun `should skip discovery for non-new tokens`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        whenever(mockRuntime.isNew(any())).thenReturn(false)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        verify(mockRuntime, never()).buy(any())
    }
    
    @Test
    fun `should handle insufficient price history gracefully`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        
        // Mock insufficient price history
        whenever(mockRuntime.getPriceHistory).thenReturn { mint ->
            listOf(1.0, 1.01) // Only 2 prices, insufficient for 14-period RSI
        }
        whenever(mockRuntime.buy(any())).thenReturn(true)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        // Should still attempt buy using fallback logic
        verify(mockRuntime).buy("TEST_MINT")
    }
    
    @Test
    fun `should calculate RSI and make buy decision`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        
        // Mock price history that should generate oversold RSI
        whenever(mockRuntime.getPriceHistory).thenReturn { mint ->
            // Declining prices to create oversold condition
            listOf(1.0, 0.98, 0.96, 0.94, 0.92, 0.90, 0.88, 0.86, 0.84, 0.82,
                   0.80, 0.78, 0.76, 0.74, 0.72, 0.70)
        }
        whenever(mockRuntime.buy(any())).thenReturn(true)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        verify(mockRuntime).buy("TEST_MINT")
    }
    
    @Test
    fun `should handle tick processing for positions`() = runBlocking {
        // First create a position
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        whenever(mockRuntime.buy(any())).thenReturn(true)
        strategy.onDiscovered(meta, mockRuntime)
        
        // Mock wallet tokens for tick processing
        val mockTokenInfo = mock<com.bswap.server.data.solana.transaction.TokenInfo>()
        whenever(mockTokenInfo.address).thenReturn("TEST_MINT")
        whenever(mockTokenInfo.tokenAmount).thenReturn(
            mock<com.bswap.server.data.solana.transaction.TokenAmount>().apply {
                whenever(uiAmount).thenReturn(1000.0)
            }
        )
        
        val mockTokenStatus = mock<TokenStatus>()
        whenever(mockTokenStatus.state).thenReturn(TokenState.Swapped)
        
        whenever(mockRuntime.allTokens()).thenReturn(listOf(mockTokenInfo))
        whenever(mockRuntime.status(any())).thenReturn(mockTokenStatus)
        whenever(mockRuntime.getTokenUsdPrice(any())).thenReturn(0.0015)
        
        // Should process without errors
        assertDoesNotThrow {
            runBlocking { strategy.onTick(mockRuntime) }
        }
    }
    
    @Test
    fun `should make sell decision based on RSI overbought`() = runBlocking {
        // Create position first
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        whenever(mockRuntime.buy(any())).thenReturn(true)
        whenever(mockRuntime.sell(any())).thenReturn(true)
        
        strategy.onDiscovered(meta, mockRuntime)
        
        // Mock position and high RSI scenario
        val mockTokenInfo = mock<com.bswap.server.data.solana.transaction.TokenInfo>()
        whenever(mockTokenInfo.address).thenReturn("TEST_MINT")
        whenever(mockTokenInfo.tokenAmount).thenReturn(
            mock<com.bswap.server.data.solana.transaction.TokenAmount>().apply {
                whenever(uiAmount).thenReturn(1000.0)
            }
        )
        
        val mockTokenStatus = mock<TokenStatus>()
        whenever(mockTokenStatus.state).thenReturn(TokenState.Swapped)
        
        whenever(mockRuntime.allTokens()).thenReturn(listOf(mockTokenInfo))
        whenever(mockRuntime.status(any())).thenReturn(mockTokenStatus)
        
        // Simulate price increases that would create overbought RSI
        var currentPrice = 0.001
        repeat(20) {
            currentPrice *= 1.02 // 2% increase each tick
            whenever(mockRuntime.getTokenUsdPrice(any())).thenReturn(currentPrice)
            strategy.onTick(mockRuntime)
        }
        
        // Should eventually sell due to overbought conditions
        verify(mockRuntime, atLeastOnce()).sell("TEST_MINT")
    }
    
    @Test
    fun `should handle errors in tick processing gracefully`() = runBlocking {
        val meta = TokenMeta("TEST_MINT", TokenSource.PUMPFUN)
        whenever(mockRuntime.buy(any())).thenReturn(true)
        strategy.onDiscovered(meta, mockRuntime)
        
        // Mock error in price calculation
        val mockTokenInfo = mock<com.bswap.server.data.solana.transaction.TokenInfo>()
        whenever(mockTokenInfo.address).thenReturn("TEST_MINT")
        whenever(mockTokenInfo.tokenAmount).thenReturn(
            mock<com.bswap.server.data.solana.transaction.TokenAmount>().apply {
                whenever(uiAmount).thenReturn(1000.0)
            }
        )
        
        val mockTokenStatus = mock<TokenStatus>()
        whenever(mockTokenStatus.state).thenReturn(TokenState.Swapped)
        
        whenever(mockRuntime.allTokens()).thenReturn(listOf(mockTokenInfo))
        whenever(mockRuntime.status(any())).thenReturn(mockTokenStatus)
        whenever(mockRuntime.getTokenUsdPrice(any())).thenThrow(RuntimeException("Price error"))
        
        // Should not throw exception
        assertDoesNotThrow {
            runBlocking { strategy.onTick(mockRuntime) }
        }
    }
}