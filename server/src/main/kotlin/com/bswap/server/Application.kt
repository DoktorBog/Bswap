package com.bswap.server

import com.bswap.server.data.SERVER_PORT
import com.bswap.server.data.dexscreener.DexScreenerClientImpl
import com.bswap.server.data.dexscreener.DexScreenerRepository
import com.bswap.server.data.solana.jupiter.JupiterSwapService
import com.bswap.server.data.solana.transaction.executeAndConfirmTransaction
import com.bswap.server.routes.apiRoute
import com.bswap.server.routes.startRoute
import com.bswap.server.routes.tokensRoute
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.networking.NetworkDriver
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
import java.math.BigDecimal

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val client = createClient()
    val rpc = createRPC(client)

    val jupiterSwapService = JupiterSwapService(client)
    val dexScreenerRepository = DexScreenerRepository(DexScreenerClientImpl(client))

    GlobalScope.launch {
        dexScreenerRepository.startAutoRefreshAll()
        executeAndConfirmTransaction(jupiterSwapService.getQuoteAndPerformSwap().swapTransaction)
        executeAndConfirmTransaction(rpc = rpc, amount = BigDecimal.valueOf(0.001))
    }


    embeddedServer(Netty, port = SERVER_PORT) {
        routing {
            startRoute()
            tokensRoute(dexScreenerRepository.tokenProfilesFlow)
            apiRoute(dexScreenerRepository.tokenProfilesFlow)
        }
    }.start(wait = true)
}


private fun createClient() = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

private fun createRPC(client: HttpClient) =
    RPC(RPC_URL, NetworkDriver(client))

const val RPC_URL = "https://api.mainnet-beta.solana.com"
