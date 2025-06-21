package com.bswap.server

import com.bswap.server.routes.fiatRoutes
import com.bswap.server.routes.swapRoutes
import com.bswap.server.routes.utilityRoutes
import com.bswap.server.service.FiatService
import com.bswap.server.service.PriceService
import com.bswap.server.service.SwapService
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val client = HttpClient(CIO) {
        install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    val swapService = SwapService(JupiterSwapService(client))
    val fiatService = FiatService()
    val priceService = PriceService(client)
    embeddedServer(Netty, port = SERVER_PORT) {
        module(swapService, fiatService, priceService)
    }.start(wait = true)
}

fun io.ktor.server.application.Application.module(
    swapService: SwapService,
    fiatService: FiatService,
    priceService: PriceService,
) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    routing {
        swapRoutes(swapService)
        fiatRoutes(fiatService)
        utilityRoutes("1.0.0", priceService)
    }
}
