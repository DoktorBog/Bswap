package com.bswap.server.core

import com.bswap.server.PriceServiceConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks price misses for tokens and determines when to force sell due to unavailable price data
 */
class PriceMissTracker(
    private val config: PriceServiceConfig
) {
    private val log = LoggerFactory.getLogger("PriceMissTracker")
    
    // Track price miss counts per token
    private val priceMisses = ConcurrentHashMap<String, PriceMissData>()
    
    data class PriceMissData(
        var consecutiveMisses: Int = 0,
        var firstMissTime: Long = System.currentTimeMillis(),
        var lastMissTime: Long = System.currentTimeMillis()
    )
    
    /**
     * Record a price miss for a token
     */
    fun recordPriceMiss(mint: String) {
        val now = System.currentTimeMillis()
        
        priceMisses.compute(mint) { _, existing ->
            if (existing == null) {
                PriceMissData(consecutiveMisses = 1, firstMissTime = now, lastMissTime = now)
            } else {
                // Check if this miss is within the configured window
                val timeSinceFirst = now - existing.firstMissTime
                if (timeSinceFirst > config.priceMissingWindowMs) {
                    // Reset the window
                    PriceMissData(consecutiveMisses = 1, firstMissTime = now, lastMissTime = now)
                } else {
                    // Increment within the window
                    existing.consecutiveMisses += 1
                    existing.lastMissTime = now
                    existing
                }
            }
        }
    }
    
    /**
     * Record a successful price fetch for a token (resets miss count)
     */
    fun recordPriceSuccess(mint: String) {
        priceMisses.remove(mint)
    }
    
    /**
     * Check if a token should be force-sold due to too many price misses
     */
    fun shouldForceSell(mint: String): Boolean {
        if (!config.sellOnPriceMissing) return false
        
        val missData = priceMisses[mint] ?: return false
        
        val now = System.currentTimeMillis()
        val timeSinceFirst = now - missData.firstMissTime
        
        // Check if we're still within the window and have exceeded max strikes
        val withinWindow = timeSinceFirst <= config.priceMissingWindowMs
        val exceedsMaxStrikes = missData.consecutiveMisses >= config.priceMissingMaxStrikes
        
        val shouldSell = withinWindow && exceedsMaxStrikes
        
        if (shouldSell) {
            log.warn("Force sell triggered for $mint: ${missData.consecutiveMisses} misses in ${timeSinceFirst}ms")
        }
        
        return shouldSell
    }
    
    /**
     * Get current miss count for a token
     */
    fun getMissCount(mint: String): Int {
        return priceMisses[mint]?.consecutiveMisses ?: 0
    }
    
    /**
     * Get time since first miss for a token
     */
    fun getTimeSinceFirstMiss(mint: String): Long? {
        val missData = priceMisses[mint] ?: return null
        return System.currentTimeMillis() - missData.firstMissTime
    }
    
    /**
     * Clean up old entries that are outside the window
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        priceMisses.forEach { (mint, missData) ->
            val timeSinceFirst = now - missData.firstMissTime
            if (timeSinceFirst > config.priceMissingWindowMs * 2) { // Give some buffer
                toRemove.add(mint)
            }
        }
        
        toRemove.forEach { mint ->
            priceMisses.remove(mint)
        }
        
        if (toRemove.isNotEmpty()) {
            log.debug("Cleaned up ${toRemove.size} old price miss entries")
        }
    }
    
    /**
     * Get statistics about current price misses
     */
    fun getStats(): Map<String, Any> {
        val now = System.currentTimeMillis()
        
        return mapOf(
            "trackedTokens" to priceMisses.size,
            "maxStrikes" to config.priceMissingMaxStrikes,
            "windowMs" to config.priceMissingWindowMs,
            "sellOnMissing" to config.sellOnPriceMissing,
            "currentMisses" to priceMisses.mapValues { (_, missData) ->
                mapOf(
                    "consecutiveMisses" to missData.consecutiveMisses,
                    "timeSinceFirstMs" to (now - missData.firstMissTime),
                    "timeSinceLastMs" to (now - missData.lastMissTime)
                )
            }
        )
    }
}