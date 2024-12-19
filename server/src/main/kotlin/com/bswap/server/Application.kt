package com.bswap.server

import com.bswap.server.data.dexscreener.DexScreenerClientImpl
import com.bswap.server.data.dexscreener.DexScreenerRepository
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.solana.transaction.DefaultTransactionExecutor
import com.bswap.server.data.solana.transaction.TokenInfo
import com.bswap.server.data.solana.transaction.executeSwapTransaction
import com.bswap.server.data.solana.transaction.getTokenAccountsByOwner
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.networking.NetworkDriver
import foundation.metaplex.solanapublickeys.PublicKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val rpc = createRPC(client)

    val jupiterSwapService = JupiterSwapService(client)
    //val dexScreenerRepository = DexScreenerRepository(DexScreenerClientImpl(client))
    //val bot = SolanaTokenSwapBot(rpc, jupiterSwapService)
    sellAllSPL(rpc, jupiterSwapService)
    GlobalScope.launch {
        //bot.observeTokenProfiles(dexScreenerRepository.tokenProfilesFlow)
        //bot.observeTokenBoostedProfiles(dexScreenerRepository.latestBoostedTokensFlow)
        //bot.observeTokenBoostedProfiles(dexScreenerRepository.topBoostedTokensFlow)
        //dexScreenerRepository.startAutoRefreshAll()
    }


    embeddedServer(Netty, port = SERVER_PORT) {
        routing {
            //startRoute()
            //tokensRoute(dexScreenerRepository.tokenProfilesFlow)
            //apiRoute(dexScreenerRepository.tokenProfilesFlow)
        }
    }.start(wait = true)
}

private fun sellAllSPL(
    rpc: RPC,
    jupiterSwapService: JupiterSwapService,
    transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc)
) = GlobalScope.launch {
    delay(30_000) // delay for tokens buy
    withContext(Dispatchers.Default) {
        while (true) {
            val listOfTokens = getTokenAccountsByOwner(
                NetworkDriver(client),
                PublicKey(""),
            )
            val tokens = listOfTokens?.map { it.account.data.parsed.info }
                ?: throw Exception("ListOfTokens failed")
            for (info in tokens) {
                if (sellLimit(info.mint)) {
                    sellAllSPL(
                        rpc, info, jupiterSwapService, transactionExecutor
                    )
                }
            }
            delay(60_000)
        }
    }
}

private val lastSellTimes = mutableMapOf<String, Long>()

fun sellLimit(tokenId: String): Boolean {
    val now = System.currentTimeMillis()
    val lastSellTime = lastSellTimes[tokenId] ?: 0L
    val waiteSellTime = 45_000 * 2
    return if (now - lastSellTime >= waiteSellTime) {
        lastSellTimes[tokenId] = now
        true
    } else {
        false
    }
}

private fun sellAllSPL(
    rpc: RPC,
    info: TokenInfo,
    jupiterSwapService: JupiterSwapService,
    transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc)
) = GlobalScope.launch {
    try {
        if (info.mint !in listOf(
                "So11111111111111111111111111111111111111112",
                "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
            )
        ) {
            // Convert token amount using BigInteger and scale based on decimals
            val amount = info.tokenAmount.amount

            if (amount == "0") return@launch

            println("Attempting to sell token: ${info.mint} with amount: $amount")

            val response = jupiterSwapService.getQuoteAndPerformSwap(
                info.tokenAmount.amount,
                info.mint,
                swapMint.base58()
            )

            val success = runCatching {
                executeSwapTransaction(rpc, response.swapTransaction, transactionExecutor)
            }.isSuccess

            if (success) {
                println("Token sold successfully: ${info.mint}")
            } else {
                println("Sell transaction failed for token: ${info.mint}")
            }
        }
    } catch (e: Exception) {
        println("Error processing token ${info.mint}: ${e.message}")
    }
}


val client by lazy {
    HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}

private fun createRPC(client: HttpClient) =
    RPC(RPC_URL, NetworkDriver(client))