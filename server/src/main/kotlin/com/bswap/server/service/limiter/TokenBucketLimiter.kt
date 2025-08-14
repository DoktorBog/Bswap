package com.bswap.server.service.limiter

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Minute-based token bucket limiter.
 * Example: TokenBucketLimiter(ratePerMinute = 15, maxBurst = 3)
 */
class TokenBucketLimiter(
    private val ratePerMinute: Int,
    private val maxBurst: Int = ratePerMinute
) {
    private val mutex = Mutex()
    private var tokens: Double = maxBurst.toDouble()
    private var lastRefillNanos: Long = System.nanoTime()
    private val ratePerMs: Double = ratePerMinute / 60_000.0

    /**
     * Try to acquire one token within [timeoutMs]. Returns false if timed out.
     */
    suspend fun acquire(timeoutMs: Long = 1500L): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            if (tryTakeOne()) return true
            if (System.nanoTime() >= deadline) return false
            // ratePerMs = tokens per millisecond; inter-arrival ms = 1 / ratePerMs
            val sleep = max(10L, (1.0 / ratePerMs).toLong())
            delay(sleep)
        }
    }

    private suspend fun tryTakeOne(): Boolean = mutex.withLock {
        refill()
        return if (tokens >= 1.0) {
            tokens -= 1.0
            true
        } else false
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedMs = (now - lastRefillNanos) / 1_000_000.0
        if (elapsedMs <= 0) return
        val add = elapsedMs * ratePerMs
        if (add > 0) {
            tokens = min(maxBurst.toDouble(), tokens + add)
            lastRefillNanos = now
        }
    }
}

/** Simple retry with exponential backoff + jitter. */
suspend inline fun <T> retrying(
    attempts: Int = 2,
    initialDelayMs: Long = 200,
    maxDelayMs: Long = 2_000,
    crossinline block: suspend () -> T
): T {
    var delayMs = initialDelayMs
    var lastErr: Throwable? = null
        repeat(attempts) { i ->
        try {
            return block()
        } catch (e: Throwable) {
            lastErr = e
            if (i == attempts - 1) return@repeat
            val jitter = (delayMs * 0.25).toLong().coerceAtLeast(10)
            val sleep = (delayMs + Random.nextLong(0, jitter))
            delayMs = kotlin.math.min(maxDelayMs, (delayMs * 2).coerceAtLeast(50))
            kotlinx.coroutines.delay(sleep)
        }
    }
    throw lastErr ?: IllegalStateException("retrying() failed without throwable")
}
