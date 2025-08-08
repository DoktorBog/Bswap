package com.bswap.shared.wallet

import wallet.core.jni.CoinType

class WalletRepository(private val adapter: WalletCoreAdapter) {
    fun generateAddress(coin: CoinType): Pair<String, ByteArray> {
        val (pub, priv) = adapter.generateKeypair(coin)
        val address = adapter.addressFromPrivateKey(priv, coin)
        return address to priv
    }

    fun signMessage(msg: ByteArray, privateKey: ByteArray, coin: CoinType): ByteArray =
        adapter.sign(msg, privateKey, coin)
}
