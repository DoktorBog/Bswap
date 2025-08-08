package com.bswap.server.data.solana.transaction

import wallet.core.jni.Curve
import wallet.core.jni.PrivateKey

data class Keypair(val publicKey: ByteArray, val secretKey: ByteArray)

object SolanaEddsa {
    fun createKeypairFromSecretKey(secret: ByteArray): Keypair {
        val pk = PrivateKey(secret)
        val pub = pk.getPublicKeyEd25519().data()
        return Keypair(pub, pk.data() + pub)
    }

    fun sign(message: ByteArray, keypair: Keypair): ByteArray {
        val pk = PrivateKey(keypair.secretKey.copyOfRange(0, 32))
        return pk.sign(message, Curve.ED25519)
    }
}

