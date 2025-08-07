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
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)

@Serializable
data class WalletBalance(
    val publicKey: String,
    val solBalance: Double,
    val tokenBalances: Map<String, Double> = emptyMap(),
    val totalValueUSD: Double = 0.0,
    val lastUpdated: Long
)

class WalletApi(private val client: HttpClient) {

    suspend fun walletInfo(address: String): WalletInfo {
        val balance = getBalance(address)
        val tokens = getTokens(address)
        return WalletInfo(address, balance, tokens)
    }

    suspend fun getBalance(address: String): Long {
        return try {
            val response = client.get("$baseUrl/wallet/balance/$address").body<String>()
            val apiResponse = kotlinx.serialization.json.Json.decodeFromString<ApiResponse<WalletBalance>>(response)
            if (apiResponse.success && apiResponse.data != null) {
                (apiResponse.data.solBalance * 1_000_000_000).toLong() // Convert SOL to lamports
            } else {
                0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    suspend fun getTokens(address: String): List<TokenInfo> {
        return try {
            val response = client.get("$baseUrl/wallet/tokens/$address").body<String>()
            val apiResponse = kotlinx.serialization.json.Json.decodeFromString<ApiResponse<List<TokenInfo>>>(response)
            if (apiResponse.success && apiResponse.data != null) {
                apiResponse.data
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getWalletBalance(address: String): WalletBalance? {
        return try {
            val response = client.get("$baseUrl/wallet/balance/$address").body<String>()
            val apiResponse = kotlinx.serialization.json.Json.decodeFromString<ApiResponse<WalletBalance>>(response)
            if (apiResponse.success && apiResponse.data != null) {
                apiResponse.data
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getHistory(
        address: String,
        limit: Int = 10,
        cursor: String? = null,
    ): HistoryPage {
        return try {
            // Convert cursor to offset for server pagination
            val offset = cursor?.let { 
                if (it.startsWith("page_")) {
                    it.removePrefix("page_").toIntOrNull()?.times(limit) ?: 0
                } else 0
            } ?: 0
            
            println("📱 CLIENT: Getting history for $address, limit=$limit, offset=$offset (from cursor: $cursor)")
            
            val response = client.post("$baseUrl/wallet/history") {
                contentType(ContentType.Application.Json)
                setBody(WalletHistoryRequest(
                    publicKey = address,
                    limit = limit,
                    offset = offset
                ))
            }.body<String>()
            
            println("📱 CLIENT: Raw response length: ${response.length}")
            
            // The server responds with HistoryPage directly (not wrapped in ApiResponse)
            val historyPage = kotlinx.serialization.json.Json.decodeFromString<HistoryPage>(response)
            println("📱 CLIENT: Decoded ${historyPage.transactions.size} transactions, nextCursor: ${historyPage.nextCursor}")
            historyPage
        } catch (e: Exception) {
            println("📱 CLIENT: Error getting history: ${e.message}")
            e.printStackTrace()
            HistoryPage(emptyList(), null)
        }
    }

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
