package com.bswap.server.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenInfo(
    val mint: String,
    val symbol: String? = null,
    val name: String? = null,
    val decimals: Int? = null,
    val amount: String? = null,
    val logoUri: String? = null,
)

@Serializable
data class WalletInfo(
    val address: String,
    val lamports: Long,
    val tokens: List<TokenInfo>,
)

@Serializable
data class SwapRequest(
    val owner: String,
    val inputMint: String,
    val outputMint: String,
    val amount: String,
)

@Serializable
data class SwapQuote(
    val inputMint: String,
    val outputMint: String,
    val inAmount: String,
    val outAmount: String,
    val priceImpactPct: String? = null,
)

@Serializable
data class SwapTx(
    val swapId: String,
    val transaction: String,
    val lastValidBlockHeight: Long? = null,
    val prioritizationFeeLamports: Long? = null,
)

@Serializable
data class ApiError(val message: String)

@Serializable
data class FiatSessionRequest(
    val provider: String,
    val amount: Double,
    val currency: String,
    val address: String,
)

@Serializable
data class FiatSessionResponse(val url: String)
