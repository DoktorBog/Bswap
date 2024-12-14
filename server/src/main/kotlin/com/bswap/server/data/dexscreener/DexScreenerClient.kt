package com.bswap.server.data.dexscreener

import com.bswap.server.data.dexscreener.models.Order
import com.bswap.server.data.dexscreener.models.PairsResponse
import com.bswap.server.data.dexscreener.models.TokenBoost
import com.bswap.server.data.dexscreener.models.TokenProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get


interface DexScreenerClient {
    suspend fun getTokenProfiles(): List<TokenProfile>
    suspend fun getLatestBoostedTokens(): List<TokenBoost>
    suspend fun getTopBoostedTokens(): List<TokenBoost>
    suspend fun getOrders(chainId: String, tokenAddress: String): List<Order>
    suspend fun getPairsByChainAndPair(chainId: String, pairId: String): PairsResponse
    suspend fun getPairsByToken(tokenAddresses: String): PairsResponse
    suspend fun searchPairs(query: String): PairsResponse
}

class DexScreenerClientImpl(private val httpClient: HttpClient) : DexScreenerClient {

    private val tokenProfilesUrl = "https://api.dexscreener.com/token-profiles/latest/v1"
    private val tokenBoostsLatestUrl = "https://api.dexscreener.com/token-boosts/latest/v1"
    private val tokenBoostsTopUrl = "https://api.dexscreener.com/token-boosts/top/v1"
    private val ordersBaseUrl = "https://api.dexscreener.com/orders/v1"
    private val pairsBaseUrl = "https://api.dexscreener.com/latest/dex"

    override suspend fun getTokenProfiles(): List<TokenProfile> =
        httpClient.get(tokenProfilesUrl).body()

    override suspend fun getLatestBoostedTokens(): List<TokenBoost> =
        httpClient.get(tokenBoostsLatestUrl).body()

    override suspend fun getTopBoostedTokens(): List<TokenBoost> =
        httpClient.get(tokenBoostsTopUrl).body()

    override suspend fun getOrders(chainId: String, tokenAddress: String): List<Order> {
        val url = "$ordersBaseUrl/$chainId/$tokenAddress"
        return httpClient.get(url).body()
    }

    override suspend fun getPairsByChainAndPair(chainId: String, pairId: String): PairsResponse {
        val url = "$pairsBaseUrl/pairs/$chainId/$pairId"
        return httpClient.get(url).body()
    }

    override suspend fun getPairsByToken(tokenAddresses: String): PairsResponse {
        val url = "$pairsBaseUrl/tokens/$tokenAddresses"
        return httpClient.get(url).body()
    }

    override suspend fun searchPairs(query: String): PairsResponse {
        val url = "$pairsBaseUrl/search?q=$query"
        return httpClient.get(url).body()
    }
}