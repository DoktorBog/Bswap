package com.bswap.shared.wallet

import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.Curve
import com.bswap.shared.wallet.WalletCoin

class WalletCoreAdapterImpl : WalletCoreAdapter {
    init {
        try { System.loadLibrary("TrustWalletCore") } catch (_: Throwable) {}
    }
    override fun generateKeypair(coin: WalletCoin): Keypair {
        val ck = when (coin) {
            WalletCoin.SOLANA -> CoinType.SOLANA
            WalletCoin.BITCOIN -> CoinType.BITCOIN
            WalletCoin.ETHEREUM -> CoinType.ETHEREUM
        }
        val pk = PrivateKey()
        val pub = when (ck.curve()) {
            Curve.SECP256K1 -> pk.getPublicKeySecp256k1(false)
            Curve.NIST256P1 -> pk.getPublicKeyNist256p1()
            else -> pk.getPublicKeyEd25519()
        }
        return Keypair(pub.data(), pk.data())
    }

    override fun addressFromPrivateKey(privateKey: ByteArray, coin: WalletCoin): String {
        val ck = when (coin) {
            WalletCoin.SOLANA -> CoinType.SOLANA
            WalletCoin.BITCOIN -> CoinType.BITCOIN
            WalletCoin.ETHEREUM -> CoinType.ETHEREUM
        }
        val pk = PrivateKey(privateKey)
        return ck.deriveAddress(pk)
    }

    override fun sign(message: ByteArray, privateKey: ByteArray, coin: WalletCoin): ByteArray {
        val ck = when (coin) {
            WalletCoin.SOLANA -> CoinType.SOLANA
            WalletCoin.BITCOIN -> CoinType.BITCOIN
            WalletCoin.ETHEREUM -> CoinType.ETHEREUM
        }
        val pk = PrivateKey(privateKey)
        return pk.sign(message, ck.curve())
    }
}
