package com.bswap.shared.wallet

import com.bswap.shared.wallet.WalletCoin

class LoggingWalletDecorator(private val delegate: WalletCoreAdapter) : WalletCoreAdapter {
    override fun generateKeypair(coin: WalletCoin): Keypair {
        val pair = delegate.generateKeypair(coin)
        println("[WalletCore] Generated keypair")
        return pair
    }

    override fun addressFromPrivateKey(privateKey: ByteArray, coin: WalletCoin): String {
        val address = delegate.addressFromPrivateKey(privateKey, coin)
        println("[WalletCore] Derived address $address")
        return address
    }

    override fun sign(message: ByteArray, privateKey: ByteArray, coin: WalletCoin): ByteArray {
        println("[WalletCore] Signing message of ${'$'}{message.size} bytes")
        return delegate.sign(message, privateKey, coin)
    }
}
