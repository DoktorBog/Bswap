package com.bswap.server.data.solana.pumpfun

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable

@Serializable
data class NewTokenResponse(
    val token: String,
    val name: String,
    val symbol: String,
    val uri: String
) {
    val eventType = "newToken"
}

@Serializable
data class TokenTradeResponse(
    val signature: String,
    val mint: String,
    val traderPublicKey: String,
    val txType: String,
    val initialBuy: Double,
    val solAmount: Double,
    val bondingCurveKey: String,
    val vTokensInBondingCurve: Double,
    val vSolInBondingCurve: Double,
    val marketCapSol: Double,
    val name: String,
    val symbol: String,
    val uri: String,
) {
    val eventType = "tokenTrade"
}