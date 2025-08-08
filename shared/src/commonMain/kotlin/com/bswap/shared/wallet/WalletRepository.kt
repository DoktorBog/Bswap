package com.bswap.shared.wallet

class WalletRepository(private val adapter: WalletCoreAdapter) {
    fun generateAddress(coin: WalletCoin): Pair<String, ByteArray> {
        val (pub, priv) = adapter.generateKeypair(coin)
        val address = adapter.addressFromPrivateKey(priv, coin)
        return address to priv
    }

    fun signMessage(msg: ByteArray, privateKey: ByteArray, coin: WalletCoin): ByteArray =
        adapter.sign(msg, privateKey, coin)
}
