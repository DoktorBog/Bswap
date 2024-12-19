package com.bswap.server

import com.bswap.server.data.dexscreener.models.TokenBoost
import com.bswap.server.data.dexscreener.models.TokenProfile
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.solana.transaction.DefaultTransactionExecutor
import com.bswap.server.data.solana.transaction.executeSwapTransaction
import com.bswap.server.data.solana.transaction.getTokenAccountsByOwner
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.networking.NetworkDriver
import foundation.metaplex.solanapublickeys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import kotlin.random.Random

val swapMint = PublicKey("So11111111111111111111111111111111111111112")

enum class TokenState { SWAPPED, RETRYING_SELL, SOLD, FAILED }

data class TokenStatus(
    val tokenAddress: String,
    var state: TokenState,
)

@OptIn(FlowPreview::class)
class SolanaTokenSwapBot(
    private val rpc: RPC,
    private val jupiterSwapService: JupiterSwapService,
    private val transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc)
) {

    private val tokenStateMap = mutableMapOf<String, TokenStatus>()
    private val coroutineScope = CoroutineScope(SupervisorJob())

    init {
        //coroutineScope.launch {
        //    while (true) {
        //        delay(60_000 * 10)
        //        tokenStateMap.clear()
        //    }
        //}
    }

    // Main function to observe the token profiles
    fun observeTokenProfiles(tokenProfilesFlow: Flow<List<TokenProfile>>) {
        coroutineScope.launch {
            tokenProfilesFlow.buffer().debounce(20_000).collect { profileList ->
                profileList.filter { it.chainId == "solana" }.forEach { profile ->
                    if (isNewToken(profile.tokenAddress)) {
                        delay(1000)
                        println("New token detected: ${profile.tokenAddress}")
                        processToken(BigDecimal.valueOf(0.001), profile.tokenAddress)
                    }
                }
            }
        }
    }

    fun observeTokenBoostedProfiles(tokenProfilesFlow: Flow<List<TokenBoost>>) {
        coroutineScope.launch {
            tokenProfilesFlow.collect { profileList ->
                profileList.filter { it.chainId == "solana" }.forEach { profile ->
                    if (isNewToken(profile.tokenAddress)) {
                        delay(1000)
                        println("New token detected: ${profile.tokenAddress}")
                        processToken(BigDecimal.valueOf(0.001), profile.tokenAddress)
                    }
                }
            }
        }
    }

    // Check if the token is new
    private fun isNewToken(tokenAddress: String): Boolean {
        return !tokenStateMap.containsKey(tokenAddress)
    }

    // Start processing the token in a new coroutine
    private fun processToken(amount: BigDecimal, tokenAddress: String) {
        tokenStateMap[tokenAddress] = TokenStatus(tokenAddress, TokenState.SWAPPED)
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                handleSwap(amount, tokenAddress)
            }
        }
    }

    // Handle the swap process with retry logic
    private suspend fun handleSwap(amount: BigDecimal, tokenAddress: String) {
        val tokenStatus = tokenStateMap[tokenAddress] ?: return
        delay(1_000 * Random.nextInt(2, 7).toLong())
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
                //tokenStateMap.remove(tokenAddress)
                throw Exception("Swap transaction failed")
            }
        } catch (e: Exception) {
            //tokenStateMap.remove(tokenAddress)
            println("Error during swap (${tokenAddress}): ${e.message}")
        }

        println("Swap failed after retries: $tokenAddress")
        tokenStatus.state = TokenState.FAILED
        //tokenStateMap.remove(tokenAddress)
    }

    // Handle the sell process
    private fun handleSell(profile: TokenProfile) = coroutineScope.launch {
        val tokenStatus = tokenStateMap[profile.tokenAddress] ?: return@launch
        try {
            println("Starting sell for token: ${profile.tokenAddress}")

            val listOfTokens = getTokenAccountsByOwner(
                NetworkDriver(client),
                PublicKey("F277zfVkW6VBfkfWPNVXKoBEgCCeVcFYdiZDUX9yCPDW"),
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
            tokenStatus.state = TokenState.RETRYING_SELL
        }
    }
}

