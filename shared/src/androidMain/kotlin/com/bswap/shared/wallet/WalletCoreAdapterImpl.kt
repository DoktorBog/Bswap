package com.bswap.shared.wallet

import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.Curve

class WalletCoreAdapterImpl : WalletCoreAdapter {
    init {
        try { System.loadLibrary("TrustWalletCore") } catch (_: Throwable) {}
    }

    override fun generateKeypair(coin: CoinType): Keypair {
        val pk = PrivateKey()
        val pub = when (coin.curve()) {
            Curve.SECP256K1 -> pk.getPublicKeySecp256k1(false)
            Curve.NIST256P1 -> pk.getPublicKeyNist256p1()
            else -> pk.getPublicKeyEd25519()
        }
        return Keypair(pub.data(), pk.data())
    }

    override fun addressFromPrivateKey(privateKey: ByteArray, coin: CoinType): String {
        val pk = PrivateKey(privateKey)
        return coin.deriveAddress(pk)
    }

    override fun sign(message: ByteArray, privateKey: ByteArray, coin: CoinType): ByteArray {
        val pk = PrivateKey(privateKey)
        return pk.sign(message, coin.curve())
    }
}
