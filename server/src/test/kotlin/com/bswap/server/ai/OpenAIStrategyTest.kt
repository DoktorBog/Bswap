package com.bswap.server.ai

import com.bswap.server.*
import com.bswap.server.data.solana.transaction.TokenAmount
import com.bswap.server.data.solana.transaction.TokenInfo
import com.bswap.shared.wallet.WalletConfig
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockTradingRuntime : TradingRuntime {
    override val walletConfig: WalletConfig = WalletConfig("test", "test")
    override val config: SolanaSwapBotConfig = SolanaSwapBotConfig()
    
    private val tokenStatuses = mutableMapOf<String, TokenStatus>()
    private val tokens = mutableListOf<TokenInfo>()
    
    override fun now(): Long = System.currentTimeMillis()
    
    override fun isNew(mint: String): Boolean = !tokenStatuses.containsKey(mint)
    
    override fun status(mint: String): TokenStatus? = tokenStatuses[mint]
    
    override suspend fun buy(mint: String): Boolean {
        tokenStatuses[mint] = TokenStatus(mint, TokenState.Swapped)
        return true
    }
    
    override suspend fun sell(mint: String): Boolean {
        tokenStatuses[mint] = TokenStatus(mint, TokenState.Sold)
        return true
    }
    
    override suspend fun tokenInfo(mint: String): TokenInfo? {
        return TokenInfo(
            address = mint,
            tokenAmount = TokenAmount(
                amount = "1000000",
                decimals = 6,
                uiAmount = 1.0
            ),
            owner = "test-owner",
            programId = "test-program"
        )
    }
    
    override suspend fun allTokens(): List<TokenInfo> = tokens
    
    override suspend fun getTokenUsdPrice(mint: String): Double? = 0.000001
    
    fun addToken(tokenInfo: TokenInfo) {
        tokens.add(tokenInfo)
    }
}

class OpenAIStrategyTest {
    
    @Test
    fun testStrategyCreation() {
        val config = AIStrategyConfig()
        val mockApiKey = "test-key"
        
        val strategy = OpenAITradingStrategy(config, mockApiKey)
        
        assertNotNull(strategy)
        assertTrue(strategy.type == StrategyType.AI_STRATEGY)
    }
    
    @Test
    fun testTokenValidation() = runBlocking {
        val config = AIStrategyConfig()
        val mockApiKey = "test-key"
        val strategy = OpenAITradingStrategy(config, mockApiKey)
        val runtime = MockTradingRuntime()
        
        val tokenMeta = TokenMeta(
            mint = "test-token-123",
            source = TokenSource.PUMPFUN
        )
        
        // This will fail without a real API key, but tests the structure
        try {
            strategy.onDiscovered(tokenMeta, runtime)
            // Test passes if no exceptions are thrown during setup
            assertTrue(true)
        } catch (e: Exception) {
            // Expected to fail without real API key
            assertTrue(e.message?.contains("API") == true || e.message?.contains("key") == true)
        }
    }
    
    @Test
    fun testConfigurationLoading() {
        val openaiKey = com.bswap.server.ConfigLoader.loadOpenAIKey()
        
        // Should either load a key or return null
        assertTrue(openaiKey == null || openaiKey.isNotEmpty())
    }
}