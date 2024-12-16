package com.bswap.server.data

import java.math.BigDecimal

fun Long.formatLamports(): BigDecimal =
    BigDecimal.valueOf(this).divide(BigDecimal.valueOf(LAMPORTS_PER_SOL))
fun BigDecimal.formatLamports(): Long =
    this.multiply(BigDecimal.valueOf(LAMPORTS_PER_SOL)).longValueExact()