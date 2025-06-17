package com.bswap.server.service

import com.bswap.server.data.solana.rpc.SolanaRpcClient
import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.data.tokenlist.TokenListRepo
import com.bswap.server.data.solana.jito.JitoBundlerService
import com.bswap.shared.model.*
import org.slf4j.LoggerFactory

class WalletService(
    private val rpcClient: SolanaRpcClient,
    private val tokenRepo: TokenListRepo,
    private val jupiter: JupiterSwapService,
    private val jito: JitoBundlerService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    sealed class Failure {
        data class Rpc(val message: String) : Failure()
        data class Jupiter(val message: String) : Failure()
        data class TokenList(val message: String) : Failure()
    }

    suspend fun getBalance(address: String): Result<Long> = try {
        Result.success(rpcClient.getBalance(address))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getTokens(address: String): Result<List<TokenInfo>> = try {
        Result.success(rpcClient.getSPLTokens(address))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getHistory(address: String): Result<List<SolanaTx>> = try {
        Result.success(rpcClient.getHistory(address))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun walletInfo(address: String): Result<WalletInfo> = try {
        val balance = rpcClient.getBalance(address)
        val tokens = rpcClient.getSPLTokens(address)
        Result.success(WalletInfo(address, balance, tokens))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun searchTokens(query: String): Result<List<TokenInfo>> = try {
        Result.success(tokenRepo.search(query))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun swap(request: SwapRequest): Result<SwapTx> = try {
        val response = jupiter.getQuoteAndPerformSwap(
            request.amount,
            request.inputMint,
            request.outputMint,
            request.owner
        )
        val txBase64 = response.swapTransaction
            ?: return Result.failure(Exception("no route"))
        Result.success(
            SwapTx(
                transaction = txBase64,
                lastValidBlockHeight = response.lastValidBlockHeight,
                prioritizationFeeLamports = response.prioritizationFeeLamports
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun swapBatch(requests: List<SwapRequest>): Result<List<SwapTx>> = try {
        val results = requests.mapNotNull { req ->
            val resp = jupiter.getQuoteAndPerformSwap(
                req.amount,
                req.inputMint,
                req.outputMint,
                req.owner
            )
            resp.swapTransaction?.let {
                SwapTx(
                    transaction = it,
                    lastValidBlockHeight = resp.lastValidBlockHeight,
                    prioritizationFeeLamports = resp.prioritizationFeeLamports
                )
            }
        }
        Result.success(results)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun submit(tx: SignedTx): Result<Unit> = try {
        val bytes = java.util.Base64.getDecoder().decode(tx.transaction)
        jito.enqueue(bytes)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
