package com.bswap.shared.wallet

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

class WalletCoreAdapterImpl : WalletCoreAdapter {
    private val random = SecureRandom()

    override fun generateKeypair(coin: WalletCoin): Keypair {
        return when (coin) {
            WalletCoin.SOLANA -> {
                val seed = ByteArray(32).also { random.nextBytes(it) }
                val priv = Ed25519PrivateKeyParameters(seed, 0)
                val pub = priv.generatePublicKey().encoded
                Keypair(pub, seed + pub)
            }
            else -> throw UnsupportedOperationException("Coin ${coin} not supported on JVM adapter")
        }
    }

    override fun addressFromPrivateKey(privateKey: ByteArray, coin: WalletCoin): String {
        return when (coin) {
            WalletCoin.SOLANA -> {
                val seed = if (privateKey.size >= 32) privateKey.copyOfRange(0, 32) else privateKey
                val priv = Ed25519PrivateKeyParameters(seed, 0)
                val pub = priv.generatePublicKey().encoded
                base58Encode(pub)
            }
            else -> throw UnsupportedOperationException("Coin ${coin} not supported on JVM adapter")
        }
    }

    override fun sign(message: ByteArray, privateKey: ByteArray, coin: WalletCoin): ByteArray {
        return when (coin) {
            WalletCoin.SOLANA -> {
                val seed = if (privateKey.size >= 32) privateKey.copyOfRange(0, 32) else privateKey
                val priv = Ed25519PrivateKeyParameters(seed, 0)
                val signer = Ed25519Signer()
                signer.init(true, priv)
                signer.update(message, 0, message.size)
                signer.generateSignature()
            }
            else -> throw UnsupportedOperationException("Coin ${coin} not supported on JVM adapter")
        }
    }

    private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private fun base58Encode(input: ByteArray): String {
        var bi = java.math.BigInteger(1, input)
        val sb = StringBuilder()
        val base = java.math.BigInteger.valueOf(58)
        while (bi.compareTo(java.math.BigInteger.ZERO) > 0) {
            val divRem = bi.divideAndRemainder(base)
            bi = divRem[0]
            val rem = divRem[1].intValueExact()
            sb.append(ALPHABET[rem])
        }
        for (b in input) {
            if (b.toInt() == 0) sb.append(ALPHABET[0]) else break
        }
        return sb.reverse().toString()
    }
}
