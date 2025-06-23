package com.bswap.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SolanaTx(
    val signature: String,
    val address: String,
    val amount: Double = 0.0,
    val incoming: Boolean = true,
)

@Serializable
data class HistoryPage(
    val transactions: List<SolanaTx>,
    val nextCursor: String? = null,
)
