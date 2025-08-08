package wallet.core.jni

import org.sol4k.PublicKey

enum class CoinType {
    SOLANA,
    BITCOIN,
    ETHEREUM;
    fun curve(): Curve = Curve.ED25519
    fun deriveAddress(privateKey: PrivateKey): String =
        PublicKey(privateKey.getPublicKeyEd25519().data()).toBase58()
}
