package com.bswap.server.service

import com.bswap.server.data.whitelist.CoinWhitelist
import com.bswap.server.data.whitelist.CoinWhitelistSource
import com.bswap.server.data.whitelist.WhitelistCoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the coin whitelist for trading operations.
 * Integrates with Jupiter tokens to resolve mint addresses and provides
 * observable updates for trading systems.
 */
class CoinWhitelistManager(
    private val jupiterTokensClient: JupiterTokensClient,
    val whitelistSource: CoinWhitelistSource = CoinWhitelistSource()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val resolvedMintsRef = AtomicReference<Map<String, String>>(emptyMap())
    
    /**
     * Observable whitelist that updates in real-time
     */
    val whitelist: StateFlow<CoinWhitelist> = whitelistSource.whitelist
    
    /**
     * Observable set of coin symbols currently being monitored
     */
    val observedCoins: StateFlow<Set<String>> = whitelistSource.observedCoins
    
    /**
     * Refresh the whitelist and resolve mint addresses from Jupiter
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            logger.info("Refreshing coin whitelist...")
            
            // Get verified tokens from Jupiter
            val verifiedTokens = jupiterTokensClient.getVerifiedTokens()
            logger.info("Retrieved ${verifiedTokens.size} verified tokens from Jupiter")
            
            // Update mint addresses for existing whitelist coins
            val currentWhitelist = whitelistSource.whitelist.value
            val updatedCoins = currentWhitelist.coins.map { coin ->
                val jupiterToken = verifiedTokens.firstOrNull { 
                    it.symbol.equals(coin.symbol, ignoreCase = true) 
                }
                
                if (jupiterToken != null) {
                    coin.copy(
                        mint = jupiterToken.address,
                        name = jupiterToken.name ?: coin.name
                    )
                } else {
                    logger.warn("Token ${coin.symbol} not found in Jupiter verified list")
                    coin
                }
            }
            
            // Update the whitelist
            whitelistSource.updateWhitelist(
                currentWhitelist.copy(coins = updatedCoins)
            )
            
            // Cache resolved mints for quick lookup
            val mintMap = updatedCoins.mapNotNull { coin ->
                coin.mint?.let { mint -> coin.symbol to mint }
            }.toMap()
            resolvedMintsRef.set(mintMap)
            
            val resolvedCount = updatedCoins.count { it.mint != null }
            val enabledCount = updatedCoins.count { it.enabled }
            
            logger.info("Whitelist refreshed: ${updatedCoins.size} total coins, $resolvedCount resolved, $enabledCount enabled")
            
        } catch (e: Exception) {
            logger.error("Failed to refresh coin whitelist: ${e.message}", e)
        }
    }
    
    /**
     * Add a new coin to the whitelist
     */
    suspend fun addCoin(
        symbol: String,
        priority: Int = 0,
        enabled: Boolean = true,
        tags: List<String> = emptyList()
    ) {
        try {
            // Try to resolve mint address
            val verifiedTokens = jupiterTokensClient.getVerifiedTokens()
            val jupiterToken = verifiedTokens.firstOrNull { 
                it.symbol.equals(symbol, ignoreCase = true) 
            }
            
            val coin = WhitelistCoin(
                symbol = symbol,
                mint = jupiterToken?.address,
                name = jupiterToken?.name,
                priority = priority,
                enabled = enabled,
                tags = tags
            )
            
            whitelistSource.updateCoin(coin)
            
            if (jupiterToken != null) {
                logger.info("Added coin $symbol with mint ${jupiterToken.address}")
            } else {
                logger.warn("Added coin $symbol but could not resolve mint address")
            }
            
        } catch (e: Exception) {
            logger.error("Failed to add coin $symbol: ${e.message}", e)
        }
    }
    
    /**
     * Remove a coin from the whitelist
     */
    fun removeCoin(symbol: String) {
        whitelistSource.removeCoin(symbol)
        logger.info("Removed coin $symbol from whitelist")
    }
    
    /**
     * Enable or disable a coin for trading
     */
    fun setCoinEnabled(symbol: String, enabled: Boolean) {
        whitelistSource.setCoinEnabled(symbol, enabled)
        logger.info("Set coin $symbol enabled=$enabled")
    }
    
    /**
     * Get all enabled coins sorted by priority
     */
    fun getEnabledCoins(): List<WhitelistCoin> {
        return whitelistSource.getEnabledCoins()
    }
    
    /**
     * Get coins by tags
     */
    fun getCoinsByTags(tags: Set<String>): List<WhitelistCoin> {
        return whitelistSource.getEnabledCoins().filter { coin ->
            tags.any { tag -> coin.tags.contains(tag) }
        }
    }
    
    /**
     * Check if a symbol is whitelisted and enabled
     */
    fun isSymbolAllowed(symbol: String): Boolean {
        return whitelistSource.isSymbolAllowed(symbol)
    }
    
    /**
     * Check if a mint address is whitelisted and enabled
     */
    fun isMintAllowed(mint: String): Boolean {
        return whitelistSource.isMintAllowed(mint)
    }
    
    /**
     * Get mint address for a symbol
     */
    fun getMintForSymbol(symbol: String): String? {
        return resolvedMintsRef.get()[symbol.uppercase()]
    }
    
    /**
     * Get all resolved mint addresses
     */
    fun getAllowedMints(): Set<String> {
        return whitelistSource.getEnabledCoins()
            .mapNotNull { it.mint }
            .toSet()
    }
    
    /**
     * Get whitelist statistics
     */
    fun getStats(): Map<String, Any> {
        val coins = whitelistSource.getEnabledCoins()
        val byTags = coins.groupBy { it.tags }.mapValues { it.value.size }
        
        return mapOf(
            "totalCoins" to whitelistSource.whitelist.value.coins.size,
            "enabledCoins" to coins.size,
            "resolvedMints" to coins.count { it.mint != null },
            "coinsByTags" to byTags,
            "highPriorityCoins" to coins.count { it.priority >= 50 },
            "lastUpdated" to whitelistSource.whitelist.value.lastUpdated
        )
    }
}