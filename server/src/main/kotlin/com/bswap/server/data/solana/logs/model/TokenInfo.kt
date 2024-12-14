package com.bswap.server.data.solana.logs.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenInfo(
    val address: String,
    val symbol: String,
    val name: String,
    val decimals: Int
)