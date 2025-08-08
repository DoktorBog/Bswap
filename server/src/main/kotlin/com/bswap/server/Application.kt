package com.bswap.server

import com.bswap.server.data.dexscreener.DexScreenerClientImpl
import com.bswap.server.data.dexscreener.DexScreenerRepository
import com.bswap.server.data.solana.jito.JitoBundlerService
import com.bswap.server.data.solana.pumpfun.PumpFunService
import com.bswap.server.data.solana.rpc.SolanaRpcClient
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.tokenlist.TokenListRepo
import com.bswap.server.routes.apiRoute
import com.bswap.server.routes.botRoutes
import com.bswap.server.routes.startRoute
import com.bswap.server.routes.tokensRoute
import com.bswap.server.routes.walletRoutes
import com.bswap.server.service.BotManagementService
import com.bswap.server.service.ServerWalletService
import com.bswap.server.service.TokenMetadataService
import com.bswap.server.service.WalletService
import com.bswap.server.validation.TokenValidator
import com.bswap.server.validation.ValidationConfig
import com.bswap.shared.wallet.WalletEngineUsage
import com.bswap.shared.wallet.WalletInitializer
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.networking.NetworkDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

fun main() {
//    // Initialize wallet configuration from file
//    println("Initializing bot wallet...")
//    try {
//        val wallet = WalletInitializer.initializeFromFile(autoCreate = true)
//        println("Bot wallet initialized successfully:")
//        println("  Public Key: ${wallet.publicKey}")
//        println("  Wallet ready for bot operations")
//    } catch (e: Exception) {
//        println("ERROR: Failed to initialize bot wallet - ${e.message}")
//        println("Bot will not be able to perform trades without a valid wallet!")
//        // Continue startup but log the issue
//    }

    WalletEngineUsage.applicationStartup()

    // Initialize services
    val tokenValidator = TokenValidator(client, ValidationConfig())
    val tokenMetadataService = TokenMetadataService(client)
    val solanaRpcClient = SolanaRpcClient(client, tokenMetadataService = tokenMetadataService)
    val serverWalletService = ServerWalletService(tokenValidator, solanaRpcClient)
    val botManagementService = BotManagementService(serverWalletService)

    // Start cache cleanup job
    appScope.launch {
        while (true) {
            delay(5 * 60 * 1000L) // Every 5 minutes
            serverWalletService.cleanupCache()
        }
    }
    val pumpFun = PumpFunService
    pumpFun.connect()
    botManagementService.bot.observePumpFun(pumpFun.observeEvents())
    dexScreenerRepository.startAutoRefreshAll()
    botManagementService.bot.observeProfiles(dexScreenerRepository.tokenProfilesFlow)
    botManagementService.bot.observeBoosted(dexScreenerRepository.topBoostedTokensFlow)
    embeddedServer(Netty, port = SERVER_PORT) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            startRoute()
            tokensRoute(dexScreenerRepository.tokenProfilesFlow)
            apiRoute(dexScreenerRepository.tokenProfilesFlow)
            walletRoutes(serverWalletService)
            botRoutes(botManagementService)
        }
    }.start(wait = true)
}

fun SolanaTokenSwapBot.runDexScreenerSwap(
    tokenProfiles: Boolean = true,
    tokenBoostedProfiles: Boolean = true,
    maxTokens: Int = 10,
    coroutineScope: CoroutineScope
) {
    coroutineScope.launch {
        delay(3_000)
        if (tokenProfiles) {
            observeProfiles(dexScreenerRepository.tokenProfilesFlow.take(maxTokens))
        }
        if (tokenBoostedProfiles) {
            observeBoosted(dexScreenerRepository.latestBoostedTokensFlow.take(maxTokens))
            observeBoosted(dexScreenerRepository.topBoostedTokensFlow.take(maxTokens))
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
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}

val rpc = createRPC(client)

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

val dexScreenerRepository = DexScreenerRepository(
    client = DexScreenerClientImpl(client),
    coroutineScope = appScope
)

val walletRepository = com.bswap.shared.wallet.WalletRepository(
    com.bswap.shared.wallet.LoggingWalletDecorator(
        com.bswap.shared.wallet.MetricsWalletDecorator(
            com.bswap.shared.wallet.WalletCoreAdapterImpl()
        )
    )
)

val walletService = WalletService(
    SolanaRpcClient(client),
    TokenListRepo(client),
    JupiterSwapService(client),
    JitoBundlerService(
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
    ),
    walletRepository
)

private fun createRPC(client: HttpClient) =
    RPC(RPC_URL, NetworkDriver(client))
