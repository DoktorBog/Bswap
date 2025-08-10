package wallet.core.jni

class PublicKey(private val bytes: ByteArray) {
    fun data(): ByteArray = bytes
}
