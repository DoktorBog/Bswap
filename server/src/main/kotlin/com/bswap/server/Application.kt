package com.bswap.server

import com.bswap.server.data.SERVER_PORT
import com.bswap.server.data.dexscreener.DexScreenerClientImpl
import com.bswap.server.data.dexscreener.DexScreenerRepository
import com.bswap.server.routes.apiRoute
import com.bswap.server.routes.startRoute
import com.bswap.server.routes.tokensRoute
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    val dexScreenerRepository = DexScreenerRepository(DexScreenerClientImpl(client))

    GlobalScope.launch { dexScreenerRepository.startAutoRefreshAll() }

    embeddedServer(Netty, port = SERVER_PORT) {
        routing {
            startRoute()
            tokensRoute(dexScreenerRepository.tokenProfilesFlow)
            apiRoute(dexScreenerRepository.tokenProfilesFlow)
        }
    }.start(wait = true)
}