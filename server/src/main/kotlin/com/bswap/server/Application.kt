package com.bswap.server

import com.bswap.server.data.dexscreener.DexScreenerClientImpl
import com.bswap.server.data.dexscreener.DexScreenerRepository
import com.bswap.server.data.solana.jito.JitoBundlerService
import com.bswap.server.data.solana.pumpfun.PumpFunService
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.solana.transaction.DefaultTransactionExecutor
import com.bswap.server.routes.apiRoute
import com.bswap.server.routes.startRoute
import com.bswap.server.routes.tokensRoute
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.networking.NetworkDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

fun main() {
    PumpFunService.connect()
    val bot = SolanaTokenSwapBot()
    bot.runDexScreenerSwap(tokenProfiles = false, tokenBoostedProfiles = false)
    bot.observePumpFun(PumpFunService.observeEvents())
    embeddedServer(Netty, port = SERVER_PORT) {
        routing {
            startRoute()
            tokensRoute(dexScreenerRepository.tokenProfilesFlow)
            apiRoute(dexScreenerRepository.tokenProfilesFlow)
        }
    }.start(wait = true)
}

fun SolanaTokenSwapBot.runDexScreenerSwap(
    tokenProfiles: Boolean = true,
    tokenBoostedProfiles: Boolean = true,
) {
    GlobalScope.launch {
        delay(3_000)
        if (tokenProfiles) {
            observeProfiles(dexScreenerRepository.tokenProfilesFlow)
        }
        if (tokenBoostedProfiles) {
            observeBoosted(dexScreenerRepository.latestBoostedTokensFlow)
            observeBoosted(dexScreenerRepository.topBoostedTokensFlow)
        }
        dexScreenerRepository.apply {
            if (tokenProfiles) {
                startAutoRefreshTokenProfiles()
            }
            if (tokenBoostedProfiles) {
                startAutoRefreshLatestBoostedTokens()
                startAutoRefreshTopBoostedTokens()
            }
        }
    }
}

val client by lazy {
    HttpClient(CIO) {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}

val rpc = createRPC(client)

val dexScreenerRepository = DexScreenerRepository(DexScreenerClientImpl(client))

private fun createRPC(client: HttpClient) =
    RPC(RPC_URL, NetworkDriver(client))