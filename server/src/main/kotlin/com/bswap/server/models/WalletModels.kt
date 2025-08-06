package com.bswap.server.models

import kotlinx.serialization.Serializable

@Serializable
data class WalletInfo(
    val publicKey: String,
    val balance: Double,
    val isActive: Boolean,
    val createdAt: Long,
    val lastUpdated: Long
)

@Serializable
data class WalletBalance(
    val publicKey: String,
    val solBalance: Double,
    val tokenBalances: Map<String, Double> = emptyMap(),
    val totalValueUSD: Double = 0.0,
    val lastUpdated: Long
)

@Serializable
data class WalletTransaction(
    val signature: String,
    val publicKey: String,
    val type: TransactionType,
    val amount: Double,
    val token: String,
    val timestamp: Long,
    val status: TransactionStatus,
    val fee: Double = 0.0,
    val description: String? = null
)

@Serializable
enum class TransactionType {
    BUY, SELL, SEND, RECEIVE, SWAP, STAKE, UNSTAKE
}

@Serializable
enum class TransactionStatus {
    PENDING, CONFIRMED, FAILED
}

@Serializable
data class CreateWalletRequest(
    val name: String? = null
)

@Serializable
data class CreateWalletResponse(
    val publicKey: String,
    val mnemonic: List<String>,
    val message: String
)

@Serializable
data class ImportWalletRequest(
    val mnemonic: List<String>,
    val name: String? = null
)

@Serializable
data class ImportWalletResponse(
    val publicKey: String,
    val message: String
)

@Serializable
data class WalletHistoryRequest(
    val publicKey: String,
    val limit: Int = 50,
    val offset: Int = 0,
    val type: TransactionType? = null
)

@Serializable
data class WalletHistoryResponse(
    val publicKey: String,
    val transactions: List<WalletTransaction>,
    val totalCount: Int,
    val hasMore: Boolean
)

@Serializable
data class TransferRequest(
    val fromWallet: String,
    val toAddress: String,
    val amount: Double,
    val token: String = "SOL"
)

@Serializable
data class TransferResponse(
    val signature: String,
    val status: String,
    val message: String
)