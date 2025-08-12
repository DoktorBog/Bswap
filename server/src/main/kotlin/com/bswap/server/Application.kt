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
import com.bswap.server.routes.commandRoutes
import com.bswap.server.routes.startRoute
import com.bswap.server.routes.tokensRoute
import com.bswap.server.routes.walletRoutes
import com.bswap.server.service.BotManagementService
import com.bswap.server.service.PriceService
import com.bswap.server.service.ServerWalletService
import com.bswap.server.service.TokenMetadataService
import com.bswap.server.service.WalletService
import com.bswap.server.validation.TokenValidator
import com.bswap.server.validation.ValidationConfig
import com.bswap.shared.wallet.WalletInitializer
import com.bswap.shared.wallet.SeedToWalletConverter
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
    // Validate configuration first
    val configValidation = ConfigLoader.validateConfiguration()
    if (!configValidation.isValid) {
        println("❌ Configuration validation failed:")
        configValidation.errors.forEach { println("  - $it") }
        println("Please fix configuration errors and restart.")
        return
    }
    
    if (configValidation.hasWarnings) {
        println("⚠️ Configuration warnings:")
        configValidation.warnings.forEach { println("  - $it") }
        println()
    }
    
    println("✅ Configuration validated successfully")
    
    // Show OpenAI integration status
    val openaiKey = ConfigLoader.loadOpenAIKey()
    if (openaiKey != null) {
        println("✅ OpenAI API key loaded - AI strategies enabled")
    } else {
        println("⚠️ OpenAI API key not found - using fallback AI strategies")
    }
    
    // Prompt for seed phrase interactively on server start
    val seedPhrase = prompt("Enter your 12/24-word seed phrase (single line): ")
    require(seedPhrase.isNotBlank()) { "Seed phrase is required" }
    val wallet = WalletInitializer.initializeFromSeed(seedPhrase)
    println("Wallet initialized. Public Key: ${wallet.publicKey}")
    privateKey = wallet.privateKey

    // Initialize services
    val tokenValidator = TokenValidator(client, ValidationConfig())
    val tokenMetadataService = TokenMetadataService(client)
    val solanaRpcClient = SolanaRpcClient(client, tokenMetadataService = tokenMetadataService)
    val dexScreenerClient = DexScreenerClientImpl(client)
    val priceService = PriceService(client, dexScreenerClient)
    val serverWalletService = ServerWalletService(tokenValidator, solanaRpcClient, priceService)
    val botManagementService = BotManagementService(serverWalletService, priceService)
    val commandProcessor = com.bswap.server.command.CommandProcessor(botManagementService, serverWalletService, priceService)

    // Pre-populate wallet cache immediately after wallet initialization
    println("Pre-populating wallet cache for immediate availability...")
    appScope.launch {
        try {
            val request = com.bswap.shared.model.WalletHistoryRequest(limit = 50, offset = 0)
            serverWalletService.getBotWalletHistory(request, silent = false)
            println("✅ Wallet cache pre-population completed")
        } catch (e: Exception) {
            println("❌ Wallet cache pre-population failed: ${e.message}")
        }
    }

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
            walletRoutes(serverWalletService, tokenMetadataService, priceService)
            botRoutes(botManagementService)
            commandRoutes(commandProcessor)
        }
    }.start(wait = true)
}

private fun prompt(message: String): String {
    print(message)
    return try {
        val br = java.io.BufferedReader(java.io.InputStreamReader(System.`in`))
        br.readLine()?.trim().orEmpty()
    } catch (e: Exception) {
        ""
    }
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

private fun createRPC(client: HttpClient) =
    RPC(RPC_URL, NetworkDriver(client))
