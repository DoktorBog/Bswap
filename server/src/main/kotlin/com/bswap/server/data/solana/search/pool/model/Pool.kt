package com.bswap.server.data.solana.search.pool.model

import com.bswap.server.data.solana.search.logs.model.TokenInfo
import kotlinx.serialization.Serializable

@Serializable
data class Pool(
    val id: String,
    val name: String? = null,
    val price: Double? = null,
    val mintA: TokenInfo,
    val mintB: TokenInfo,
    val tvl: Double? = null
)

@Serializable
data class PoolData(
    val count: Int,
    val data: List<Pool>
)

@Serializable
data class PoolResponse(
    val success: Boolean,
    val data: PoolData
)