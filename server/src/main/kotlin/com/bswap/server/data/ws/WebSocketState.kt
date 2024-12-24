package com.bswap.server.data.ws

sealed class WebSocketState {
    data object Connecting : WebSocketState()
    data object Connected : WebSocketState()
    data object Disconnected : WebSocketState()
    data object Reconnecting : WebSocketState()
}