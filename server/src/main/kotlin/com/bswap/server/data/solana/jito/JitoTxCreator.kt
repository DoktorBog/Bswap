package com.bswap.server.data.solana.jito

import com.bswap.server.data.solana.transaction.SolanaEddsa
import com.bswap.server.data.solana.transaction.createSolTransaction
import com.bswap.server.privateKey
import com.bswap.server.rpc
import com.bswap.shared.wallet.decodeBase58
import com.bswap.shared.wallet.encodeToBase58
import foundation.metaplex.solanapublickeys.PublicKey
import org.sol4k.Keypair
import org.sol4k.VersionedTransaction

object JitoTxCreator {
    
    suspend fun createTipTx(
        lamports: Long,
        toPubkey: String,
    ): String {
        return try {
            
            // Use the same exact process as working swap transactions
            // First, create a Metaplex transaction using the working createSolTransaction
            val metaplexTransaction = createSolTransaction(
                rpc = rpc,
                lamports = lamports,
                toPublicKey = PublicKey(toPubkey)
            )
            
            // Serialize the Metaplex transaction to base64 (same as Jupiter transactions)
            val metaplexBytes = metaplexTransaction.serialize()
            val metaplexBase64 = java.util.Base64.getEncoder().encodeToString(metaplexBytes)
            
            
            // Parse as VersionedTransaction (exact same as swap transactions)
            val versionedTransaction = VersionedTransaction.from(metaplexBase64)
            
            // Create Sol4k keypair for signing (exact same as swap transactions)
            val decodedPrivateKey = privateKey.decodeBase58()
            val secretKey = decodedPrivateKey.copyOfRange(0, 32)
            val k = SolanaEddsa.createKeypairFromSecretKey(secretKey)
            val sol4kSender = Keypair.fromSecretKey(k.secretKey)
            
            // Sign with Sol4k (exact same as swap transactions)
            versionedTransaction.sign(sol4kSender)
            
            // Serialize to get the final bytes (exact same as swap transactions)
            val txBytes = versionedTransaction.serialize()
            
            // IMPORTANT: Jito requires base58 encoding, NOT base64!
            // According to Jito docs: "Jito bundles require fully-signed transactions to be encoded as base-58 strings"
            val base58Tx = txBytes.encodeToBase58()
            
            
            base58Tx
        } catch (e: Exception) {
            throw e
        }
    }
}