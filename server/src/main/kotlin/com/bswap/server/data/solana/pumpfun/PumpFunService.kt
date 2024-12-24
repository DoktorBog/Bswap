package com.bswap.server.data.solana.pumpfun

import com.bswap.server.client
import com.bswap.server.data.ws.WebSocketState
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PumpFunService {

    private const val WS_URL = "wss://pumpportal.fun/api/data"

    private val eventFlow = MutableSharedFlow<WebSocketResponse>()
    private val tokenStorage = mutableSetOf<String>()
    private val stateFlow = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected)
    private val json = Json { ignoreUnknownKeys = true }

    private val handlers = listOf(
        NewTokenHandler(tokenStorage, eventFlow),
        TradeEventHandler(eventFlow)
    )

    private var session: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        scope.launch {
            try {
                stateFlow.emit(WebSocketState.Connecting)
                client.webSocket(WS_URL) {
                    session = this
                    stateFlow.emit(WebSocketState.Connected)
                    println("Connected to WebSocket")

                    sendRequest("subscribeNewToken")

                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> handleMessage(frame.readText())
                            is Frame.Close -> reconnect()
                            else -> Unit
                        }
                    }
                }
            } catch (e: Exception) {
                println("WebSocket Error: ${e.message}")
                reconnect()
            }
        }
    }

    // Reconnect logic
    private suspend fun reconnect() {
        println("Reconnecting...")
        stateFlow.emit(WebSocketState.Reconnecting)
        delay(5000) // Retry after delay
        connect()
    }

    // Disconnect WebSocket
    fun disconnect() {
        scope.launch {
            session?.close()
            println("Disconnected from WebSocket")
            stateFlow.emit(WebSocketState.Disconnected)
        }
    }

    // Send Requests (Builder Pattern)
    private suspend fun sendRequest(method: String, keys: List<String>? = null) {
        val request = WebSocketRequestBuilder()
            .setMethod(method)
            .setKeys(keys)
            .build()
        session?.send(Frame.Text(json.encodeToString(request)))
        println("Sent Request: $method")
    }

    // Handle Incoming Messages
    private suspend fun handleMessage(message: String) {
        runCatching {
            val response = json.decodeFromString<WebSocketResponse>(message)
            handlers.forEach { handler ->
                if (handler.handle(response)) return
            }
        }
        println("Unhandled Event: $message")
    }

    // Observe Trades
    fun observeEvents(): Flow<WebSocketResponse> = eventFlow.asSharedFlow()

    // Observe State
    fun observeState(): Flow<WebSocketState> = stateFlow.asStateFlow()
}