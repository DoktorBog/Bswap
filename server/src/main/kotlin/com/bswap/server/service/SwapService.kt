package com.bswap.server.service

import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import com.bswap.server.model.SwapRequest
import com.bswap.server.model.SwapQuote
import com.bswap.server.model.SwapTx
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class SwapService(
    private val jupiter: JupiterSwapService,
) {
    private val mutex = Mutex()
    private val statuses = mutableMapOf<String, String>()

    suspend fun quote(request: SwapRequest): SwapQuote {
        val quote = jupiter.getQuote(request.inputMint, request.outputMint, request.amount.toLong())
        return SwapQuote(
            inputMint = quote.inputMint ?: request.inputMint,
            outputMint = quote.outputMint ?: request.outputMint,
            inAmount = quote.inAmount ?: request.amount,
            outAmount = quote.outAmount ?: "0",
            priceImpactPct = quote.priceImpactPct
        )
    }

    suspend fun buildSwap(request: SwapRequest): SwapTx {
        val response = jupiter.getQuoteAndPerformSwap(
            request.amount,
            request.inputMint,
            request.outputMint,
            request.owner
        )
        val txBase64 = response.swapTransaction ?: throw IllegalStateException("no route")
        val id = UUID.randomUUID().toString()
        mutex.withLock { statuses[id] = "built" }
        return SwapTx(
            swapId = id,
            transaction = txBase64,
            lastValidBlockHeight = response.lastValidBlockHeight,
            prioritizationFeeLamports = response.prioritizationFeeLamports
        )
    }

    suspend fun status(id: String): String? = mutex.withLock { statuses[id] }
}
