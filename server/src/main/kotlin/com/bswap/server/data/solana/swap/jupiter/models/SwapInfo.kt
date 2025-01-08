package com.bswap.server.data.solana.swap.jupiter.models

import kotlinx.serialization.Serializable

@Serializable
data class QuoteResponse(
    val inputMint: String? = null,
    val inAmount: String? = null,
    val outputMint: String? = null,
    val outAmount: String? = null,
    val otherAmountThreshold: String? = null,
    val swapMode: String? = null,
    val slippageBps: Int? = null,
    val platformFee: String? = null,
    val priceImpactPct: String? = null,
    val routePlan: List<RoutePlan> = emptyList(),
    val scoreReport: String? = null,
    val contextSlot: Long? = null,
    val timeTaken: Double? = null
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
    val error: String? = null,
    val swapTransaction: String? = null,
    val lastValidBlockHeight: Long? = null,
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