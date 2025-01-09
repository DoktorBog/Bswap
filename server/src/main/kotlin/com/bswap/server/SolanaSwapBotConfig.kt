package com.bswap.server

import foundation.metaplex.solanapublickeys.PublicKey
import java.math.BigDecimal

data class SolanaSwapBotConfig(
    val walletPublicKey: PublicKey = PublicKey(""),
    val swapMint: PublicKey = PublicKey("So11111111111111111111111111111111111111112"),
    val solAmountToTrade: BigDecimal = BigDecimal("0.0007"),
    val autoSellAllSpl: Boolean = true,
    val maxKnownTokens: Int = 10,
    val sellWaitMs: Long = 65_000,
    val zeroBalanceCloseBatch: Int = 9,
    val splSellBatch: Int = 10,
    val closeAccountsIntervalMs: Long = 60_000,
    val sellAllSplIntervalMs: Long = 10_000,
    val clearMapIntervalMs: Long = 55_000 * 60,
    val useJito: Boolean = true,
)

const val privateKey: String =
    ""
