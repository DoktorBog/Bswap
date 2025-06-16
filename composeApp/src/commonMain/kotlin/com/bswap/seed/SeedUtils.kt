package com.bswap.seed

import kotlin.random.Random
import java.security.MessageDigest

object SeedUtils {
    fun generateSeed(): List<String> {
        val entropy = Random.Default.nextBytes(16)
        val sha = MessageDigest.getInstance("SHA-256").digest(entropy)
        val bits = entropy + byteArrayOf(sha[0])
        val bitString = bits.joinToString("") { byte ->
            String.format("%8s", byte.toInt() and 0xFF) .replace(' ', '0')
        }
        val words = mutableListOf<String>()
        for (i in 0 until 12) {
            val idxBits = bitString.substring(i * 11, i * 11 + 11)
            val index = idxBits.toInt(2)
            words += ENGLISH_WORDS[index]
        }
        return words
    }

    fun validateSeed(seed: List<String>, entered: List<String>): Boolean =
        seed == entered
}
