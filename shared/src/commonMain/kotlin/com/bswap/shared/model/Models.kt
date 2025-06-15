package com.bswap.shared.model

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
data class BatchSwapRequest(
    val swaps: List<SwapRequest>,
)

@Serializable
data class SignedTx(
    val transaction: String,
)

@Serializable
data class SwapTx(
    val transaction: String,
    val lastValidBlockHeight: Long? = null,
    val prioritizationFeeLamports: Long? = null,
)

@Serializable
data class ApiError(
    val message: String,
)
