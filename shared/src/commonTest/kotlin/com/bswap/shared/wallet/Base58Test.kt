package com.bswap.shared.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class Base58Test {

    @Test
    fun testEmptyString() {
        val empty = ""
        val decoded = empty.decodeBase58()
        assertTrue(decoded.isEmpty(), "Empty string should decode to empty byte array")
        
        val emptyBytes = byteArrayOf()
        val encoded = emptyBytes.encodeToBase58()
        assertEquals("", encoded, "Empty byte array should encode to empty string")
    }

    @Test
    fun testSingleByte() {
        val testCases = mapOf(
            byteArrayOf(0.toByte()) to "1",
            byteArrayOf(1.toByte()) to "2", 
            byteArrayOf(57.toByte()) to "z",
            byteArrayOf(58.toByte()) to "21"
        )
        
        for ((bytes, expected) in testCases) {
            val encoded = bytes.encodeToBase58()
            assertEquals(expected, encoded, "Failed to encode ${bytes.contentToString()}")
            
            val decoded = expected.decodeBase58()
            assertContentEquals(bytes, decoded, "Failed to decode $expected")
        }
    }

    @Test
    fun testLeadingZeros() {
        val testCases = mapOf(
            byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()) to "1112",
            byteArrayOf(0.toByte(), 1.toByte(), 2.toByte(), 3.toByte()) to "1Ldp",
            byteArrayOf(0.toByte(), 0.toByte(), 255.toByte(), 255.toByte()) to "115Q"
        )
        
        for ((bytes, expected) in testCases) {
            val encoded = bytes.encodeToBase58()
            assertEquals(expected, encoded, "Failed to encode ${bytes.contentToString()}")
            
            val decoded = expected.decodeBase58()
            assertContentEquals(bytes, decoded, "Failed to decode $expected")
        }
    }

    @Test
    fun testLargeNumbers() {
        val testCases = mapOf(
            byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()) to "7YXq9G",
            byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9) to "1kA3B2yGe7Ey",
            byteArrayOf(-1, -2, -3, -4, -5) to "VtB5VXc"
        )
        
        for ((bytes, expected) in testCases) {
            val encoded = bytes.encodeToBase58()
            assertEquals(expected, encoded, "Failed to encode ${bytes.contentToString()}")
            
            val decoded = expected.decodeBase58()
            assertContentEquals(bytes, decoded, "Failed to decode $expected")
        }
    }

    @Test
    fun testSolanaPublicKey() {
        // Test with a known Solana public key format (32 bytes)
        val publicKeyBytes = ByteArray(32) { (it * 7 + 11).toByte() }
        
        val encoded = publicKeyBytes.encodeToBase58()
        assertTrue(encoded.length > 30, "Solana public key should encode to reasonable length")
        
        val decoded = encoded.decodeBase58()
        assertContentEquals(publicKeyBytes, decoded, "Public key should round-trip correctly")
    }

    @Test
    fun testSolanaPrivateKey() {
        // Test with a known Solana private key format (64 bytes: 32 private + 32 public)
        val privateKeyBytes = ByteArray(64) { (it * 3 + 17).toByte() }
        
        val encoded = privateKeyBytes.encodeToBase58()
        assertTrue(encoded.length > 80, "Solana private key should encode to reasonable length")
        
        val decoded = encoded.decodeBase58()
        assertContentEquals(privateKeyBytes, decoded, "Private key should round-trip correctly")
        assertEquals(64, decoded.size, "Decoded private key should be 64 bytes")
    }

    @Test
    fun testInvalidCharacters() {
        val invalidStrings = listOf(
            "0", // Invalid character '0'
            "O", // Invalid character 'O'
            "I", // Invalid character 'I' 
            "l", // Invalid character 'l' (lowercase L)
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0", // Contains '0'
            "Hello World!", // Contains spaces and special chars
            "test+test"  // Contains '+'
        )
        
        for (invalid in invalidStrings) {
            assertFailsWith<IllegalArgumentException>(
                "Should throw exception for invalid string: '$invalid'"
            ) {
                invalid.decodeBase58()
            }
        }
    }

    @Test
    fun testValidAlphabet() {
        // Test that all valid Base58 characters work
        val validChars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        
        for (c in validChars) {
            val singleChar = c.toString()
            // Should not throw exception
            val decoded = singleChar.decodeBase58()
            assertTrue(decoded.isNotEmpty(), "Valid character '$c' should decode to non-empty array")
            
            // Should round-trip
            val encoded = decoded.encodeToBase58()
            assertTrue(encoded.isNotEmpty(), "Decoded bytes should encode back to non-empty string")
        }
    }

    @Test
    fun testRoundTripWithRandomData() {
        // Test round-trip encoding/decoding with various byte arrays
        val testArrays = listOf(
            byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte(), 5.toByte()),
            ByteArray(32) { it.toByte() },
            ByteArray(64) { (it * 2).toByte() },
            byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 255.toByte(), 255.toByte()),
            ByteArray(100) { (it * 7 % 256).toByte() }
        )
        
        for (original in testArrays) {
            val encoded = original.encodeToBase58()
            val decoded = encoded.decodeBase58()
            assertContentEquals(original, decoded, 
                "Round-trip failed for ${original.contentToString()}")
        }
    }

    @Test
    fun testBase58AlphabetOrder() {
        // Verify the alphabet is correctly ordered
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        
        // Test that encoding single bytes produces expected order
        for (i in 1 until 58) {
            val bytes = byteArrayOf(i.toByte())
            val encoded = bytes.encodeToBase58()
            assertEquals(alphabet[i].toString(), encoded, 
                "Byte $i should encode to character '${alphabet[i]}'")
        }
    }

    @Test
    fun testPerformance() {
        // Performance test with reasonably large data
        val largeData = ByteArray(1000) { (it * 13 % 256).toByte() }
        
        val startTime = System.currentTimeMillis()
        val encoded = largeData.encodeToBase58()
        val decoded = encoded.decodeBase58()
        val endTime = System.currentTimeMillis()
        
        assertContentEquals(largeData, decoded, "Large data round-trip should work")
        assertTrue(endTime - startTime < 1000, "Encoding/decoding 1KB should take less than 1 second")
    }
}