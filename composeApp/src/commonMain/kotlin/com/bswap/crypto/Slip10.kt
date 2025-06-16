package com.bswap.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal SLIP-0010 Ed25519 key derivation for hardened paths.
 */
object Slip10 {
    private const val HMAC_ALG = "HmacSHA512"
    private val MASTER_KEY = "ed25519 seed".toByteArray()

    fun derivePath(path: IntArray, seed: ByteArray): ByteArray {
        var keyMaterial = hmac(MASTER_KEY, seed)
        var secret = keyMaterial.copyOfRange(0, 32)
        var chainCode = keyMaterial.copyOfRange(32, 64)

        for (index in path) {
            val hardened = index or 0x80000000.toInt()
            val data = ByteBuffer.allocate(1 + 32 + 4).apply {
                put(0)
                put(secret)
                putInt(hardened)
            }.array()
            keyMaterial = hmac(chainCode, data)
            secret = keyMaterial.copyOfRange(0, 32)
            chainCode = keyMaterial.copyOfRange(32, 64)
        }
        return secret
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(key, HMAC_ALG))
        return mac.doFinal(data)
    }
}
