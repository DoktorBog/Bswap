package com.bswap.server.data.dexscreener

import com.bswap.server.data.dexscreener.models.Order
import com.bswap.server.data.dexscreener.models.PairsResponse
import com.bswap.server.data.dexscreener.models.TokenBoost
import com.bswap.server.data.dexscreener.models.TokenProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

const val autoRefreshInterval = 4_000L

class DexScreenerRepository(
    private val client: DexScreenerClient,
    private val rateLimiter: DexScreenerRateLimiter = DexScreenerRateLimiter(),
    private val coroutineScope: CoroutineScope = GlobalScope
) {

    fun startAutoRefreshAll(
        chainId: String? = null,
        tokenAddress: String? = null,
        pairId: String? = null,
        tokenAddresses: String? = null,
        query: String? = null
    ) {
        // Refresh endpoints that require no parameters
        startAutoRefreshTokenProfiles()
        startAutoRefreshLatestBoostedTokens()
        //startAutoRefreshTopBoostedTokens()
//
        // Refresh endpoints that need parameters if provided
        if (chainId != null && tokenAddress != null) {
            startAutoRefreshOrders(chainId, tokenAddress)
        }
//
        //if (chainId != null && pairId != null) {
        //    startAutoRefreshPairsByChainAndPair(chainId, pairId)
        //}
//
        //if (tokenAddresses != null) {
        //    startAutoRefreshPairsByToken(tokenAddresses)
        //}
//
        //if (query != null) {
        //    startAutoRefreshSearchPairs(query)
        //}
    }

    private val logger = LoggerFactory.getLogger(DexScreenerRepository::class.java)

    private val _tokenProfilesFlow = MutableStateFlow<List<TokenProfile>>(emptyList())
    val tokenProfilesFlow: StateFlow<List<TokenProfile>> get() = _tokenProfilesFlow

    private val _latestBoostedTokensFlow = MutableStateFlow<List<TokenBoost>>(emptyList())
    val latestBoostedTokensFlow: StateFlow<List<TokenBoost>> get() = _latestBoostedTokensFlow

    private val _topBoostedTokensFlow = MutableStateFlow<List<TokenBoost>>(emptyList())
    val topBoostedTokensFlow: StateFlow<List<TokenBoost>> get() = _topBoostedTokensFlow

    private val _ordersFlow = MutableStateFlow<List<Order>>(emptyList())
    val ordersFlow: StateFlow<List<Order>> get() = _ordersFlow

    private val _pairsFlow = MutableStateFlow<PairsResponse?>(null)
    val pairsFlow: StateFlow<PairsResponse?> get() = _pairsFlow

    private val _pairsByTokenFlow = MutableStateFlow<PairsResponse?>(null)
    val pairsByTokenFlow: StateFlow<PairsResponse?> get() = _pairsByTokenFlow

    private val _searchPairsFlow = MutableStateFlow<PairsResponse?>(null)
    val searchPairsFlow: StateFlow<PairsResponse?> get() = _searchPairsFlow

    private var tokenProfilesJob: Job? = null
    private var latestBoostedTokensJob: Job? = null
    private var topBoostedTokensJob: Job? = null
    private var ordersJob: Job? = null
    private var pairsJob: Job? = null
    private var pairsByTokenJob: Job? = null
    private var searchPairsJob: Job? = null
    
    private suspend fun refreshTokenProfiles() {
        rateLimiter.acquire(RateLimitCategory.Standard)
        try {
            val data = client.getTokenProfiles()
            _tokenProfilesFlow.value = data
            logger.info("Fetched ${data.size} token profiles")
        } catch (e: Exception) {
            logger.error("Failed to refresh token profiles: ${e.message}", e)
        }
    }

    private suspend fun refreshLatestBoostedTokens() {
        rateLimiter.acquire(RateLimitCategory.Standard)
        try {
            val data = client.getLatestBoostedTokens()
            _latestBoostedTokensFlow.value = data
            logger.info("Fetched ${data.size} latest boosted tokens")
        } catch (e: Exception) {
            logger.error("Failed to refresh latest boosted tokens: ${e.message}", e)
        }
    }

    private suspend fun refreshTopBoostedTokens() {
        rateLimiter.acquire(RateLimitCategory.Standard)
        try {
            val data = client.getTopBoostedTokens()
            _topBoostedTokensFlow.value = data
            logger.info("Fetched ${data.size} top boosted tokens")
        } catch (e: Exception) {
            logger.error("Failed to refresh top boosted tokens: ${e.message}", e)
        }
    }

    private suspend fun refreshOrders(chainId: String, tokenAddress: String) {
        rateLimiter.acquire(RateLimitCategory.Standard)
        try {
            val data = client.getOrders(chainId, tokenAddress)
            _ordersFlow.value = data
            logger.info("Fetched ${data.size} orders for $tokenAddress on $chainId")
        } catch (e: Exception) {
            logger.error("Failed to refresh orders: ${e.message}", e)
        }
    }

    private suspend fun refreshPairsByChainAndPair(chainId: String, pairId: String) {
        rateLimiter.acquire(RateLimitCategory.Fast)
        try {
            val data = client.getPairsByChainAndPair(chainId, pairId)
            _pairsFlow.value = data
            logger.info("Fetched pairs for chainId=$chainId, pairId=$pairId")
        } catch (e: Exception) {
            logger.error("Failed to refresh pairs by chain and pair: ${e.message}", e)
        }
    }

    private suspend fun refreshPairsByToken(tokenAddresses: String) {
        rateLimiter.acquire(RateLimitCategory.Fast)
        try {
            val data = client.getPairsByToken(tokenAddresses)
            _pairsByTokenFlow.value = data
            logger.info("Fetched pairs for tokens=$tokenAddresses")
        } catch (e: Exception) {
            logger.error("Failed to refresh pairs by token: ${e.message}", e)
        }
    }

    private suspend fun refreshSearchPairs(query: String) {
        rateLimiter.acquire(RateLimitCategory.Fast)
        try {
            val data = client.searchPairs(query)
            _searchPairsFlow.value = data
            logger.info("Searched pairs with query=$query, found ${data.pairs?.size ?: 0}")
        } catch (e: Exception) {
            logger.error("Failed to search pairs: ${e.message}", e)
        }
    }

    fun startAutoRefreshTokenProfiles(intervalMs: Long = autoRefreshInterval) {
        tokenProfilesJob?.cancel()
        tokenProfilesJob = coroutineScope.launch {
            while (isActive) {
                refreshTokenProfiles()
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefreshTokenProfiles() {
        tokenProfilesJob?.cancel()
        tokenProfilesJob = null
    }

    fun startAutoRefreshLatestBoostedTokens(intervalMs: Long = autoRefreshInterval) {
        latestBoostedTokensJob?.cancel()
        latestBoostedTokensJob = coroutineScope.launch {
            while (isActive) {
                refreshLatestBoostedTokens()
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefreshLatestBoostedTokens() {
        latestBoostedTokensJob?.cancel()
        latestBoostedTokensJob = null
    }

    fun startAutoRefreshTopBoostedTokens(intervalMs: Long = autoRefreshInterval) {
        topBoostedTokensJob?.cancel()
        topBoostedTokensJob = coroutineScope.launch {
            while (isActive) {
                refreshTopBoostedTokens()
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefreshTopBoostedTokens() {
        topBoostedTokensJob?.cancel()
        topBoostedTokensJob = null
    }

    fun startAutoRefreshOrders(chainId: String, tokenAddress: String, intervalMs: Long = autoRefreshInterval) {
        ordersJob?.cancel()
        ordersJob = coroutineScope.launch {
            while (isActive) {
                refreshOrders(chainId, tokenAddress)
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefreshOrders() {
        ordersJob?.cancel()
        ordersJob = null
    }

    fun startAutoRefreshPairsByChainAndPair(chainId: String, pairId: String, intervalMs: Long = autoRefreshInterval) {
        pairsJob?.cancel()
        pairsJob = coroutineScope.launch {
            while (isActive) {
                refreshPairsByChainAndPair(chainId, pairId)
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefreshPairsByChainAndPair() {
        pairsJob?.cancel()
        pairsJob = null
    }

    fun startAutoRefreshPairsByToken(tokenAddresses: String, intervalMs: Long = autoRefreshInterval) {
        pairsByTokenJob?.cancel()
        pairsByTokenJob = coroutineScope.launch {
            while (isActive) {
                refreshPairsByToken(tokenAddresses)
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefreshPairsByToken() {
        pairsByTokenJob?.cancel()
        pairsByTokenJob = null
    }

    fun startAutoRefreshSearchPairs(query: String, intervalMs: Long = autoRefreshInterval) {
        searchPairsJob?.cancel()
        searchPairsJob = coroutineScope.launch {
            while (isActive) {
                refreshSearchPairs(query)
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefreshSearchPairs() {
        searchPairsJob?.cancel()
        searchPairsJob = null
    }

    fun stopAllAutoRefresh() {
        tokenProfilesJob?.cancel(); tokenProfilesJob = null
        latestBoostedTokensJob?.cancel(); latestBoostedTokensJob = null
        topBoostedTokensJob?.cancel(); topBoostedTokensJob = null
        ordersJob?.cancel(); ordersJob = null
        pairsJob?.cancel(); pairsJob = null
        pairsByTokenJob?.cancel(); pairsByTokenJob = null
        searchPairsJob?.cancel(); searchPairsJob = null
    }
}