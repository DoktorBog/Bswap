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
    val jupiterSwapService = JupiterSwapService(client)
    val executor = DefaultTransactionExecutor(rpc)
    val jitoService = JitoBundlerService(
        client = client,
        jitoFeeLamports = 1000,
        tipAccounts = listOf(
            "Cw8CFyM9FkoMi7K7Crf6HNQqf4uEMzpKw6QNghXLvLkY",
            "DttWaMuVvTiduZRnguLF7jNxTgiMBZ1hyAumKUiL2KRL",
            "96gYZGLnJYVFmbjzopPSU6QiEV5fGqZNyN9nmNhvrZU5",
            "3AVi9Tg9Uo68tJfuvoKvqKNWKkC5wPdSSdeBnizKZ6jT",
            "HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe",
            "ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49",
            "ADuUkR4vqLUMWXxW9gh6D6L8pMSawimctcNZ5pGwDcEt",
            "DfXygSm4jCyNCybVYYK6DwvWqjKee8pbDmJGcLWNDXjh"
        )
    )
    val config = SolanaSwapBotConfig(
        rpc = rpc,
        jupiterSwapService = jupiterSwapService,
        useJito = false
    )
    PumpFunService.connect()
    val bot = SolanaTokenSwapBot(config, executor, jitoService)
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
    tokenBoostedProfiles: Boolean = false,
) {
    GlobalScope.launch {
        delay(60000)
        if (tokenProfiles) {
            observeProfiles(dexScreenerRepository.tokenProfilesFlow)
        }
        if (tokenBoostedProfiles) {
            observeBoosted(dexScreenerRepository.latestBoostedTokensFlow)
            observeBoosted(dexScreenerRepository.topBoostedTokensFlow)
        }
        dexScreenerRepository.startAutoRefreshAll()
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