package com.bswap.server.data.solana.transaction

import foundation.metaplex.solanapublickeys.PublicKey
import foundation.metaplex.base58.encodeToBase58String
import kotlinx.coroutines.runBlocking
import org.sol4k.VersionedTransaction
import wallet.core.jni.Curve
import wallet.core.jni.PrivateKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SolanaTransactionIntegrationTest {

    // Test with a known keypair for reproducible results  
    private val testSecretKey = ByteArray(32) { (it * 3 + 17).toByte() }
    
    @Test
    fun testFullKeypairWorkflow() {
        // 1. Generate keypair from secret
        val keypair = SolanaEddsa.createKeypairFromSecretKey(testSecretKey)
        
        // 2. Create HotSigner
        val solanaKeypair = SolanaKeypair(PublicKey(keypair.publicKey), keypair.secretKey)
        val hotSigner = HotSigner(solanaKeypair)
        
        // 3. Test message signing workflow
        val message = "Solana transaction test message".toByteArray()
        val signature = runBlocking {
            hotSigner.signMessage(message)
        }
        
        // Verify signature properties
        assertNotNull(signature)
        assertEquals(64, signature.size, "Ed25519 signature should be 64 bytes")
        
        // 4. Verify signature using wallet-core directly
        val privateKey = PrivateKey(keypair.secretKey.copyOfRange(0, 32))
        val directSignature = privateKey.sign(message, Curve.ED25519)
        
        assertTrue(
            signature.contentEquals(directSignature),
            "HotSigner and direct wallet-core signing should produce identical signatures"
        )
        
        // 5. Verify public key consistency
        val publicKeyFromPrivate = privateKey.getPublicKeyEd25519().data()
        assertTrue(
            keypair.publicKey.contentEquals(publicKeyFromPrivate),
            "Public key should match what wallet-core generates"
        )
    }
    
    @Test
    fun testSignatureUniqueness() {
        // Test that different messages produce different signatures
        val keypair = SolanaEddsa.createKeypairFromSecretKey(testSecretKey)
        val solanaKeypair = SolanaKeypair(PublicKey(keypair.publicKey), keypair.secretKey)
        val hotSigner = HotSigner(solanaKeypair)
        
        val message1 = "Message 1".toByteArray()
        val message2 = "Message 2".toByteArray()
        
        val signature1 = runBlocking { hotSigner.signMessage(message1) }
        val signature2 = runBlocking { hotSigner.signMessage(message2) }
        
        assertNotEquals(
            signature1.contentToString(),
            signature2.contentToString(),
            "Different messages should produce different signatures"
        )
    }
    
    @Test
    fun testDifferentKeysProduceDifferentSignatures() {
        val message = "Same message for both keys".toByteArray()
        
        // Key 1
        val testKey1 = ByteArray(32) { it.toByte() }
        val keypair1 = SolanaEddsa.createKeypairFromSecretKey(testKey1)
        val signer1 = HotSigner(SolanaKeypair(PublicKey(keypair1.publicKey), keypair1.secretKey))
        
        // Key 2
        val testKey2 = ByteArray(32) { (it + 100).toByte() }
        val keypair2 = SolanaEddsa.createKeypairFromSecretKey(testKey2)
        val signer2 = HotSigner(SolanaKeypair(PublicKey(keypair2.publicKey), keypair2.secretKey))
        
        val signature1 = runBlocking { signer1.signMessage(message) }
        val signature2 = runBlocking { signer2.signMessage(message) }
        
        assertNotEquals(
            signature1.contentToString(),
            signature2.contentToString(),
            "Different keys should produce different signatures for same message"
        )
    }
    
    @Test
    fun testTransactionExecutionResultValidation() {
        // Test various execution result scenarios
        val successResult = TransactionExecutionResult(confirmed = true)
        assertTrue(successResult.confirmed)
        assertEquals(null, successResult.error)
        
        val failureWithError = TransactionExecutionResult(confirmed = false, error = "RPC timeout")
        assertTrue(!failureWithError.confirmed)
        assertEquals("RPC timeout", failureWithError.error)
        
        val failureWithoutError = TransactionExecutionResult(confirmed = false)
        assertTrue(!failureWithoutError.confirmed)
        assertEquals(null, failureWithoutError.error)
    }
    
    @Test
    fun testPublicKeyEncoding() {
        val keypair = SolanaEddsa.createKeypairFromSecretKey(testSecretKey)
        val publicKey = PublicKey(keypair.publicKey)
        
        // Test that public key can be encoded to base58
        val base58String = publicKey.toString()
        assertNotNull(base58String)
        assertTrue(base58String.length > 30, "Base58 public key should be reasonable length")
        
        // Test that it contains only valid base58 characters
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        assertTrue(
            base58String.all { it in base58Chars },
            "Public key should contain only valid base58 characters"
        )
    }
    
    @Test
    fun testSigningPerformance() {
        // Test that signing is reasonably fast
        val keypair = SolanaEddsa.createKeypairFromSecretKey(testSecretKey)
        val signer = HotSigner(SolanaKeypair(PublicKey(keypair.publicKey), keypair.secretKey))
        val message = "Performance test message".toByteArray()
        
        val startTime = System.currentTimeMillis()
        
        runBlocking {
            // Sign 100 messages
            repeat(100) {
                signer.signMessage(message)
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Should complete 100 signatures in under 1 second
        assertTrue(duration < 1000, "100 signatures should complete in under 1 second, took ${duration}ms")
    }
    
}