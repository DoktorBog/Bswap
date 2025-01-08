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
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object PumpFunService {

    private const val WS_URL = "wss://pumpportal.fun/api/data"
    private val eventFlow = MutableSharedFlow<TokenTradeResponse>()
    private val stateFlow = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected)
    private val subscribedTokens = mutableSetOf<String>()
    private val processedTokens = mutableSetOf<String>()
    private val json = Json { ignoreUnknownKeys = true }
    private var session: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        scope.launch {
            try {
                stateFlow.emit(WebSocketState.Connecting)
                client.webSocket(WS_URL) {
                    session = this
                    stateFlow.emit(WebSocketState.Connected)
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
                reconnect()
            }
        }
    }

    private suspend fun reconnect() {
        stateFlow.emit(WebSocketState.Reconnecting)
        delay(5000)
        connect()
    }

    fun disconnect() {
        scope.launch {
            session?.close()
            stateFlow.emit(WebSocketState.Disconnected)
        }
    }

    private suspend fun subscribeTokenTrade(token: String) {
        if (subscribedTokens.add(token)) {
            sendRequest("subscribeTokenTrade", listOf(token))
        }
    }

    private suspend fun unsubscribeTokenTrade(token: String) {
        if (subscribedTokens.remove(token)) {
            sendRequest("unsubscribeTokenTrade", listOf(token))
        }
    }

    private suspend fun sendRequest(method: String, keys: List<String>? = null) {
        val request = WebSocketRequestBuilder()
            .setMethod(method)
            .setKeys(keys)
            .build()
        session?.send(Frame.Text(json.encodeToString(request)))
    }

    private suspend fun handleMessage(message: String) {
        runCatching {
            val jsonObject = json.parseToJsonElement(message).jsonObject

            if (jsonObject.containsKey("signature")) {
                val trade = json.decodeFromString<TokenTradeResponse>(message)
                handleTokenTrade(trade)
            } else if (jsonObject.containsKey("token")) {
                val newToken = json.decodeFromString<NewTokenResponse>(message)
                handleNewToken(newToken)
            }
        }
    }

    private suspend fun handleNewToken(response: NewTokenResponse) {
        if (processedTokens.contains(response.token)) return
        subscribeTokenTrade(response.token)
    }

    private suspend fun handleTokenTrade(response: TokenTradeResponse) {
        if (processedTokens.add(response.mint)) {
            eventFlow.emit(response)
            unsubscribeTokenTrade(response.mint)
        }
    }

    fun observeEvents(): Flow<TokenTradeResponse> = eventFlow.asSharedFlow()
    fun observeState(): Flow<WebSocketState> = stateFlow.asStateFlow()
}