package com.bswap.server

import com.bswap.server.data.dexscreener.models.TokenBoost
import com.bswap.server.data.dexscreener.models.TokenProfile
import com.bswap.server.data.solana.pumpfun.WebSocketResponse
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.solana.transaction.DefaultTransactionExecutor
import com.bswap.server.data.solana.transaction.TokenInfo
import com.bswap.server.data.solana.transaction.createCloseAccountInstruction
import com.bswap.server.data.solana.transaction.executeSolTransaction
import com.bswap.server.data.solana.transaction.executeSwapTransaction
import com.bswap.server.data.solana.transaction.getTokenAccountsByOwner
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.networking.NetworkDriver
import foundation.metaplex.solanapublickeys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

enum class TokenState { SWAPPED, SOLD }

data class TokenStatus(
    val tokenAddress: String,
    var state: TokenState,
)

@OptIn(FlowPreview::class)
class SolanaTokenSwapBot(
    private val rpc: RPC,
    private val jupiterSwapService: JupiterSwapService,
    private val transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
    private val walletPublicKey: PublicKey = PublicKey(""),
    private val swapMint: PublicKey = PublicKey("So11111111111111111111111111111111111111112"),
    private val solAmountToTrade: BigDecimal = BigDecimal.valueOf(0.001),
    private val singleTradeToken: String? = null
) {

    private val tokenStateMap = mutableMapOf<String, TokenStatus>()
    private val coroutineScope = CoroutineScope(SupervisorJob())

    init {
        pereodicSellAllSPL(rpc, jupiterSwapService)
        closeZeroAccountsTask()
    }

    fun singleTrade(tokenMint: String) {
        processToken(solAmountToTrade, tokenMint)
    }

    private fun closeZeroAccountsTask(delay: Long = 60_000 * 5) {
        coroutineScope.launch {
            while (true) {
                delay(delay)
                val listOfTokens = getTokenAccountsByOwner(
                    NetworkDriver(client),
                    walletPublicKey,
                )
                val tokens = listOfTokens?.map { it.account.data.parsed.info }
                    ?: throw Exception("ListOfTokens failed")
                for (info in tokens.filter { it.tokenAmount.amount == "0" }) {
                    println("close account ${info.mint}")
                    delay(1000)
                    kotlin.runCatching {
                        executeSolTransaction(
                            rpc = rpc,
                            transactionInstruction = createCloseAccountInstruction(
                                tokenAccount = PublicKey(info.mint),
                                destination = swapMint,
                                owner = walletPublicKey
                            )
                        )
                    }
                }
            }
        }
    }

    private fun clearTokensMapWithDelay(delay: Long = 60_000 * 10) {
        coroutineScope.launch {
            while (true) {
                delay(delay)
                tokenStateMap.clear()
            }
        }
    }

    fun observeTokenProfiles(tokenProfilesFlow: Flow<List<TokenProfile>>) {
        coroutineScope.launch {
            tokenProfilesFlow.buffer().debounce(20_000).collect { profileList ->
                profileList.filter { it.chainId == "solana" }.forEach { profile ->
                    if (isNewToken(profile.tokenAddress)) {
                        delay(1000)
                        println("New token detected: ${profile.tokenAddress}")
                        processToken(solAmountToTrade, profile.tokenAddress)
                    }
                }
            }
        }
    }


    fun observePumpFun(tokenProfilesFlow: Flow<WebSocketResponse>) {
        coroutineScope.launch {
            tokenProfilesFlow.filter { it.marketCapSol > 35 }
                .buffer(2)
                .debounce(2000)
                .collect { response ->
                    if (isNewToken(response.mint)) {
                        delay(10000)
                        println("ProcessToken : ${response.mint}")
                        lastSellTimes[response.mint] = System.currentTimeMillis()
                        processToken(solAmountToTrade, response.mint)
                    }
                }
        }
    }

    fun observeTokenBoostedProfiles(tokenProfilesFlow: Flow<List<TokenBoost>>) {
        coroutineScope.launch {
            tokenProfilesFlow.sample(1000).collect { profileList ->
                profileList.filter { it.chainId == "solana" }.forEach { profile ->
                    if (isNewToken(profile.tokenAddress)) {
                        delay(2000)
                        lastSellTimes[profile.tokenAddress] = System.currentTimeMillis()
                        println("New token detected: ${profile.tokenAddress}")
                        processToken(solAmountToTrade, profile.tokenAddress)
                    }
                }
            }
        }
    }

    private fun isNewToken(tokenAddress: String): Boolean {
        return !tokenStateMap.containsKey(tokenAddress)
    }

    private fun processToken(amount: BigDecimal, tokenAddress: String) {
        tokenStateMap[tokenAddress] = TokenStatus(tokenAddress, TokenState.SWAPPED)
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                handleSwap(amount, tokenAddress)
            }
        }
    }

    private suspend fun handleSwap(amount: BigDecimal, tokenAddress: String) {
        val tokenStatus = tokenStateMap[tokenAddress] ?: return
        try {

            println("Swapping token: $tokenAddress")

            val response = jupiterSwapService.getQuoteAndPerformSwap(
                amount, swapMint.base58(), tokenAddress
            )

            val success =
                executeSwapTransaction(rpc, response.swapTransaction, transactionExecutor)
            if (success) {
                println("Token swapped successfully: $tokenAddress")
                tokenStatus.state = TokenState.SWAPPED
                // handleSell(profile)
                return
            } else {
                throw Exception("Swap transaction failed")
            }
        } catch (e: Exception) {
            println("Error during swap (${tokenAddress}): ${e.message}")
        }
    }

    private fun handleSell(profile: TokenProfile) = coroutineScope.launch {
        val tokenStatus = tokenStateMap[profile.tokenAddress] ?: return@launch
        try {
            println("Starting sell for token: ${profile.tokenAddress}")

            val listOfTokens = getTokenAccountsByOwner(
                NetworkDriver(client),
                walletPublicKey
            )

            val amount = listOfTokens?.map { it.account.data.parsed.info }?.firstOrNull {
                it.mint == profile.tokenAddress
            }?.tokenAmount?.amount ?: run {
                println("Sell Error no token amount: ${profile.tokenAddress}")
                return@launch
            }

            val response = jupiterSwapService.getQuoteAndPerformSwap(
                amount, profile.tokenAddress, swapMint.base58()
            )

            val success = executeSwapTransaction(rpc, response.swapTransaction, transactionExecutor)
            if (success) {
                println("Token sold successfully: ${profile.tokenAddress}")
                tokenStatus.state = TokenState.SOLD
            } else {
                throw Exception("Sell transaction failed")
            }
        } catch (e: Exception) {
            println("Error during sell (${profile.tokenAddress}): ${e.message}")
        }
    }

    private fun pereodicSellAllSPL(
        rpc: RPC,
        jupiterSwapService: JupiterSwapService,
        transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
        periodicDelay: Long = 20_000,
    ) = GlobalScope.launch {
        withContext(Dispatchers.Default) {
            while (true) {
                delay(periodicDelay)
                val listOfTokens = getTokenAccountsByOwner(
                    NetworkDriver(client),
                    walletPublicKey,
                )
                val tokens = listOfTokens?.map { it.account.data.parsed.info }
                    ?: throw Exception("ListOfTokens failed")
                for (info in tokens.filterNot { it.tokenAmount.amount == "0" }.shuffled()
                    .take(10)) {
                    if (sellLimit(info.mint, 59_000L)) {
                        delay(1000)
                        pereodicSellAllSPL(
                            rpc, info, jupiterSwapService, transactionExecutor
                        )
                    }
                }
            }
        }
    }

    private val lastSellTimes = mutableMapOf<String, Long>()

    private fun sellLimit(tokenId: String, waiteSellTime: Long): Boolean {
        val now = System.currentTimeMillis()
        val lastSellTime = lastSellTimes[tokenId] ?: 0L
        return if (now - lastSellTime >= waiteSellTime) {
            lastSellTimes[tokenId] = now
            true
        } else {
            false
        }
    }

    private fun pereodicSellAllSPL(
        rpc: RPC,
        info: TokenInfo,
        jupiterSwapService: JupiterSwapService,
        transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc)
    ) = GlobalScope.launch {
        try {
            if (info.mint !in listOf(
                    swapMint.base58(),
                )
            ) {
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
}

