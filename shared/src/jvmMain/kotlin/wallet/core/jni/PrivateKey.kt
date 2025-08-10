package wallet.core.jni

import org.sol4k.Keypair

class PrivateKey(private val bytes: ByteArray = Keypair.generate().secret) {
    fun data(): ByteArray = bytes
    fun getPublicKeyEd25519(): PublicKey = PublicKey(Keypair.fromSecretKey(bytes).publicKey.bytes())
    fun getPublicKeySecp256k1(compressed: Boolean = false): PublicKey = PublicKey(ByteArray(0))
    fun getPublicKeyNist256p1(): PublicKey = PublicKey(ByteArray(0))
    fun sign(message: ByteArray, curve: Curve): ByteArray {
        return when (curve) {
            Curve.ED25519 -> Keypair.fromSecretKey(bytes).sign(message)
            else -> ByteArray(0)
        }
    }
}
