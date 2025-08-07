package com.bswap.server.validation

import kotlinx.serialization.Serializable

@Serializable
data class TokenMetadata(
    val mint: String,
    val name: String? = null,
    val symbol: String? = null,
    val uri: String? = null,
    val isMutable: Boolean = true,
    val updateAuthority: String? = null
)

@Serializable
data class TokenAccountInfo(
    val mint: String,
    val decimals: Int,
    val supply: String,
    val isInitialized: Boolean,
    val freezeAuthority: String? = null,
    val mintAuthority: String? = null
)

@Serializable
data class LiquidityPool(
    val address: String,
    val baseMint: String,
    val quoteMint: String,
    val baseReserve: String,
    val quoteReserve: String,
    val lpSupply: String,
    val liquidityUsd: Double? = null
)

@Serializable
data class TokenValidationResult(
    val isValid: Boolean,
    val mint: String,
    val reasons: List<String> = emptyList(),
    val riskScore: Double = 0.0,
    val metadata: TokenMetadata? = null,
    val accountInfo: TokenAccountInfo? = null,
    val liquidityPools: List<LiquidityPool> = emptyList(),
    val holderCount: Int = 0,
    val topHolderPercentage: Double = 0.0
)

enum class ValidationRule {
    FROZEN_AUTHORITY_CHECK,
    MINT_AUTHORITY_CHECK,
    LIQUIDITY_CHECK,
    HOLDER_DISTRIBUTION_CHECK,
    METADATA_CHECK,
    SUPPLY_CHECK,
    RUG_PATTERN_CHECK,
    PUMP_FUN_VALIDATION,
    HONEYPOT_CHECK
}
