package com.bswap.server.data.dexscreener

import kotlinx.coroutines.delay

sealed class RateLimitCategory {
    data object Standard : RateLimitCategory()
    data object Fast : RateLimitCategory()
}

class DexScreenerRateLimiter {

    private val categoryLimits = mapOf(
        RateLimitCategory.Standard to 60,
        RateLimitCategory.Fast to 300
    )

    private val nextAllowedTime = mutableMapOf<RateLimitCategory, Long>()

    suspend fun acquire(category: RateLimitCategory) {
        val currentTime = System.currentTimeMillis()
        val requestsPerMin = categoryLimits[category] ?: 60
        val minIntervalMs = (60_000L / requestsPerMin)

        val allowedTime = nextAllowedTime[category] ?: currentTime

        if (allowedTime > currentTime) {
            val waitTime = allowedTime - currentTime
            delay(waitTime)
        }

        nextAllowedTime[category] = System.currentTimeMillis() + minIntervalMs
    }
}