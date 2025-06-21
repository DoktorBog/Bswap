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
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

class JupiterSwapService(
    private val client: HttpClient,
    private val jupiterApiUrl: String = JUPITER_API_URL
) {
    val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun getQuote(
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
        logger.debug("quote latency=${time}ms")
        return json.decodeFromString(QuoteResponse.serializer(), text)
    }

    private suspend fun performSwap(
        quote: QuoteResponse,
        userPublicKey: String
    ): SwapResponse {
        lateinit var text: String
        val time = measureTimeMillis {
            text = client.post("$jupiterApiUrl/swap") {
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
            }.bodyAsText()
        }
        logger.debug("swap latency=${time}ms")
        return json.decodeFromString(SwapResponse.serializer(), text)
    }

    suspend fun getQuoteAndPerformSwap(
        amount: String,
        inputMint: String,
        outputMint: String,
        userPublicKey: String,
    ): SwapResponse {
        val quote = getQuote(inputMint, outputMint, amount.toLong())
        return performSwap(quote, userPublicKey)
    }
}