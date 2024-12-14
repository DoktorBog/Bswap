package com.bswap.server.data.dexscreener.models

import kotlinx.serialization.Serializable

@Serializable
data class TokenProfile(
    val url: String,
    val chainId: String,
    val tokenAddress: String,
    val icon: String,
    val header: String? = null,
    val openGraph: String? = null,
    val description: String? = null,
    val links: List<Link>? = null
)

@Serializable
data class Link(
    val label: String? = null,
    val type: String? = null,
    val url: String
)