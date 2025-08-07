package com.bswap.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryPage(
    val transactions: List<SolanaTx>,
    val nextCursor: String? = null,
)
