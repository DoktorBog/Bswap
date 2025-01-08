package com.bswap.server

import com.bswap.server.data.solana.swap.jupiter.JupiterSwapService
import foundation.metaplex.rpc.RPC
import foundation.metaplex.solanapublickeys.PublicKey
import java.math.BigDecimal

data class SolanaSwapBotConfig(
    val rpc: RPC,
    val jupiterSwapService: JupiterSwapService,
    val walletPublicKey: PublicKey = PublicKey(""),
    val swapMint: PublicKey = PublicKey("So11111111111111111111111111111111111111112"),
    val solAmountToTrade: BigDecimal = BigDecimal("0.0013"),
    val autoSellAllSpl: Boolean = true,
    val maxKnownTokens: Int = 10,
    val sellWaitMs: Long = 30_000,
    val zeroBalanceCloseBatch: Int = 9,
    val splSellBatch: Int = 10,
    val closeAccountsIntervalMs: Long = 10_000,
    val sellAllSplIntervalMs: Long = 10_000,
    val clearMapIntervalMs: Long = 60_000 * 60,
    val useJito: Boolean = true
)

const val privateKey: String =
    ""
