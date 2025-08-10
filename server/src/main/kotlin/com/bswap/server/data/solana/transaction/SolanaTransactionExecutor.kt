package com.bswap.server.data.solana.transaction

import com.bswap.server.data.formatLamports
import com.bswap.server.privateKey
import com.bswap.server.rpc
import com.bswap.shared.wallet.decodeBase58
import com.metaplex.signer.Signer
import foundation.metaplex.base58.encodeToBase58String
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.RpcSendTransactionConfiguration
import foundation.metaplex.rpc.SerializedTransaction
import foundation.metaplex.rpc.TransactionSignature
import foundation.metaplex.solana.programs.SystemProgram.transfer
import foundation.metaplex.solana.transactions.SolanaTransactionBuilder
import foundation.metaplex.solana.transactions.Transaction
import foundation.metaplex.solana.transactions.TransactionInstruction
import foundation.metaplex.solanapublickeys.PublicKey
import org.sol4k.VersionedTransaction
import wallet.core.jni.Curve
import wallet.core.jni.PrivateKey
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
    try {
        println("createSwapTransaction: Starting transaction creation")
        
        // Validate private key format
        if (privateKey.isBlank()) {
            throw IllegalStateException("Private key is empty or blank")
        }
        
        println("createSwapTransaction: Private key length: ${privateKey.length} characters")
        
        // Decode private key from base58
        val decodedPrivateKey = try {
            privateKey.decodeBase58()
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Failed to decode private key from Base58: ${e.message}", e)
        } catch (e: Exception) {
            throw IllegalStateException("Unexpected error decoding private key: ${e.message}", e)
        }
        
        println("createSwapTransaction: Decoded private key length: ${decodedPrivateKey.size} bytes")
        
        // Validate decoded key length
        if (decodedPrivateKey.size < 32) {
            throw IllegalStateException("Decoded private key is too short: ${decodedPrivateKey.size} bytes (expected at least 32)")
        }
        
        // Extract the 32-byte secret key (first 32 bytes)
        val secretKey = decodedPrivateKey.copyOfRange(0, 32)
        println("createSwapTransaction: Secret key extracted (32 bytes)")
        
        // Create keypair from secret key
        val k = try {
            SolanaEddsa.createKeypairFromSecretKey(secretKey)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create keypair from secret key: ${e.message}", e)
        }
        println("createSwapTransaction: Keypair created successfully")
        
        println("createSwapTransaction: Creating sol4k keypair from secret")
        val sender = org.sol4k.Keypair.fromSecretKey(k.secretKey)
        println("createSwapTransaction: Sol4k keypair created successfully")
        
        println("createSwapTransaction: Parsing transaction from base64")
        val transaction = VersionedTransaction.from(base64)
        println("createSwapTransaction: Transaction parsed successfully")
        
        println("createSwapTransaction: Signing transaction")
        transaction.sign(sender)
        println("createSwapTransaction: Transaction signed successfully")
        
        println("createSwapTransaction: Serializing transaction")
        val serialized = transaction.serialize()
        println("createSwapTransaction: Transaction serialized successfully, size: ${serialized.size} bytes")
        
        return serialized
    } catch (e: Exception) {
        println("createSwapTransaction: ERROR - ${e.message}")
        e.printStackTrace()
        throw e
    }
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
    // Validate private key format (same as createSwapTransaction)
    if (privateKey.isBlank()) {
        throw IllegalStateException("Private key is empty or blank")
    }
    
    // Decode private key with proper validation
    val decodedPrivateKey = privateKey.decodeBase58()
    if (decodedPrivateKey.size < 32) {
        throw IllegalStateException("Private key decoded size is ${decodedPrivateKey.size}, expected at least 32 bytes")
    }
    
    val k = SolanaEddsa.createKeypairFromSecretKey(decodedPrivateKey.copyOfRange(0, 32))
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
