package com.bswap.server.data.whitelist

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Loads coin whitelist from various sources (JSON file, resources, etc.)
 */
class CoinWhitelistLoader {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Load whitelist from a JSON file
     */
    fun loadFromFile(filePath: String): CoinWhitelist? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                logger.warn("Whitelist file not found: $filePath")
                return null
            }
            
            val content = file.readText()
            val whitelist = json.decodeFromString<CoinWhitelist>(content)
            logger.info("Loaded whitelist from $filePath: ${whitelist.coins.size} coins")
            whitelist
            
        } catch (e: Exception) {
            logger.error("Failed to load whitelist from file $filePath: ${e.message}", e)
            null
        }
    }
    
    /**
     * Load whitelist from classpath resources
     */
    fun loadFromResources(resourcePath: String = "coin-whitelist.json"): CoinWhitelist? {
        return try {
            val resource = this::class.java.classLoader.getResourceAsStream(resourcePath)
            if (resource == null) {
                logger.warn("Whitelist resource not found: $resourcePath")
                return null
            }
            
            val content = resource.bufferedReader().use { it.readText() }
            val whitelist = json.decodeFromString<CoinWhitelist>(content)
            logger.info("Loaded whitelist from resources $resourcePath: ${whitelist.coins.size} coins")
            whitelist
            
        } catch (e: Exception) {
            logger.error("Failed to load whitelist from resources $resourcePath: ${e.message}", e)
            null
        }
    }
    
    /**
     * Save whitelist to a JSON file
     */
    fun saveToFile(whitelist: CoinWhitelist, filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            
            val content = json.encodeToString(CoinWhitelist.serializer(), whitelist)
            file.writeText(content)
            
            logger.info("Saved whitelist to $filePath: ${whitelist.coins.size} coins")
            true
            
        } catch (e: Exception) {
            logger.error("Failed to save whitelist to file $filePath: ${e.message}", e)
            false
        }
    }
    
    /**
     * Load whitelist with fallback strategy:
     * 1. Try loading from external file
     * 2. Fall back to classpath resources
     * 3. Return null if both fail
     */
    fun loadWithFallback(
        externalFilePath: String? = null,
        resourcePath: String = "coin-whitelist.json"
    ): CoinWhitelist? {
        // Try external file first if provided
        externalFilePath?.let { path ->
            loadFromFile(path)?.let { return it }
        }
        
        // Fall back to resources
        return loadFromResources(resourcePath)
    }
    
    /**
     * Create a whitelist from a simple list of symbols
     */
    fun createFromSymbols(
        symbols: List<String>,
        defaultPriority: Int = 50,
        defaultEnabled: Boolean = true,
        defaultTags: List<String> = emptyList()
    ): CoinWhitelist {
        val coins = symbols.mapIndexed { index, symbol ->
            WhitelistCoin(
                symbol = symbol.uppercase(),
                priority = defaultPriority - index, // Higher priority for earlier symbols
                enabled = defaultEnabled,
                tags = defaultTags
            )
        }
        
        return CoinWhitelist(
            version = "1.0",
            lastUpdated = System.currentTimeMillis(),
            coins = coins
        )
    }
}