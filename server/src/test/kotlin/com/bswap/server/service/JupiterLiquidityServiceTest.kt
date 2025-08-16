package com.bswap.server.service

import com.bswap.server.config.EnhancedTradingConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*
import java.net.http.HttpClient
import java.net.http.HttpResponse

class JupiterLiquidityServiceTest {
    
    private lateinit var httpClient: HttpClient
    private lateinit var httpResponse: HttpResponse<String>
    private lateinit var jupiterService: JupiterLiquidityService
    private lateinit var config: EnhancedTradingConfig
    
    @BeforeEach
    fun setup() {
        httpClient = mock()
        httpResponse = mock()
        config = EnhancedTradingConfig()
        jupiterService = JupiterLiquidityService(config, httpClient)
    }
    
    @Test
    fun `should analyze liquidity successfully`() = runBlocking {
        val mockResponse = """
        {
            "data": [{
                "inAmount": "1000000",
                "outAmount": "950000",
                "priceImpactPct": 2.5,
                "routePlan": [{
                    "swapInfo": {
                        "ammKey": "test_amm",
                        "label": "Raydium",
                        "inputMint": "So11111111111111111111111111111111111111112",
                        "outputMint": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                        "inAmount": "1000000",
                        "outAmount": "950000",
                        "feeAmount": "5000"
                    }
                }]
            }]
        }
        """.trimIndent()
        
        whenever(httpResponse.statusCode()).thenReturn(200)
        whenever(httpResponse.body()).thenReturn(mockResponse)
        whenever(httpClient.send(any(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        val analysis = jupiterService.analyzeLiquidity("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 1000.0)
        
        assertNotNull(analysis)
        assertEquals(2.5, analysis!!.priceImpact, 0.1)
        assertTrue(analysis.isLiquid)
        assertEquals(1, analysis.routes.size)
        assertTrue(analysis.riskScore < 0.5) // Low price impact = low risk
    }
    
    @Test
    fun `should handle high price impact`() = runBlocking {
        val mockResponse = """
        {
            "data": [{
                "inAmount": "1000000",
                "outAmount": "800000",
                "priceImpactPct": 15.0,
                "routePlan": [{
                    "swapInfo": {
                        "ammKey": "test_amm",
                        "label": "Raydium",
                        "inputMint": "So11111111111111111111111111111111111111112",
                        "outputMint": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                        "inAmount": "1000000",
                        "outAmount": "800000",
                        "feeAmount": "5000"
                    }
                }]
            }]
        }
        """.trimIndent()
        
        whenever(httpResponse.statusCode()).thenReturn(200)
        whenever(httpResponse.body()).thenReturn(mockResponse)
        whenever(httpClient.send(any(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        val analysis = jupiterService.analyzeLiquidity("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 1000.0)
        
        assertNotNull(analysis)
        assertEquals(15.0, analysis!!.priceImpact, 0.1)
        assertFalse(analysis.isLiquid) // High price impact = illiquid
        assertTrue(analysis.riskScore > 0.7) // High price impact = high risk
        assertTrue(analysis.warnings.isNotEmpty())
    }
    
    @Test
    fun `should handle API errors gracefully`() = runBlocking {
        whenever(httpResponse.statusCode()).thenReturn(500)
        whenever(httpResponse.body()).thenReturn("Internal Server Error")
        whenever(httpClient.send(any(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        val analysis = jupiterService.analyzeLiquidity("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 1000.0)
        
        assertNull(analysis)
    }
    
    @Test
    fun `should validate trade correctly`() = runBlocking {
        val mockResponse = """
        {
            "data": [{
                "priceImpactPct": 3.0
            }]
        }
        """.trimIndent()
        
        whenever(httpResponse.statusCode()).thenReturn(200)
        whenever(httpResponse.body()).thenReturn(mockResponse)
        whenever(httpClient.send(any(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        val isValid = jupiterService.validateTrade("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 1000.0)
        
        assertTrue(isValid) // 3% impact should be acceptable
    }
    
    @Test
    fun `should cache analysis results`() = runBlocking {
        val mockResponse = """
        {
            "data": [{
                "priceImpactPct": 2.0
            }]
        }
        """.trimIndent()
        
        whenever(httpResponse.statusCode()).thenReturn(200)
        whenever(httpResponse.body()).thenReturn(mockResponse)
        whenever(httpClient.send(any(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        // First call
        val analysis1 = jupiterService.analyzeLiquidity("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 1000.0)
        
        // Second call (should use cache)
        val analysis2 = jupiterService.analyzeLiquidity("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 1000.0)
        
        // Should only make one HTTP call due to caching
        verify(httpClient, times(1)).send(any(), any<HttpResponse.BodyHandler<String>>())
        
        assertEquals(analysis1!!.priceImpact, analysis2!!.priceImpact)
    }
    
    @Test
    fun `should handle empty route response`() = runBlocking {
        val mockResponse = """
        {
            "data": []
        }
        """.trimIndent()
        
        whenever(httpResponse.statusCode()).thenReturn(200)
        whenever(httpResponse.body()).thenReturn(mockResponse)
        whenever(httpClient.send(any(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        val analysis = jupiterService.analyzeLiquidity("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 1000.0)
        
        assertNull(analysis)
    }
    
    @Test
    fun `should calculate risk score correctly`() = runBlocking {
        // Test different price impact scenarios
        val testCases = listOf(
            1.0 to 0.2,  // Low impact -> low risk
            5.0 to 0.5,  // Medium impact -> medium risk
            15.0 to 0.8, // High impact -> high risk
            25.0 to 1.0  // Very high impact -> max risk
        )
        
        testCases.forEach { (priceImpact, expectedRisk) ->
            val mockResponse = """
            {
                "data": [{
                    "priceImpactPct": $priceImpact
                }]
            }
            """.trimIndent()
            
            whenever(httpResponse.body()).thenReturn(mockResponse)
            val analysis = jupiterService.analyzeLiquidity("TEST_MINT", 1000.0)
            
            assertNotNull(analysis)
            assertEquals(expectedRisk, analysis!!.riskScore, 0.1)
        }
    }
}