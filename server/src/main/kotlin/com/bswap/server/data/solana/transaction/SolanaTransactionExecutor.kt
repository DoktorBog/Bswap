package com.bswap.server.data.solana.transaction

import com.bswap.server.RPC_URL
import com.bswap.server.data.formatLamports
import com.metaplex.signer.Signer
import foundation.metaplex.base58.decodeBase58
import foundation.metaplex.base58.encodeToBase58String
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.RpcSendTransactionConfiguration
import foundation.metaplex.rpc.TransactionSignature
import foundation.metaplex.solana.programs.SystemProgram.transfer
import foundation.metaplex.solana.transactions.SolanaTransactionBuilder
import foundation.metaplex.solana.transactions.Transaction
import foundation.metaplex.solanaeddsa.Keypair
import foundation.metaplex.solanaeddsa.SolanaEddsa
import foundation.metaplex.solanapublickeys.PublicKey
import org.sol4k.Connection
import org.sol4k.VersionedTransaction
import java.math.BigDecimal

const val privateKey: String =
    ""

data class TransactionExecutionResult(
    val confirmed: Boolean,
    val error: String? = null
)

class SolanaKeypair(
    override val publicKey: com.solana.publickey.PublicKey,
    override val secretKey: ByteArray
) : Keypair

class HotSigner(private val keyPair: Keypair) : Signer {
    override val publicKey: com.solana.publickey.PublicKey = keyPair.publicKey
    override suspend fun signMessage(message: ByteArray): ByteArray =
        SolanaEddsa.sign(message, keyPair)
}

//TODO: refactoring
suspend fun executeAndConfirmTransaction(
    base64: String,
) {
    val swapConnection = Connection(RPC_URL)
    val k = SolanaEddsa.createKeypairFromSecretKey(privateKey.decodeBase58().copyOfRange(0, 32))
    val sender = org.sol4k.Keypair.fromSecretKey(k.secretKey)
    swapConnection.getLatestBlockhash()
    val transaction = VersionedTransaction.from(base64)
    println("Executing swap transaction...")
    transaction.sign(sender)
    runCatching {
        val signature = swapConnection.sendTransaction(transaction)
        println("Transaction swap succeeded $signature")
    }.onFailure {
        println("Transaction swap failed $it")
    }
}

//TODO: refactoring to sol4k
suspend fun executeAndConfirmTransaction(
    rpc: RPC,
    amount: BigDecimal,
    transactionExecutor: DefaultTransactionExecutor = DefaultTransactionExecutor(rpc),
) {

    val k = SolanaEddsa.createKeypairFromSecretKey(privateKey.decodeBase58().copyOfRange(0, 32))
    val signer = HotSigner(SolanaKeypair(k.publicKey, k.secretKey))
    val latestBlockhash = rpc.getLatestBlockhash(null)
    val transaction: Transaction = SolanaTransactionBuilder()
        .addInstruction(
            transfer(
                k.publicKey,
                PublicKey(""),
                amount.formatLamports()
            )
        )
        .setRecentBlockHash(latestBlockhash.blockhash)
        .setSigners(listOf(signer))
        .build()
    transactionExecutor.executeAndConfirm(transaction)
}

//TODO: refactoring to sol4k
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

    private suspend fun execute(
        transaction: Transaction,
    ): TransactionSignature {
        val serializedTransaction = transaction.serialize()
        return rpc.sendTransaction(
            serializedTransaction,
            RpcSendTransactionConfiguration(
                skipPreflight = false,
                maxRetries = UInt.MIN_VALUE.inc().inc() //2
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