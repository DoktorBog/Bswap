package com.bswap.seed

import java.security.MessageDigest

/** Utility for validating BIP-39 mnemonics. */
object MnemonicValidator {
    /**
     * Returns `true` if [words] form a valid BIP-39 mnemonic.
     */
    fun isValidMnemonic(words: List<String>): Boolean {
        if (words.size !in setOf(12, 15, 18, 21, 24)) return false
        val indices = words.map { ENGLISH_WORDS.indexOf(it).takeIf { idx -> idx >= 0 } ?: return false }
        val bitString = indices.joinToString(separator = "") { it.toString(2).padStart(11, '0') }
        val entBits = 32 * (words.size / 3)
        val csBits = entBits / 32
        val entropyBits = bitString.substring(0, entBits)
        val checksumBits = bitString.substring(entBits)
        val entropyBytes = ByteArray(entBits / 8)
        for (i in entropyBytes.indices) {
            val slice = entropyBits.substring(i * 8, i * 8 + 8)
            entropyBytes[i] = slice.toInt(2).toByte()
        }
        val hashBits = MessageDigest.getInstance("SHA-256")
            .digest(entropyBytes)
            .joinToString(separator = "") { String.format("%8s", Integer.toBinaryString(it.toInt() and 0xFF)).replace(' ', '0') }
        val expected = hashBits.substring(0, csBits)
        return expected == checksumBits
    }
}

// TODO(JD): Add support for non-English wordlists
