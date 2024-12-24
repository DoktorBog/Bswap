package com.bswap.server.data.solana.pumpfun

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketRequest(
    val method: String,
    val keys: List<String>? = null
)

class WebSocketRequestBuilder {
    private var method: String = ""
    private var keys: List<String>? = null

    fun setMethod(method: String) = apply { this.method = method }
    fun setKeys(keys: List<String>?) = apply { this.keys = keys }
    fun build(): WebSocketRequest = WebSocketRequest(method, keys)
}