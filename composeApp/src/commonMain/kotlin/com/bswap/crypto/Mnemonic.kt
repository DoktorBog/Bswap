package com.bswap.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Mnemonic {
    fun toSeed(words: List<String>, passphrase: String = ""): ByteArray {
        val phrase = words.joinToString(" ")
        val salt = "mnemonic" + passphrase
        val spec = PBEKeySpec(phrase.toCharArray(), salt.toByteArray(), 2048, 512)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return f.generateSecret(spec).encoded
    }
}
