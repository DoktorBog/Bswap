package com.bswap.server.data

import com.bswap.server.LAMPORTS_PER_SOL
import java.math.BigDecimal
import java.math.RoundingMode

fun Long.formatLamports(): BigDecimal =
    BigDecimal.valueOf(this).divide(BigDecimal.valueOf(LAMPORTS_PER_SOL))

fun BigDecimal.formatLamports(): Long =
    this.multiply(BigDecimal.valueOf(LAMPORTS_PER_SOL)).longValueExact()

fun BigDecimal.toHumanReadable(decimals: Int): BigDecimal {
    return this.divide(BigDecimal.TEN.pow(decimals), decimals, RoundingMode.HALF_UP)
}

fun BigDecimal.toRawAmount(decimals: Int): Long {
    return this.multiply(BigDecimal.TEN.pow(decimals))
        .setScale(0, RoundingMode.HALF_UP) // Ensure no fractional values
        .toLong() // Convert to Long
}
