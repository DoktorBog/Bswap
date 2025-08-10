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
    val publicKey: String = "",
    val solBalance: Double = 0.0,
    val tokenBalances: Map<String, Double> = emptyMap(),
    val totalValueUSD: Double = 0.0,
    val lastUpdated: Long = 0L
)

@Serializable
data class WalletReadyResponse(
    val success: Boolean,
    val message: String,
    val ready: Boolean,
    val walletPublicKey: String? = null,
    val transactionCount: Int = 0,
    val isBackgroundFetching: Boolean = false,
    val isFullyFetched: Boolean = false,
    val reason: String? = null
)

class WalletApi(private val client: HttpClient) {
    
    // Cache for wallet readiness to avoid repeated checks
    private var walletReadyCache: Boolean? = null
    private var walletReadyCacheTime: Long = 0
    private val walletReadyCacheTtl = 30_000 // 30 seconds
    
    // Cache for balance to reduce server requests
    private var balanceCache: Long? = null
    private var balanceCacheTime: Long = 0
    private val balanceCacheTtl = 5_000 // 5 seconds

    suspend fun walletInfo(address: String): WalletInfo {
        val balance = getBalance()
        val tokens = getTokens()
        return WalletInfo(address, balance, tokens)
    }

    suspend fun isWalletReady(): Boolean {
        val now = System.currentTimeMillis()
        
        // Return cached result if available and not expired
        walletReadyCache?.let { cachedResult ->
            if (now - walletReadyCacheTime < walletReadyCacheTtl) {
                return cachedResult
            }
        }
        
        return try {
            val response = client.get("$baseUrl/wallet/ready").body<String>()
            val walletReadyResponse = kotlinx.serialization.json.Json.decodeFromString<WalletReadyResponse>(response)
            val isReady = walletReadyResponse.success && walletReadyResponse.ready
            
            // Cache the result
            walletReadyCache = isReady
            walletReadyCacheTime = now
            
            if (!isReady) {
                println("📱 CLIENT: Wallet not ready - reason: ${walletReadyResponse.reason}")
            }
            
            isReady
        } catch (e: Exception) {
            println("❌ Error checking wallet readiness: ${e.message}")
            // Don't cache failures
            false
        }
    }

    private suspend fun waitForWalletReady(maxWaitTimeMs: Long = 30000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < maxWaitTimeMs) {
            if (isWalletReady()) {
                return true
            }
            kotlinx.coroutines.delay(1000) // Wait 1 second between checks
        }
        return false
    }

    suspend fun getBalance(): Long {
        val now = System.currentTimeMillis()
        
        // Return cached balance if available and not expired
        balanceCache?.let { cachedBalance ->
            if (now - balanceCacheTime < balanceCacheTtl) {
                return cachedBalance
            }
        }
        
        return try {
            // Only check wallet readiness if we don't have a cached ready state
            if (walletReadyCache != true) {
                if (!isWalletReady()) {
                    println("📱 CLIENT: Wallet not ready, waiting briefly...")
                    waitForWalletReady(5000) // Reduced wait time
                }
            }

            val response = client.get("$baseUrl/wallet/balance").body<String>()
            val apiResponse = kotlinx.serialization.json.Json.decodeFromString<ApiResponse<WalletBalance>>(response)
            
            if (apiResponse.success && apiResponse.data != null) {
                val balance = (apiResponse.data.solBalance * 1_000_000_000).toLong()
                
                // Cache the successful result
                balanceCache = balance
                balanceCacheTime = now
                
                balance
            } else {
                println("📱 CLIENT: Balance request failed: ${apiResponse.message}")
                0L
            }
        } catch (e: Exception) {
            println("📱 CLIENT: Error getting balance: ${e.message}")
            // Don't cache failures, return previous cached value if available
            balanceCache ?: 0L
        }
    }
    
    /**
     * Clear cached data to force fresh API calls
     */
    fun clearCache() {
        walletReadyCache = null
        walletReadyCacheTime = 0
        balanceCache = null
        balanceCacheTime = 0
    }

    suspend fun getTokens(): List<TokenInfo> {
        return try {
            val response = client.get("$baseUrl/wallet/tokens").body<String>()
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

    suspend fun getWalletBalance(): WalletBalance? {
        return try {
            val response = client.get("$baseUrl/wallet/balance").body<String>()
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
        limit: Int = 10,
        cursor: String? = null,
    ): HistoryPage {
        return try {
            // For first page requests, check if wallet is ready, if not wait a bit
            if (cursor == null || cursor == "page_0") {
                println("📱 CLIENT: First page request - checking wallet readiness")
                if (!isWalletReady()) {
                    println("📱 CLIENT: Wallet not ready, waiting for initialization...")
                    if (!waitForWalletReady(15000)) {
                        println("📱 CLIENT: Wallet still not ready after waiting, proceeding anyway")
                    } else {
                        println("📱 CLIENT: Wallet is now ready!")
                    }
                }
            }

            // Convert cursor to offset for server pagination
            val offset = cursor?.let {
                if (it.startsWith("page_")) {
                    it.removePrefix("page_").toIntOrNull()?.times(limit) ?: 0
                } else 0
            } ?: 0

            println("📱 CLIENT: Getting history, limit=$limit, offset=$offset (from cursor: $cursor)")

            val response = client.post("$baseUrl/wallet/history") {
                contentType(ContentType.Application.Json)
                setBody(WalletHistoryRequest(
                    limit = limit,
                    offset = offset
                ))
            }.body<String>()

            println("📱 CLIENT: Raw response length: ${response.length}")

            // The server responds with HistoryPage directly; however ensure it's valid JSON
            val historyPage = kotlinx.serialization.json.Json.decodeFromString<HistoryPage>(response)
            println("📱 CLIENT: Decoded ${historyPage.transactions.size} transactions, nextCursor: ${historyPage.nextCursor}")

            // If we got empty results on first page, retry once after a delay
            if (historyPage.transactions.isEmpty() && (cursor == null || cursor == "page_0")) {
                println("📱 CLIENT: Got empty results on first page, retrying after delay...")
                kotlinx.coroutines.delay(3000)

                val retryResponse = client.post("$baseUrl/wallet/history") {
                    contentType(ContentType.Application.Json)
                    setBody(WalletHistoryRequest(
                        limit = limit,
                        offset = offset
                    ))
                }.body<String>()

                val retryHistoryPage = kotlinx.serialization.json.Json.decodeFromString<HistoryPage>(retryResponse)
                println("📱 CLIENT: Retry decoded ${retryHistoryPage.transactions.size} transactions")
                return retryHistoryPage
            }

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
