package com.bswap.server.data.solana.swap.jupiter

import com.bswap.server.JUPITER_API_URL
import com.bswap.server.data.solana.swap.jupiter.models.QuoteResponse
import com.bswap.server.data.solana.swap.jupiter.models.SwapResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis

class JupiterSwapService(
    private val client: HttpClient,
    private val jupiterApiUrl: String = JUPITER_API_URL
) {
    val json = Json { ignoreUnknownKeys = true }

    private suspend fun getQuote(
        inputMint: String,
        outputMint: String,
        amount: Long,
    ): QuoteResponse {
        lateinit var text: String
        val time = measureTimeMillis {
            text = client.get("$jupiterApiUrl/quote") {
                parameter("inputMint", inputMint)
                parameter("outputMint", outputMint)
                parameter("amount", amount)
                parameter("autoSlippage", true)
            }.bodyAsText()
        }
        return json.decodeFromString(QuoteResponse.serializer(), text)
    }

    private suspend fun performSwap(
        quote: QuoteResponse,
        userPublicKey: String
    ): SwapResponse {
        val response: HttpResponse = client.post("$jupiterApiUrl/swap") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "quoteResponse": ${Json.encodeToString(QuoteResponse.serializer(), quote)},
                    "userPublicKey": "$userPublicKey",
                    "wrapAndUnwrapSol": true
                }
                """.trimIndent()
            )
        }
        response.bodyAsText()
        return json.decodeFromString(SwapResponse.serializer(), response.bodyAsText())
    }

    suspend fun getQuoteAndPerformSwap(
        amount: String,
        inputMint: String,
        outputMint: String,
        userPublicKey: String,
    ): SwapResponse {
        // Convert decimal amount to lamports (1 SOL = 1_000_000_000 lamports)
        val amountInLamports = (amount.toDouble() * 1_000_000_000).toLong()
        val quote = getQuote(inputMint, outputMint, amountInLamports)
        return performSwap(quote, userPublicKey)
    }

    suspend fun getQuoteAndPerformSwap(
        amount: Double,
        inputMint: String,
        outputMint: String,
        userPublicKey: String,
    ): SwapResponse {
        val amountInLamports = amount.toLong()
        val quote = getQuote(inputMint, outputMint, amountInLamports)
        return performSwap(quote, userPublicKey)
    }
}
