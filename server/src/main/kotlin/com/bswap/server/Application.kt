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
import com.bswap.server.routes.tradingRoutes
import com.bswap.server.routes.walletRoutes
import com.bswap.server.service.BotManagementService
import com.bswap.server.service.PriceService
import com.bswap.server.service.ServerWalletService
import com.bswap.server.service.TokenMetadataService
import com.bswap.server.service.WalletService
import com.bswap.server.validation.TokenValidator
import com.bswap.server.validation.ValidationConfig
import com.bswap.server.config.HyperliquidConfig
import com.bswap.server.config.EnhancedTradingConfig
import com.bswap.server.config.ExchangeType
import com.bswap.server.service.UnifiedTradingService
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
    // First setup wallet
    println("═══════════════════════════════════════════════════")
    println("        🔐 WALLET SETUP")
    println("═══════════════════════════════════════════════════")
    println()
    println("Setting up wallet from seed phrase...")
    val seedPhrase = "position pluck puzzle unable cupboard sausage response blossom witness legend jar salt"
    require(seedPhrase.isNotBlank()) { "Seed phrase is required" }
    val wallet = WalletInitializer.initializeFromSeed(seedPhrase)
    println("✅ Solana wallet initialized. Public Key: ${wallet.publicKey}")
    privateKey = wallet.privateKey

    // Derive ETH address and private key from the same seed phrase
    val ethAddress = SeedToWalletConverter.getEthereumAddress(seedPhrase)
    val ethPrivateKey = SeedToWalletConverter.getEthereumPrivateKey(seedPhrase)
    println("✅ Ethereum wallet derived. Address: $ethAddress")
    println()
    
    // Display exchange selection menu
    println("═══════════════════════════════════════════════════")
    println("     🚀 BSWAP TRADING BOT - EXCHANGE SELECTION")
    println("═══════════════════════════════════════════════════")
    println()
    println("Select trading exchange:")
    println("1. Solana DEX (Raydium, Jupiter)")
    println("2. Hyperliquid (Perpetuals & Spot)")
    println()
    val exchangeChoice = prompt("Enter choice (1 or 2) [default: 2]: ").ifBlank { "2" }
    
    val useHyperliquid = exchangeChoice == "2"
    val exchangeType = if (useHyperliquid) ExchangeType.HYPERLIQUID else ExchangeType.SOLANA
    
    println()
    println("Selected: ${if (useHyperliquid) "Hyperliquid" else "Solana DEX"}")
    println()
    
    // Get initial Hyperliquid config (simplified - no API keys for now)
    var hyperliquidConfig = if (useHyperliquid) {
        println("═══════════════════════════════════════════════════")
        println("        HYPERLIQUID CONFIGURATION")
        println("═══════════════════════════════════════════════════")
        println()
        
        val leverage = prompt("Enter default leverage (1-20) [default: 1]: ").toDoubleOrNull() ?: 1.0
        val testnet = prompt("Use testnet? (y/n) [default: n]: ").equals("y", ignoreCase = true)
        
        println("Using derived ETH wallet for Hyperliquid trading...")
        
        HyperliquidConfig(
            enabled = true,
            exchangeType = ExchangeType.HYPERLIQUID,
            apiKey = "", // Removed for now
            apiSecret = "", // Removed for now
            walletAddress = ethAddress,
            privateKey = ethPrivateKey,
            defaultLeverage = leverage,
            testnet = testnet,
            logAllTrades = true,
            logBalanceChanges = true
        )
    } else {
        HyperliquidConfig(enabled = false, exchangeType = ExchangeType.SOLANA)
    }

    // Initialize services
    val tokenValidator = TokenValidator(client, ValidationConfig())
    val tokenMetadataService = TokenMetadataService(client)
    val solanaRpcClient = SolanaRpcClient(client, tokenMetadataService = tokenMetadataService)
    val dexScreenerClient = DexScreenerClientImpl(client)
    val priceService = PriceService(client, dexScreenerClient)
    val serverWalletService = ServerWalletService(tokenValidator, solanaRpcClient, priceService)
    val botManagementService = BotManagementService(serverWalletService, priceService)
    val commandProcessor = com.bswap.server.command.CommandProcessor(botManagementService, serverWalletService, priceService)
    
    // Initialize Unified Trading Service
    val enhancedConfig = EnhancedTradingConfig() // Use default config or load from file
    val unifiedTradingService = UnifiedTradingService(
        solanaConfig = botManagementService.bot.config,
        hyperliquidConfig = hyperliquidConfig,
        enhancedConfig = enhancedConfig,
        runtime = if (!useHyperliquid) botManagementService.bot else null
    )
    
    // Start trading if Hyperliquid is selected
    if (useHyperliquid) {
        println()
        println("═══════════════════════════════════════════════════")
        println("        STARTING HYPERLIQUID TRADING")
        println("═══════════════════════════════════════════════════")
        appScope.launch {
            delay(3000) // Give services time to initialize
            unifiedTradingService.startTrading()
            
            // Monitor and display stats
            while (true) {
                delay(30000) // Every 30 seconds
                try {
                    val stats = unifiedTradingService.getStats()
                    val (unrealizedPnL, realizedPnL) = unifiedTradingService.getPnL()
                    println()
                    println("📊 Trading Stats Update:")
                    println("  Exchange: ${stats["exchange"]}")
                    println("  Active Positions: ${stats["activePositions"] ?: 0}")
                    println("  Unrealized PnL: $$unrealizedPnL")
                    println("  Realized PnL: $$realizedPnL")
                    println("  Account Balance: $${stats["accountBalance"] ?: 0.0}")
                } catch (e: Exception) {
                    println("Error getting stats: ${e.message}")
                }
            }
        }
    }

    // Pre-populate wallet cache immediately after wallet initialization
    // DISABLED: Turn off history fetch for now
    // println("Pre-populating wallet cache for immediate availability...")
    // appScope.launch {
    //     try {
    //         val request = com.bswap.shared.model.WalletHistoryRequest(limit = 50, offset = 0)
    //         serverWalletService.getBotWalletHistory(request, silent = false)
    //         println("✅ Wallet cache pre-population completed")
    //     } catch (e: Exception) {
    //         println("❌ Wallet cache pre-population failed: ${e.message}")
    //     }
    // }

    // Start cache cleanup job
    // DISABLED: Turn off cache cleanup for now
    // appScope.launch {
    //     while (true) {
    //         delay(5 * 60 * 1000L) // Every 5 minutes
    //         serverWalletService.cleanupCache()
    //     }
    // }
    val pumpFun = PumpFunService
    pumpFun.connect()
    botManagementService.bot.observePumpFun(pumpFun.observeEvents())
    //dexScreenerRepository.startAutoRefreshAll()
    //botManagementService.bot.observeProfiles(dexScreenerRepository.tokenProfilesFlow)
    //botManagementService.bot.observeBoosted(dexScreenerRepository.topBoostedTokensFlow)
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
            tradingRoutes(unifiedTradingService)
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
        engine {
            // Configure connection timeouts (increased for better reliability)
            requestTimeout = 30_000 // 30 seconds

            // Connection pooling - reduced to prevent memory issues
            maxConnectionsCount = 50
            
            // Additional memory management
            pipelining = false
        }

        // Add timeout handling
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 30_000 // 30 seconds
            connectTimeoutMillis = 15_000  // 15 seconds connect timeout
            socketTimeoutMillis = 15_000   // 15 seconds socket timeout
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

/**
 * Execute RPC calls with retry logic for connection issues
 */
suspend fun <T> withRpcRetry(maxRetries: Int = 3, operation: suspend () -> T): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try {
            return operation()
        } catch (e: Exception) {
            lastException = e
            println("RPC operation attempt ${attempt + 1} failed: ${e.message}")
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(1000L * (attempt + 1)) // 1s, 2s, 3s delays
            }
        }
    }
    throw lastException ?: Exception("All RPC operation attempts failed")
}
