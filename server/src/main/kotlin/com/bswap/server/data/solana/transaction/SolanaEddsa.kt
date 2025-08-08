package com.bswap.server.data.solana.transaction

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

data class Keypair(val publicKey: ByteArray, val secretKey: ByteArray)

object SolanaEddsa {
    fun createKeypairFromSecretKey(secret: ByteArray): Keypair {
        val seed = if (secret.size >= 32) secret.copyOfRange(0, 32) else secret
        val priv = Ed25519PrivateKeyParameters(seed, 0)
        val pub = priv.generatePublicKey().encoded
        return Keypair(pub, seed + pub)
    }

    fun sign(message: ByteArray, keypair: Keypair): ByteArray {
        val seed = keypair.secretKey.copyOfRange(0, 32)
        val priv = Ed25519PrivateKeyParameters(seed, 0)
        val signer = Ed25519Signer()
        signer.init(true, priv)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }
}
