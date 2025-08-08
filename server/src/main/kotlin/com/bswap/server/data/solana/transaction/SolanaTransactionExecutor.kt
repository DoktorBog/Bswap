package com.bswap.server.data.solana.transaction

import com.bswap.server.data.formatLamports
import com.bswap.server.privateKey
import com.bswap.server.rpc
import com.metaplex.signer.Signer
import com.bswap.shared.wallet.decodeBase58
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.RpcSendTransactionConfiguration
import foundation.metaplex.rpc.SerializedTransaction
import foundation.metaplex.rpc.TransactionSignature
import foundation.metaplex.solana.programs.SystemProgram.transfer
import foundation.metaplex.solana.transactions.SolanaTransactionBuilder
import foundation.metaplex.solana.transactions.Transaction
import foundation.metaplex.solana.transactions.TransactionInstruction
import foundation.metaplex.solanapublickeys.PublicKey
import wallet.core.jni.PrivateKey
import wallet.core.jni.Curve
import foundation.metaplex.base58.encodeToBase58String
import com.bswap.server.data.solana.transaction.SolanaEddsa
import org.sol4k.VersionedTransaction
import java.math.BigDecimal

data class TransactionExecutionResult(
    val confirmed: Boolean,
    val error: String? = null
)

data class SolanaKeypair(
    val publicKey: com.solana.publickey.PublicKey,
    val secretKey: ByteArray
)

class HotSigner(private val keyPair: SolanaKeypair) : Signer {
    override val publicKey: com.solana.publickey.PublicKey = keyPair.publicKey
    override suspend fun signMessage(message: ByteArray): ByteArray {
        val pk = PrivateKey(keyPair.secretKey.copyOfRange(0, 32))
        return pk.sign(message, Curve.ED25519)
    }
}

suspend fun createSwapTransaction(
    base64: String,
): ByteArray {
    val k = SolanaEddsa.createKeypairFromSecretKey(privateKey.decodeBase58().copyOfRange(0, 32))
    val sender = org.sol4k.Keypair.fromSecretKey(k.secretKey)
    val transaction = VersionedTransaction.from(base64)
    transaction.sign(sender)
    return transaction.serialize()
}

suspend fun executeSwapTransaction(
    rpc: RPC,
    base64: String,
    transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
): Boolean {
    return transactionExecutor.executeAndConfirm(createSwapTransaction(base64)).confirmed
}

suspend fun executeSolTransaction(
    rpc: RPC,
    transactionInstruction: TransactionInstruction,
    transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
) {

    val k = SolanaEddsa.createKeypairFromSecretKey(privateKey.decodeBase58().copyOfRange(0, 32))
    val signer = HotSigner(SolanaKeypair(PublicKey(k.publicKey), k.secretKey))
    val latestBlockhash = rpc.getLatestBlockhash(null)
    val transaction: Transaction = SolanaTransactionBuilder()
        .addInstruction(transactionInstruction)
        .setRecentBlockHash(latestBlockhash.blockhash)
        .setSigners(listOf(signer))
        .build()
    transactionExecutor.executeAndConfirm(transaction)
}

suspend fun createTransactionWithInstructions(
    transactionInstruction: List<TransactionInstruction>,
): Transaction {
    val k = SolanaEddsa.createKeypairFromSecretKey(privateKey.decodeBase58().copyOfRange(0, 32))
    val signer = HotSigner(SolanaKeypair(PublicKey(k.publicKey), k.secretKey))
    val latestBlockhash = rpc.getLatestBlockhash(null)
    return SolanaTransactionBuilder()
        .apply {
            for (instruction in transactionInstruction.take(9)) {
                addInstruction(instruction)
            }
        }
        .setRecentBlockHash(latestBlockhash.blockhash)
        .setSigners(listOf(signer)).build()
}

suspend fun executeSolTransaction(
    rpc: RPC,
    transactionInstruction: List<TransactionInstruction>,
    transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
) {
    transactionExecutor.executeAndConfirm(createTransactionWithInstructions(transactionInstruction))
}

suspend fun createSolTransaction(
    rpc: RPC,
    amount: BigDecimal? = null,
    lamports: Long? = null,
    toPublicKey: PublicKey = PublicKey(""),
): Transaction {
    val k = SolanaEddsa.createKeypairFromSecretKey(privateKey.decodeBase58().copyOfRange(0, 32))
    val signer = HotSigner(SolanaKeypair(PublicKey(k.publicKey), k.secretKey))
    val latestBlockhash = rpc.getLatestBlockhash(null)
    return SolanaTransactionBuilder()
        .addInstruction(
            transfer(
                PublicKey(k.publicKey),
                toPublicKey,
                amount?.formatLamports() ?: (lamports ?: 0L)
            )
        )
        .setRecentBlockHash(latestBlockhash.blockhash)
        .setSigners(listOf(signer))
        .build()
}

suspend fun executeSolTransaction(
    rpc: RPC,
    amount: BigDecimal? = null,
    lamports: Long? = null,
    toPublicKey: PublicKey = PublicKey(""),
    transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
) {
    transactionExecutor.executeAndConfirm(createSolTransaction(rpc, amount, lamports, toPublicKey))
}

class DefaultTransactionExecutor(
    private val rpc: RPC
) {

    suspend fun executeAndConfirm(
        transaction: Transaction,
    ): TransactionExecutionResult {
        println("Executing transaction...")

        return try {
            val signature = execute(transaction)
            val signString = signature.encodeToBase58String()
            println("Transaction signature: $signString")
            println("Confirming transaction...")
            confirm(signature)
        } catch (e: Exception) {
            println("Transaction failed Exception $e")
            TransactionExecutionResult(false, null)
        }
    }

    suspend fun executeAndConfirm(
        transaction: SerializedTransaction,
    ): TransactionExecutionResult {
        println("Executing transaction...")

        return try {
            val signature = execute(transaction)
            val signString = signature.encodeToBase58String()
            println("Transaction signature: $signString")
            println("Confirming transaction...")
            confirm(signature)
        } catch (e: Exception) {
            println("Transaction failed Exception $e")
            TransactionExecutionResult(false, null)
        }
    }

    private suspend fun execute(
        transaction: SerializedTransaction,
    ): TransactionSignature {
        return rpc.sendTransaction(
            transaction,
            RpcSendTransactionConfiguration(
                skipPreflight = true,
                maxRetries = 3u
            )
        )
    }

    private suspend fun execute(
        transaction: Transaction,
    ): TransactionSignature {
        val serializedTransaction = transaction.serialize()
        return rpc.sendTransaction(
            serializedTransaction,
            RpcSendTransactionConfiguration(
                skipPreflight = true,
                maxRetries = 3u
            )
        )
    }

    private fun confirm(
        transactionSignature: TransactionSignature,
    ): TransactionExecutionResult {
        val transactionSignatureList = transactionSignature.toList()

        return if (transactionSignatureList.isNotEmpty()) {
            println("Transaction succeed \uD83D\uDD25")
            TransactionExecutionResult(true)
        } else {
            println("Transaction failed $transactionSignatureList")
            TransactionExecutionResult(
                false,
                "Transaction failed: $transactionSignatureList"
            )
        }
    }
}