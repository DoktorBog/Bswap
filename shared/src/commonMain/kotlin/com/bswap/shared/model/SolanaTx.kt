package com.bswap.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SolanaTx(
    val signature: String,
    val address: String,
    val amount: Double,
    val incoming: Boolean,
    val asset: Asset = Asset.SOL,
    val mint: String? = null,
    val decimals: Int? = null,
    val tokenName: String? = null,
    val tokenSymbol: String? = null,
    val tokenLogo: String? = null,
    val usdValue: Double? = null
) {
    enum class Asset { SOL, SPL }
}
