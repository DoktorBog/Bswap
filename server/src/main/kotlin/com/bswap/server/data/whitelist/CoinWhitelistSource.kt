package com.bswap.server.data.whitelist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class WhitelistCoin(
    val symbol: String,
    val mint: String? = null,
    val name: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val tags: List<String> = emptyList()
)

@Serializable
data class CoinWhitelist(
    val version: String = "1.0",
    val lastUpdated: Long = System.currentTimeMillis(),
    val coins: List<WhitelistCoin> = emptyList()
)

/**
 * Observable coin whitelist source for trading operations.
 * Provides real-time updates when the whitelist changes.
 */
class CoinWhitelistSource(
    private val loader: CoinWhitelistLoader = CoinWhitelistLoader()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    private val _whitelist = MutableStateFlow(loadInitialWhitelist())
    val whitelist: StateFlow<CoinWhitelist> = _whitelist.asStateFlow()
    
    private val _observedCoins = MutableStateFlow<Set<String>>(emptySet())
    val observedCoins: StateFlow<Set<String>> = _observedCoins.asStateFlow()
    
    /**
     * Get currently enabled coins for trading observation
     */
    fun getEnabledCoins(): List<WhitelistCoin> {
        return _whitelist.value.coins
            .filter { it.enabled }
            .sortedByDescending { it.priority }
    }
    
    /**
     * Get coins by symbol
     */
    fun getCoinsBySymbol(symbols: Set<String>): List<WhitelistCoin> {
        return _whitelist.value.coins.filter { coin ->
            symbols.any { it.equals(coin.symbol, ignoreCase = true) }
        }
    }
    
    /**
     * Get coins by mint address
     */
    fun getCoinsByMint(mints: Set<String>): List<WhitelistCoin> {
        return _whitelist.value.coins.filter { coin ->
            coin.mint != null && mints.contains(coin.mint)
        }
    }
    
    /**
     * Update the entire whitelist
     */
    fun updateWhitelist(newWhitelist: CoinWhitelist) {
        _whitelist.value = newWhitelist.copy(lastUpdated = System.currentTimeMillis())
        updateObservedCoins()
        logger.info("Whitelist updated with ${newWhitelist.coins.size} coins, ${getEnabledCoins().size} enabled")
    }
    
    /**
     * Add or update a single coin
     */
    fun updateCoin(coin: WhitelistCoin) {
        val current = _whitelist.value
        val updatedCoins = current.coins.toMutableList()
        
        val existingIndex = updatedCoins.indexOfFirst { 
            it.symbol.equals(coin.symbol, ignoreCase = true) 
        }
        
        if (existingIndex >= 0) {
            updatedCoins[existingIndex] = coin
        } else {
            updatedCoins.add(coin)
        }
        
        updateWhitelist(current.copy(coins = updatedCoins))
    }
    
    /**
     * Remove a coin by symbol
     */
    fun removeCoin(symbol: String) {
        val current = _whitelist.value
        val updatedCoins = current.coins.filterNot { 
            it.symbol.equals(symbol, ignoreCase = true) 
        }
        updateWhitelist(current.copy(coins = updatedCoins))
    }
    
    /**
     * Enable/disable a coin for trading
     */
    fun setCoinEnabled(symbol: String, enabled: Boolean) {
        val current = _whitelist.value
        val updatedCoins = current.coins.map { coin ->
            if (coin.symbol.equals(symbol, ignoreCase = true)) {
                coin.copy(enabled = enabled)
            } else {
                coin
            }
        }
        updateWhitelist(current.copy(coins = updatedCoins))
    }
    
    /**
     * Check if a coin symbol is whitelisted and enabled
     */
    fun isSymbolAllowed(symbol: String): Boolean {
        return _whitelist.value.coins.any { 
            it.symbol.equals(symbol, ignoreCase = true) && it.enabled 
        }
    }
    
    /**
     * Check if a mint address is whitelisted and enabled
     */
    fun isMintAllowed(mint: String): Boolean {
        return _whitelist.value.coins.any { 
            it.mint == mint && it.enabled 
        }
    }
    
    private fun updateObservedCoins() {
        val enabledSymbols = getEnabledCoins().map { it.symbol }.toSet()
        _observedCoins.value = enabledSymbols
    }
    
    /**
     * Load whitelist from configuration or fallback to default
     */
    private fun loadInitialWhitelist(): CoinWhitelist {
        val configPath = System.getenv("COIN_WHITELIST_PATH")
        val loaded = loader.loadWithFallback(configPath)
        
        return if (loaded != null) {
            logger.info("Loaded whitelist configuration with ${loaded.coins.size} coins")
            loaded
        } else {
            logger.info("Using default whitelist configuration")
            getDefaultWhitelist()
        }
    }
    
    /**
     * Reload whitelist from configuration
     */
    fun reloadFromConfig(): Boolean {
        return try {
            val configPath = System.getenv("COIN_WHITELIST_PATH")
            val loaded = loader.loadWithFallback(configPath)
            
            if (loaded != null) {
                updateWhitelist(loaded)
                logger.info("Reloaded whitelist from configuration")
                true
            } else {
                logger.warn("Failed to reload whitelist from configuration")
                false
            }
        } catch (e: Exception) {
            logger.error("Error reloading whitelist: ${e.message}", e)
            false
        }
    }
    
    /**
     * Save current whitelist to file
     */
    fun saveToFile(filePath: String): Boolean {
        return loader.saveToFile(_whitelist.value, filePath)
    }
    
    private fun getDefaultWhitelist(): CoinWhitelist {
        return CoinWhitelist(
            version = "1.0",
            coins = listOf(
                // Major liquid tokens - High priority
                WhitelistCoin("SOL", priority = 100, tags = listOf("native", "high-liquidity")),
                WhitelistCoin("USDC", priority = 95, tags = listOf("stablecoin", "high-liquidity")),
                WhitelistCoin("USDT", priority = 90, tags = listOf("stablecoin", "high-liquidity")),
                
                // DEX and ecosystem tokens - High priority
                WhitelistCoin("JUP", priority = 85, tags = listOf("dex", "ecosystem")),
                WhitelistCoin("RAY", priority = 80, tags = listOf("dex", "ecosystem")),
                WhitelistCoin("ORCA", priority = 75, tags = listOf("dex", "ecosystem")),
                
                // Oracle and infrastructure - Medium-high priority
                WhitelistCoin("PYTH", priority = 70, tags = listOf("oracle", "infrastructure")),
                WhitelistCoin("JTO", priority = 65, tags = listOf("infrastructure")),
                
                // Liquid staking tokens - Medium priority
                WhitelistCoin("mSOL", priority = 60, tags = listOf("lst", "defi")),
                WhitelistCoin("bSOL", priority = 55, tags = listOf("lst", "defi")),
                WhitelistCoin("jitoSOL", priority = 50, tags = listOf("lst", "defi")),
                
                // Large meme coins with good liquidity - Medium priority
                WhitelistCoin("BONK", priority = 45, tags = listOf("meme", "community")),
                WhitelistCoin("WIF", priority = 40, tags = listOf("meme", "community")),
                WhitelistCoin("POPCAT", priority = 35, tags = listOf("meme", "community")),
                
                // Other ecosystem tokens - Lower priority
                WhitelistCoin("TNSR", priority = 30, tags = listOf("nft", "marketplace")),
                WhitelistCoin("HNT", priority = 25, tags = listOf("iot", "infrastructure")),
                WhitelistCoin("WEN", priority = 20, tags = listOf("meme", "community")),
                WhitelistCoin("SAMO", priority = 15, tags = listOf("meme", "community")),
                
                // Additional high-volume tokens - Variable priority based on market conditions
                WhitelistCoin("DRIFT", priority = 10, enabled = false, tags = listOf("defi", "derivatives")),
                WhitelistCoin("ZEUS", priority = 5, enabled = false, tags = listOf("meme")),
                WhitelistCoin("BOOK", priority = 5, enabled = false, tags = listOf("meme"))
            )
        )
    }
}