package com.bswap.server.validation

import com.bswap.server.RPC_URL

data class ValidationConfig(
    val solanaRpcUrl: String = RPC_URL,
    val heliusApiUrl: String = "https://api.helius.xyz",
    val heliusApiKey: String? = null,
    val raydiumApiUrl: String = "https://api.raydium.io",
    val dexScreenerApiUrl: String = "https://api.dexscreener.com/latest",
    val jupiterApiUrl: String = "https://quote-api.jup.ag",
    val cacheEnabled: Boolean = true,
    val cacheTtlMinutes: Long = 10,
    val rateLimitDelayMs: Long = 120,
    val maxRetries: Int = 3,
    val minLiquidity: Double = 5_000.0,
    val minHolderCount: Int = 10,
    val maxTopHolderPercentage: Double = 20.0,
    val maxSupply: Double = 10_000_000_000.0,
    val maxRiskScore: Double = 0.5
)
