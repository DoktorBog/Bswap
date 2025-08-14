package com.bswap.addon

import kotlin.math.sqrt

internal fun sma(values: List<Double>, period: Int): Double? {
    if (period <= 0 || values.size < period) return null
    var s = 0.0
    val start = values.size - period
    for (i in start until values.size) s += values[i]
    return s / period
}

internal fun rsi(closes: List<Double>, period: Int): Double? {
    val logger = org.slf4j.LoggerFactory.getLogger("RSI-Calculator")
    
    // Enhanced validation for insufficient data
    if (period <= 1) {
        logger.warn("🔢 RSI CALC: Invalid period=$period (must be > 1)")
        return null
    }
    
    if (closes.size <= period) {
        logger.warn("🔢 RSI CALC: Insufficient data - DataPoints=${closes.size}, RequiredPeriod=$period")
        
        // For very small datasets, return neutral RSI
        if (closes.size >= 3) {
            logger.info("🔧 RSI FALLBACK: Using simplified calculation for ${closes.size} points")
            // Simple fallback: if last price > average of previous prices, RSI > 50
            val lastPrice = closes.last()
            val avgPrice = closes.dropLast(1).average()
            return if (lastPrice > avgPrice) 65.0 else 35.0
        }
        
        return null
    }
    
    var gain = 0.0
    var loss = 0.0
    for (i in 1..period) {
        val d = closes[i] - closes[i - 1]
        if (d >= 0) gain += d else loss -= d
    }
    gain /= period
    loss /= period
    for (i in period + 1 until closes.size) {
        val d = closes[i] - closes[i - 1]
        val g = if (d > 0) d else 0.0
        val l = if (d < 0) -d else 0.0
        gain = (gain * (period - 1) + g) / period
        loss = (loss * (period - 1) + l) / period
    }
    if (loss == 0.0) return 100.0
    val rs = gain / loss
    val rsiValue = 100.0 - 100.0 / (1.0 + rs)
    
    // Log RSI calculation details for debugging
    logger.debug("🔢 RSI CALC: Period=$period, DataPoints=${closes.size}, AvgGain=${"%.6f".format(gain)}, AvgLoss=${"%.6f".format(loss)}, RS=${"%.4f".format(rs)}, RSI=${"%.2f".format(rsiValue)}")
    
    return rsiValue
}

private fun stddev(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val m = values.sum() / values.size
    var s = 0.0
    for (v in values) {
        val d = v - m
        s += d * d
    }
    return sqrt(s / values.size)
}

internal data class BB(val middle: Double, val upper: Double, val lower: Double)

internal fun bollinger(closes: List<Double>, period: Int, dev: Double): BB? {
    if (period <= 1 || closes.size < period) return null
    val start = closes.size - period
    var sum = 0.0
    for (i in start until closes.size) sum += closes[i]
    val m = sum / period
    val window = ArrayList<Double>(period)
    for (i in start until closes.size) window.add(closes[i])
    val sd = stddev(window)
    return BB(m, m + dev * sd, m - dev * sd)
}

internal fun donchianHigh(values: List<Double>, lookback: Int): Double? {
    if (lookback <= 0 || values.size < lookback) return null
    var h = Double.NEGATIVE_INFINITY
    val start = values.size - lookback
    for (i in start until values.size) if (values[i] > h) h = values[i]
    return h
}

internal fun donchianLow(values: List<Double>, lookback: Int): Double? {
    if (lookback <= 0 || values.size < lookback) return null
    var l = Double.POSITIVE_INFINITY
    val start = values.size - lookback
    for (i in start until values.size) if (values[i] < l) l = values[i]
    return l
}

internal fun roc(closes: List<Double>, period: Int): Double? {
    if (period <= 0 || closes.size <= period) return null
    val prev = closes[closes.size - period - 1]
    if (prev == 0.0) return null
    val curr = closes.last()
    return (curr - prev) / prev
}
