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

@Serializable
data class TokenBoost(
    val url: String,
    val chainId: String,
    val tokenAddress: String,
    val amount: Double? = null,
    val totalAmount: Double? = null,
    val icon: String? = null,
    val header: String? = null,
    val description: String? = null,
    val links: List<Link>? = null
)

@Serializable
data class Order(
    val type: String,
    val status: String,
    val paymentTimestamp: Long
)

@Serializable
data class TokenInfo(
    val address: String,
    val name: String,
    val symbol: String
)

@Serializable
data class Liquidity(
    val usd: Double,
    val base: Double,
    val quote: Double
)

@Serializable
data class Website(
    val url: String
)

@Serializable
data class Social(
    val platform: String,
    val handle: String
)

@Serializable
data class Info(
    val imageUrl: String? = null,
    val websites: List<Website>? = null,
    val socials: List<Social>? = null
)

@Serializable
data class Boosts(
    val active: Int
)

@Serializable
data class Pair(
    val chainId: String,
    val dexId: String,
    val url: String,
    val pairAddress: String,
    val labels: List<String>? = null,
    val baseToken: TokenInfo,
    val quoteToken: TokenInfo,
    val priceNative: String,
    val priceUsd: String,
    val liquidity: Liquidity,
    val fdv: Long? = null,
    val marketCap: Long? = null,
    val pairCreatedAt: Long? = null,
    val info: Info? = null,
    val boosts: Boosts? = null
)

@Serializable
data class PairsResponse(
    val schemaVersion: String,
    val pairs: List<Pair>? = null
)