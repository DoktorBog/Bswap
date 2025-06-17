package foundation.metaplex.solanaeddsa

import foundation.metaplex.solanapublickeys.PublicKey
import wallet.core.jni.PrivateKey
import wallet.core.jni.Curve

object SolanaEddsa {
    fun generateKeypair(): Keypair {
        val privateKey = PrivateKey()
        val publicKey = privateKey.getPublicKeyEd25519()
        return Keypair(PublicKey(publicKey.data()), privateKey.data())
    }

    fun createKeypairFromSeed(seed: ByteArray): Keypair {
        val privateKey = PrivateKey(seed)
        val publicKey = privateKey.getPublicKeyEd25519()
        return Keypair(PublicKey(publicKey.data()), privateKey.data())
    }

    fun sign(message: ByteArray, keypair: Keypair): ByteArray {
        val privateKey = PrivateKey(keypair.secretKey)
        return privateKey.sign(message, Curve.ED25519)
    }
}
