package com.bswap.server.data.solana.swap.jupiter.models

import kotlinx.serialization.Serializable

@Serializable
data class QuoteResponse(
    val inputMint: String,
    val inAmount: String,
    val outputMint: String,
    val outAmount: String,
    val otherAmountThreshold: String,
    val swapMode: String,
    val slippageBps: Int,
    val platformFee: String? = null,
    val priceImpactPct: String,
    val routePlan: List<RoutePlan>,
    val scoreReport: String? = null,
    val contextSlot: Long,
    val timeTaken: Double
)

@Serializable
data class RoutePlan(
    val swapInfo: SwapInfo,
    val percent: Int
)

@Serializable
data class SwapInfo(
    val ammKey: String,
    val label: String,
    val inputMint: String,
    val outputMint: String,
    val inAmount: String,
    val outAmount: String,
    val feeAmount: String,
    val feeMint: String
)

@Serializable
data class SwapResponse(
    val swapTransaction: String,
    val lastValidBlockHeight: Long,
    val prioritizationFeeLamports: Long? = null,
    val computeUnitLimit: Long? = null,
    val prioritizationType: PrioritizationType? = null,
    val simulationSlot: Long? = null,
    val dynamicSlippageReport: DynamicSlippageReport? = null,
    val simulationError: String? = null
)

@Serializable
data class PrioritizationType(
    val computeBudget: ComputeBudget
)

@Serializable
data class ComputeBudget(
    val microLamports: Long,
    val estimatedMicroLamports: Long
)

@Serializable
data class DynamicSlippageReport(
    val slippageBps: Int,
    val otherAmount: Long,
    val simulatedIncurredSlippageBps: Int,
    val amplificationRatio: String
)