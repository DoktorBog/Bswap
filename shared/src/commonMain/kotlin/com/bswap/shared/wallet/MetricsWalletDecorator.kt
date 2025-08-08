package com.bswap.shared.wallet

import com.bswap.shared.wallet.WalletCoin
import kotlin.system.measureTimeMillis

class MetricsWalletDecorator(private val delegate: WalletCoreAdapter) : WalletCoreAdapter {
    override fun generateKeypair(coin: WalletCoin): Keypair {
        lateinit var result: Keypair
        val time = measureTimeMillis { result = delegate.generateKeypair(coin) }
        println("[Metrics] generateKeypair took ${time}ms")
        return result
    }

    override fun addressFromPrivateKey(privateKey: ByteArray, coin: WalletCoin): String {
        lateinit var addr: String
        val time = measureTimeMillis { addr = delegate.addressFromPrivateKey(privateKey, coin) }
        println("[Metrics] addressFromPrivateKey took ${time}ms")
        return addr
    }

    override fun sign(message: ByteArray, privateKey: ByteArray, coin: WalletCoin): ByteArray {
        lateinit var sig: ByteArray
        val time = measureTimeMillis { sig = delegate.sign(message, privateKey, coin) }
        println("[Metrics] sign took ${time}ms")
        return sig
    }
}
