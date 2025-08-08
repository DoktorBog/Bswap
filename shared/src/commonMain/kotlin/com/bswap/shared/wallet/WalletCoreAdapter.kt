package com.bswap.shared.wallet

/** Adapter interface for platform-specific wallet/key operations. */
interface WalletCoreAdapter {
    fun generateKeypair(coin: WalletCoin): Keypair
    fun addressFromPrivateKey(privateKey: ByteArray, coin: WalletCoin): String
    fun sign(message: ByteArray, privateKey: ByteArray, coin: WalletCoin): ByteArray
}
