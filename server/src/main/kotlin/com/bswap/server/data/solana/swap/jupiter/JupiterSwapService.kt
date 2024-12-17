package com.bswap.server.data.solana.swap.jupiter

import com.bswap.server.data.formatLamports
import com.bswap.server.data.solana.swap.jupiter.models.QuoteResponse
import com.bswap.server.data.solana.swap.jupiter.models.SwapResponse
import com.bswap.server.data.solana.transaction.HotSigner
import com.bswap.server.data.solana.transaction.SolanaKeypair
import com.bswap.server.data.solana.transaction.privateKey
import foundation.metaplex.base58.decodeBase58
import foundation.metaplex.base58.encodeToBase58String
import foundation.metaplex.solanaeddsa.SolanaEddsa
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
import java.math.BigDecimal

class JupiterSwapService(
    private val client: HttpClient,
    private val jupiterApiUrl: String = "https://quote-api.jup.ag/v6"
) {

    private suspend fun getQuote(
        inputMint: String,
        outputMint: String,
        amount: Long,
        slippageBps: Int
    ): QuoteResponse {
        val response: HttpResponse = client.get("$jupiterApiUrl/quote") {
            parameter("inputMint", inputMint)
            parameter("outputMint", outputMint)
            parameter("amount", amount)
            parameter("slippageBps", slippageBps)
        }
        return Json.decodeFromString(QuoteResponse.serializer(), response.bodyAsText())
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
        return Json.decodeFromString(SwapResponse.serializer(), response.bodyAsText())
    }

    suspend fun getQuoteAndPerformSwap(
        amount: BigDecimal,
        inputMint: String,
        outputMint: String,
        slippageBps: Int = 100,
    ): SwapResponse {
        val k = SolanaEddsa.createKeypairFromSecretKey(privateKey.decodeBase58().copyOfRange(0, 32))
        val signer = HotSigner(SolanaKeypair(k.publicKey, k.secretKey))
        val response = getQuote(inputMint, outputMint, amount.formatLamports(), slippageBps)
        return performSwap(response, signer.publicKey.bytes.encodeToBase58String())
    }
}