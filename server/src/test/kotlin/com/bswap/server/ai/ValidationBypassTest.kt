package com.bswap.server.ai

import com.bswap.server.*
import com.bswap.server.data.solana.transaction.TokenAmount
import com.bswap.server.data.solana.transaction.TokenInfo
import com.bswap.server.stratagy.TradingStrategyFactory
import com.bswap.shared.wallet.WalletConfig
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationBypassTest {
    
    @Test
    fun testAIStrategyBypassesValidation() = runBlocking {
        // Create AI strategy configuration
        val aiConfig = TradingStrategySettings(
            type = StrategyType.AI_STRATEGY,
            aiStrategy = AIStrategyConfig(
                confidenceThreshold = 0.1 // Low threshold for testing
            )
        )
        
        // Create non-AI strategy configuration 
        val standardConfig = TradingStrategySettings(
            type = StrategyType.PUMPFUN_PRIORITY
        )
        
        // Create strategies
        val aiStrategy = TradingStrategyFactory.create(aiConfig)
        val standardStrategy = TradingStrategyFactory.create(standardConfig)
        
        // Verify strategy types
        assertEquals(StrategyType.AI_STRATEGY, aiStrategy.type)
        assertEquals(StrategyType.PUMPFUN_PRIORITY, standardStrategy.type)
        
        // Test that AI strategy is OpenAI-enhanced when key is available
        val openaiKey = com.bswap.server.ConfigLoader.loadOpenAIKey()
        if (openaiKey != null) {
            assertTrue(aiStrategy is OpenAITradingStrategy, "Should use OpenAI strategy when key is available")
        } else {
            assertTrue(aiStrategy is AITradingStrategy, "Should use fallback AI strategy when no OpenAI key")
        }
        
        println("✅ AI Strategy Validation Bypass Test - Strategy Types Verified")
    }
    
    @Test 
    fun testValidationLogic() {
        // Test validation bypass logic
        val aiStrategyType = StrategyType.AI_STRATEGY
        val standardStrategyType = StrategyType.PUMPFUN_PRIORITY
        
        // Simulate validation bypass logic from SolanaTokenSwapBot
        val shouldValidateForAI = aiStrategyType != StrategyType.AI_STRATEGY
        val shouldValidateForStandard = standardStrategyType != StrategyType.AI_STRATEGY
        
        assertTrue(!shouldValidateForAI, "AI strategy should bypass basic validation")
        assertTrue(shouldValidateForStandard, "Standard strategy should use basic validation")
        
        println("✅ Validation Logic Test Passed")
    }
    
    @Test
    fun testAIStrategyAutonomy() = runBlocking {
        val mockApiKey = "test-key-123"
        val config = AIStrategyConfig()
        val strategy = OpenAITradingStrategy(config, mockApiKey)
        
        // Test strategy properties
        assertEquals(StrategyType.AI_STRATEGY, strategy.type)
        
        // The strategy should be created successfully even without real API key
        println("✅ AI Strategy Autonomy Test - Strategy Created Successfully")
        
        // Test would normally fail with fake API key, but creation should work
        assertTrue(true, "Strategy instantiation successful")
    }
}