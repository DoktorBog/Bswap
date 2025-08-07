package com.bswap.server.validation

data class ValidationConfig(
    val solanaRpcUrl: String = "https://api.mainnet-beta.solana.com",
    val metaplexApiUrl: String = "https://api.metaplex.solana.com",
    val heliusApiUrl: String = "https://api.helius.xyz",
    val heliusApiKey: String? = System.getenv("HELIUS_API_KEY"),
    val jupiterApiUrl: String = "https://quote-api.jup.ag",
    val raydiumApiUrl: String = "https://api.raydium.io",
    val pumpFunApiUrl: String = "https://client-api-2-74b1891ee9f9.herokuapp.com/api",
    val dexScreenerApiUrl: String = "https://api.dexscreener.com/latest",
    
    // Validation thresholds
    val minLiquidity: Double = 5000.0,
    val maxTopHolderPercentage: Double = 50.0,
    val minHolderCount: Int = 10,
    val maxSupply: Double = 1_000_000_000_000.0,
    val maxRiskScore: Double = 0.5,
    
    // API rate limits
    val rateLimitDelayMs: Long = 100,
    val maxRetries: Int = 3,
    val timeoutMs: Long = 10000,
    
    // Cache settings
    val cacheEnabled: Boolean = true,
    val cacheTtlMinutes: Long = 5
)