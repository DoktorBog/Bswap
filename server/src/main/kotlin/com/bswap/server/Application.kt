package com.bswap.server

import com.bswap.server.data.SERVER_PORT
import com.bswap.server.data.solana.pool.PoolMonitorService
import com.bswap.server.data.dexscreener.TokenInfoService
import com.bswap.server.routes.apiRoute
import com.bswap.server.routes.newPullsRoute
import com.bswap.server.routes.startRoute
import com.bswap.server.routes.tokensRoute
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    val poolMonitorService = PoolMonitorService(client)
    val tokenInfoService = TokenInfoService(client)

    GlobalScope.launch { poolMonitorService.monitorPools() }
    GlobalScope.launch { tokenInfoService.monitorTokenProfiles() }

    embeddedServer(Netty, port = SERVER_PORT) {
        routing {
            startRoute()
            newPullsRoute(poolMonitorService)
            tokensRoute(tokenInfoService)
            apiRoute(tokenInfoService)
        }
    }.start(wait = true)
}