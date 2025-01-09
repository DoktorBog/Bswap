package com.bswap.server

import foundation.metaplex.solanapublickeys.PublicKey
import java.math.BigDecimal

data class SolanaSwapBotConfig(
    val walletPublicKey: PublicKey = PublicKey(""),
    val swapMint: PublicKey = PublicKey("So11111111111111111111111111111111111111112"),
    val solAmountToTrade: BigDecimal = BigDecimal("0.0005"),
    val autoSellAllSpl: Boolean = true,
    val maxKnownTokens: Int = 8,
    val sellWaitMs: Long = 20_000,
    val zeroBalanceCloseBatch: Int = 5,
    val splSellBatch: Int = 8,
    val closeAccountsIntervalMs: Long = 120_000,
    val sellAllSplIntervalMs: Long = 15_000,
    val clearMapIntervalMs: Long = 60_000 * 60,
    val useJito: Boolean = true
)

const val privateKey: String =
    ""