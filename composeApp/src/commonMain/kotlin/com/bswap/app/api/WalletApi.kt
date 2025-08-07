package com.bswap.app.api

import com.bswap.app.baseUrl
import com.bswap.shared.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType

class WalletApi(private val client: HttpClient) {

    suspend fun walletInfo(address: String): WalletInfo {
        val balance = getBalance(address)
        val tokens = getTokens(address)
        return WalletInfo(address, balance, tokens)
    }

    suspend fun getBalance(address: String): Long =
        client.get("$baseUrl/wallet/$address/balance").body()

    suspend fun getTokens(address: String): List<TokenInfo> =
        client.get("$baseUrl/wallet/$address/tokens").body()

    suspend fun getHistory(
        address: String,
        limit: Int = 10,
        cursor: String? = null,
    ): HistoryPage =
        client.post("$baseUrl/wallet/history") {
            contentType(ContentType.Application.Json)
            setBody(WalletHistoryRequest(
                publicKey = address,
                limit = limit,
                offset = 0
            ))
        }.body()

    suspend fun swap(request: SwapRequest): SwapTx =
        client.post("$baseUrl/swap") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun submitTx(tx: SignedTx) {
        client.post("$baseUrl/submitTx") {
            contentType(ContentType.Application.Json)
            setBody(tx)
        }
    }
}
