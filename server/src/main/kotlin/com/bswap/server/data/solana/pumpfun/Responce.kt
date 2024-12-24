package com.bswap.server.data.solana.pumpfun

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable

@Serializable
data class WebSocketResponse(
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
    val uri: String
) {
    val eventType = "newToken"
}

interface EventHandler {
    suspend fun handle(response: WebSocketResponse): Boolean
}

class NewTokenHandler(
    private val tokenStorage: MutableSet<String>,
    private val eventFlow: MutableSharedFlow<WebSocketResponse>
) : EventHandler {
    override suspend fun handle(response: WebSocketResponse): Boolean {
        if (response.eventType == "newToken" && tokenStorage.add(response.mint)) {
            eventFlow.emit(response)
            return true
        }
        return false
    }
}

class TradeEventHandler(
    private val eventFlow: MutableSharedFlow<WebSocketResponse>
) : EventHandler {
    override suspend fun handle(response: WebSocketResponse): Boolean {
        if (response.eventType == "trade") {
            eventFlow.emit(response)
            return true
        }
        return false
    }
}