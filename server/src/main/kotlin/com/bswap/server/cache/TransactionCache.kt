package com.bswap.server.cache

import com.bswap.shared.model.SolanaTx
import com.bswap.shared.model.HistoryPage
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

data class WalletCache(
    val transactions: MutableList<SolanaTx> = mutableListOf(),
    var isFullyFetched: Boolean = false,
    var lastCursor: String? = null,
    var lastUpdateTime: Long = System.currentTimeMillis(),
    var fetchJob: Job? = null
)

class TransactionCache(
    private val solanaRpcClient: com.bswap.server.data.solana.rpc.SolanaRpcClient,
    private val enableLogging: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<String, WalletCache>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        const val FIRST_PAGE_SIZE = 50
        const val BACKGROUND_BATCH_SIZE = 100
        const val MAX_CACHED_TRANSACTIONS = 1000
        const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Get first page immediately - always fast response
     * If cache exists - return from cache
     * If no cache - return empty page and start background fetching
     * If fetching already in progress - return what's available in cache
     */
    suspend fun getFirstPage(publicKey: String, silent: Boolean = false): HistoryPage {
        if (!silent && enableLogging) {
            logger.info("⚡ TransactionCache: Fast first page request for $publicKey")
        }
        
        val walletCache = cache.getOrPut(publicKey) { WalletCache() }
        
        // If we have cached transactions (regardless of age), return them immediately
        if (walletCache.transactions.isNotEmpty()) {
            if (!silent && enableLogging) {
                logger.info("⚡ TransactionCache: Returning cached first page INSTANTLY (${walletCache.transactions.take(FIRST_PAGE_SIZE).size} transactions)")
            }
            
            val firstPage = walletCache.transactions.take(FIRST_PAGE_SIZE)
            val hasMore = walletCache.transactions.size > FIRST_PAGE_SIZE || !walletCache.isFullyFetched
            
            // If cache is expired and no fetching is in progress, start background refresh
            if (System.currentTimeMillis() - walletCache.lastUpdateTime > CACHE_EXPIRY_MS && 
                walletCache.fetchJob?.isActive != true) {
                if (!silent && enableLogging) {
                    logger.info("⚡ TransactionCache: Cache expired, starting background refresh")
                }
                startBackgroundFetching(publicKey, silent)
            }
            
            return HistoryPage(firstPage, if (hasMore) "page_1" else null)
        }
        
        // If fetching is already in progress, wait longer for some results
        if (walletCache.fetchJob?.isActive == true) {
            if (!silent && enableLogging) {
                logger.info("⚡ TransactionCache: Fetching in progress, waiting for first results...")
            }
            
            // Wait max 5 seconds for some results - RPC is working so we should get data
            val startTime = System.currentTimeMillis()
            while (walletCache.transactions.isEmpty() && 
                   walletCache.fetchJob?.isActive == true && 
                   System.currentTimeMillis() - startTime < 5000) {
                delay(200) // Check every 200ms
            }
            
            if (walletCache.transactions.isNotEmpty()) {
                if (!silent && enableLogging) {
                    logger.info("⚡ TransactionCache: Got results from ongoing fetch: ${walletCache.transactions.size} transactions")
                }
                val firstPage = walletCache.transactions.take(FIRST_PAGE_SIZE)
                val hasMore = walletCache.transactions.size > FIRST_PAGE_SIZE || !walletCache.isFullyFetched
                return HistoryPage(firstPage, if (hasMore) "page_1" else null)
            } else {
                if (!silent && enableLogging) {
                    logger.warn("⚡ TransactionCache: Still no results after 5 seconds, returning empty with cursor")
                }
            }
        }
        
        // No cache, no ongoing fetch - start background fetching and return empty page
        if (!silent && enableLogging) {
            logger.info("⚡ TransactionCache: No cache available, starting background fetch, returning empty page INSTANTLY")
        }
        
        if (walletCache.fetchJob?.isActive != true) {
            startBackgroundFetching(publicKey, silent)
        }
        
        // Return empty page with cursor to indicate more data is coming
        return HistoryPage(emptyList(), "page_1")
    }
    
    /**
     * Get specific page from cache (for pagination) - always fast
     */
    fun getPage(publicKey: String, pageIndex: Int, pageSize: Int = FIRST_PAGE_SIZE): HistoryPage {
        logger.info("⚡ TransactionCache: Fast page request $pageIndex for $publicKey")
        
        val walletCache = cache[publicKey] ?: run {
            logger.info("⚡ TransactionCache: No cache found for $publicKey, starting background fetch")
            // Start background fetch for this wallet
            scope.launch { startBackgroundFetching(publicKey) }
            return HistoryPage(emptyList(), "page_${pageIndex + 1}")
        }
        
        val startIndex = pageIndex * pageSize
        val endIndex = minOf(startIndex + pageSize, walletCache.transactions.size)
        
        if (startIndex >= walletCache.transactions.size) {
            // Requested page is beyond what we have cached
            if (walletCache.isFullyFetched) {
                logger.info("⚡ TransactionCache: Page $pageIndex beyond all transactions (fully fetched)")
                return HistoryPage(emptyList(), null)
            } else {
                logger.info("⚡ TransactionCache: Page $pageIndex beyond cached, but more may exist")
                // Start background fetch if not running
                if (walletCache.fetchJob?.isActive != true) {
                    scope.launch { startBackgroundFetching(publicKey) }
                }
                return HistoryPage(emptyList(), "page_${pageIndex + 1}")
            }
        }
        
        val pageTransactions = walletCache.transactions.subList(startIndex, endIndex)
        val hasMore = endIndex < walletCache.transactions.size || !walletCache.isFullyFetched
        
        logger.info("⚡ TransactionCache: Returning page $pageIndex with ${pageTransactions.size} transactions INSTANTLY")
        
        return HistoryPage(
            pageTransactions,
            if (hasMore) "page_${pageIndex + 1}" else null
        )
    }
    
    /**
     * Start background fetching of remaining pages
     */
    private fun startBackgroundFetching(publicKey: String, silent: Boolean = false) {
        val walletCache = cache.getOrPut(publicKey) { WalletCache() }
        
        // Cancel existing fetch job if any
        walletCache.fetchJob?.cancel()
        
        walletCache.fetchJob = scope.launch {
            if (!silent && enableLogging) {
                logger.info("🔄 TransactionCache: Starting background fetch for $publicKey")
                logger.info("🔄 TransactionCache: Initial state - cached: ${walletCache.transactions.size}, lastCursor: ${walletCache.lastCursor}")
            }
            
            var cursor = walletCache.lastCursor
            var pageCount = if (walletCache.transactions.isEmpty()) 1 else 2 // Start from page 1 if no cache
            
            try {
                // Always try to fetch at least one page, even if cursor is null
                var shouldContinue = true
                var fetchCount = 0
                
                while (shouldContinue && 
                       walletCache.transactions.size < MAX_CACHED_TRANSACTIONS && 
                       !walletCache.isFullyFetched &&
                       isActive &&
                       fetchCount < 10) { // Max 10 pages to prevent infinite loops
                    
                    fetchCount++
                    logger.info("🔄 TransactionCache: Attempting fetch $fetchCount/10, page $pageCount with cursor: $cursor")
                    logger.info("🔄 TransactionCache: Using publicKey: $publicKey, batchSize: $BACKGROUND_BATCH_SIZE")
                    
                    val page = try {
                        solanaRpcClient.getHistory(publicKey, BACKGROUND_BATCH_SIZE, cursor)
                    } catch (e: Exception) {
                        logger.error("🔄 TransactionCache: RPC error on fetch $fetchCount", e)
                        // Try without cursor on first attempt if cursor fails
                        if (fetchCount == 1 && cursor != null) {
                            logger.info("🔄 TransactionCache: Retrying without cursor")
                            try {
                                solanaRpcClient.getHistory(publicKey, BACKGROUND_BATCH_SIZE, null)
                            } catch (e2: Exception) {
                                logger.error("🔄 TransactionCache: RPC failed even without cursor", e2)
                                break
                            }
                        } else {
                            break
                        }
                    }
                    
                    logger.info("🔄 TransactionCache: Received page with ${page.transactions.size} transactions, nextCursor: ${page.nextCursor}")
                    
                    if (page.transactions.isEmpty()) {
                        logger.info("🔄 TransactionCache: Empty page received, marking as fully fetched")
                        walletCache.isFullyFetched = true
                        shouldContinue = false
                    } else {
                        // Add new transactions to cache
                        val sizeBefore = walletCache.transactions.size
                        walletCache.transactions.addAll(page.transactions)
                        walletCache.lastCursor = page.nextCursor
                        walletCache.lastUpdateTime = System.currentTimeMillis()
                        cursor = page.nextCursor
                        pageCount++
                        
                        logger.info("🔄 TransactionCache: Added ${page.transactions.size} transactions (${sizeBefore} -> ${walletCache.transactions.size})")
                        
                        if (cursor == null) {
                            logger.info("🔄 TransactionCache: No more cursor, marking as fully fetched")
                            walletCache.isFullyFetched = true
                            shouldContinue = false
                        }
                    }
                    
                    // Small delay to avoid overwhelming the RPC
                    if (shouldContinue) {
                        delay(300)
                    }
                }
                
                logger.info("🔄 TransactionCache: Background fetching completed for $publicKey")
                logger.info("🔄 TransactionCache: Final state - total: ${walletCache.transactions.size}, isFullyFetched: ${walletCache.isFullyFetched}, fetchCount: $fetchCount")
                
            } catch (e: Exception) {
                if (e is CancellationException) {
                    logger.info("🔄 TransactionCache: Background fetching cancelled for $publicKey")
                } else {
                    logger.error("🔄 TransactionCache: Error during background fetching for $publicKey", e)
                }
            }
        }
    }
    
    /**
     * Clear cache for specific wallet
     */
    fun clearCache(publicKey: String) {
        logger.info("🗑️ TransactionCache: Clearing cache for $publicKey")
        cache[publicKey]?.let { walletCache ->
            walletCache.fetchJob?.cancel()
            cache.remove(publicKey)
        }
    }
    
    /**
     * Clear all expired cache entries
     */
    fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = cache.entries
            .filter { currentTime - it.value.lastUpdateTime > CACHE_EXPIRY_MS }
            .map { it.key }
            
        expiredKeys.forEach { key ->
            logger.info("🗑️ TransactionCache: Removing expired cache for $key")
            clearCache(key)
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(publicKey: String): Map<String, Any> {
        val walletCache = cache[publicKey] ?: return mapOf("cached" to false)
        
        return mapOf(
            "cached" to true,
            "transactionCount" to walletCache.transactions.size,
            "isFullyFetched" to walletCache.isFullyFetched,
            "lastUpdateTime" to walletCache.lastUpdateTime,
            "isBackgroundFetching" to (walletCache.fetchJob?.isActive == true)
        )
    }
}