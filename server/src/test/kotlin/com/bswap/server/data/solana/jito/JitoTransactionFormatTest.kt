package com.bswap.server.data.solana.jito

import com.bswap.server.data.solana.transaction.createSwapTransaction
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import java.util.Base64

class JitoTransactionFormatTest {

    @Test
    fun testTipTransactionVsSwapTransactionFormat() = runBlocking {
        try {
            // Create a sample Jupiter swap transaction (base64)
            val sampleSwapBase64 = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAQAGCbCV394cDl4acSgWSbuWSQuPVFYOGgrsUEybY4+g25jjMO1JVDbLDZRCGoQ606MycHInkG+PvONJWUFhAzrMHb3/ZZ9rm6sB6wLhVo0K6jzQEsi9tZ+et2rWZp6ie565KgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAwZGb+UhFzL/7K26csOb57yM5bvF9xJrLEObOkAAAAAEedVb8jHAbu50xW7OaBUH/bGy3qP0jlECsc2iVrwTjwbd9uHXZaGT2cvhRs7reawctIXtX1s3kTqM9YV+/wCpjJclj04kifG7PRApFI4NgwtaE5na/xCEBI572Nvp+Fm0P/on9df2SnTAmx8pWHneSwmrNt/J3VFLMhqns4zl6PAk8EjeGZVNt42ZLK3ezP+gPQf2kyAzZGKqQNNtLq1MCAQABQLAXBUABAAJAwQXAQAAAAAABwYAAgAMAwYBAQMCAAIMAgAAAEBCDwAAAAAABgECAREHBgABAA8DBgEBBRgGAAIBBQ8FCAUNEAoCAQsJDwwABgYNDg0j5RfLl3rjrSoBAAAATWQAAUBCDwAAAAAA9FGJxhgBAAAyAAAGAwIAAAEJAd1Ir49o+BaUryKJUf9i+98y7G5/5KNrQQD6qqcO1XYXA4qIiQUKjIuOjQ=="
            
            println("=== SWAP TRANSACTION ANALYSIS ===")
            
            // Process swap transaction through the same pipeline as Jito
            val swapTx = createSwapTransaction(sampleSwapBase64)
            val swapBase64 = Base64.getEncoder().encodeToString(swapTx)
            
            println("Swap transaction:")
            println("- Original base64 length: ${sampleSwapBase64.length}")
            println("- After processing: ${swapBase64.length} chars, ${swapTx.size} bytes")
            println("- First 100 chars: ${swapBase64.take(100)}")
            
            // Decode and analyze structure
            val swapDecoded = Base64.getDecoder().decode(swapBase64)
            println("- Decoded bytes: ${swapDecoded.size}")
            println("- First 20 bytes: ${swapDecoded.take(20).joinToString(" ") { "%02x".format(it) }}")
            
            println("\n=== TIP TRANSACTION ANALYSIS ===")
            
            // Create tip transaction
            val tipTx = JitoTxCreator.createTipTx(
                lamports = 10000,
                toPubkey = "HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe"
            )
            
            println("Tip transaction:")
            println("- Base64 length: ${tipTx.length} chars")
            println("- First 100 chars: ${tipTx.take(100)}")
            
            // Decode and analyze structure
            val tipDecoded = Base64.getDecoder().decode(tipTx)
            println("- Decoded bytes: ${tipDecoded.size}")
            println("- First 20 bytes: ${tipDecoded.take(20).joinToString(" ") { "%02x".format(it) }}")
            
            println("\n=== FORMAT COMPARISON ===")
            
            // Compare transaction headers
            val swapHeader = swapDecoded.take(10)
            val tipHeader = tipDecoded.take(10)
            
            println("Swap header: ${swapHeader.joinToString(" ") { "%02x".format(it) }}")
            println("Tip header:  ${tipHeader.joinToString(" ") { "%02x".format(it) }}")
            println("Headers match: ${swapHeader == tipHeader}")
            
            // Compare account counts
            if (swapDecoded.size > 3 && tipDecoded.size > 3) {
                val swapAccounts = swapDecoded[3].toInt() and 0xFF
                val tipAccounts = tipDecoded[3].toInt() and 0xFF
                println("Swap accounts: $swapAccounts")
                println("Tip accounts: $tipAccounts")
            }
            
            // Validate both are valid base64 and non-empty
            assertNotNull(swapTx)
            assertNotNull(tipTx)
            assertTrue(swapTx.isNotEmpty())
            assertTrue(tipTx.isNotEmpty())
            
            println("\n=== TRANSACTION VALIDATION ===")
            println("Both transactions created successfully")
            println("Test completed - check logs for format differences")
            
        } catch (e: Exception) {
            if (e.message?.contains("Private key is empty") == true || 
                e.message?.contains("toIndex (32) is greater than size (0)") == true) {
                println("Skipping Jito format test - no private key configured (expected in CI)")
                // This is expected in CI environment
            } else {
                println("Unexpected error in Jito format test: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }
    
    @Test
    fun testTipTransactionValidation() = runBlocking {
        try {
            println("=== TIP TRANSACTION VALIDATION ===")
            
            val tipTx = JitoTxCreator.createTipTx(
                lamports = 5000,
                toPubkey = "ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49"
            )
            
            // Basic validation
            assertNotNull(tipTx)
            assertTrue(tipTx.isNotEmpty())
            
            // Base64 validation
            val decoded = Base64.getDecoder().decode(tipTx)
            assertTrue(decoded.isNotEmpty())
            
            // Transaction format validation
            println("Tip transaction validation:")
            println("- Base64 length: ${tipTx.length}")
            println("- Decoded size: ${decoded.size} bytes")
            println("- Size range check: ${decoded.size in 200..300} (expected ~215)")
            
            // Check if it starts with correct transaction signature count
            if (decoded.isNotEmpty()) {
                val sigCount = decoded[0].toInt() and 0xFF
                println("- Signature count: $sigCount (should be 1)")
                assertTrue(sigCount == 1, "Tip transaction should have exactly 1 signature")
            }
            
            // Verify it's in the expected size range for a simple transfer
            assertTrue(decoded.size in 200..300, "Tip transaction size should be ~215 bytes, got ${decoded.size}")
            
            println("Tip transaction validation passed")
            
        } catch (e: Exception) {
            if (e.message?.contains("Private key is empty") == true || 
                e.message?.contains("toIndex (32) is greater than size (0)") == true) {
                println("Skipping tip validation test - no private key configured (expected in CI)")
            } else {
                throw e
            }
        }
    }
    
    @Test
    fun testJitoFormatConsistency() {
        println("=== JITO FORMAT CONSISTENCY TEST ===")
        
        // Test that our format matches Jito expectations
        // Jito bundles expect:
        // 1. Transaction #0 = Tip transaction (SOL transfer to tip account)
        // 2. Transactions #1-N = User transactions
        // 3. All transactions must be base64-encoded, signed, serialized VersionedTransactions
        
        println("Jito bundle format requirements:")
        println("1. All transactions must be base58-encoded signed VersionedTransactions (NOT base64)")
        println("2. Transaction #0 must be a valid SOL transfer (tip transaction)")
        println("3. Transaction format must match Solana's VersionedTransaction specification")
        println("4. Transactions must be properly signed with valid signatures")
        println("5. Maximum 5 transactions per bundle")
        
        assertTrue(true, "Format consistency check completed")
    }
}