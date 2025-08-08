package com.bswap.shared.wallet

import wallet.core.jni.CoinType

class LoggingWalletDecorator(private val delegate: WalletCoreAdapter) : WalletCoreAdapter {
    override fun generateKeypair(coin: CoinType): Keypair {
        val pair = delegate.generateKeypair(coin)
        println("[WalletCore] Generated keypair")
        return pair
    }

    override fun addressFromPrivateKey(privateKey: ByteArray, coin: CoinType): String {
        val address = delegate.addressFromPrivateKey(privateKey, coin)
        println("[WalletCore] Derived address $address")
        return address
    }

    override fun sign(message: ByteArray, privateKey: ByteArray, coin: CoinType): ByteArray {
        println("[WalletCore] Signing message of ${'$'}{message.size} bytes")
        return delegate.sign(message, privateKey, coin)
    }
}
