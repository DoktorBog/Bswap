package com.bswap.shared.wallet

import wallet.core.jni.CoinType
import kotlin.system.measureTimeMillis

class MetricsWalletDecorator(private val delegate: WalletCoreAdapter) : WalletCoreAdapter {
    override fun generateKeypair(coin: CoinType): Keypair {
        lateinit var result: Keypair
        val time = measureTimeMillis { result = delegate.generateKeypair(coin) }
        println("[Metrics] generateKeypair took ${time}ms")
        return result
    }

    override fun addressFromPrivateKey(privateKey: ByteArray, coin: CoinType): String {
        lateinit var addr: String
        val time = measureTimeMillis { addr = delegate.addressFromPrivateKey(privateKey, coin) }
        println("[Metrics] addressFromPrivateKey took ${time}ms")
        return addr
    }

    override fun sign(message: ByteArray, privateKey: ByteArray, coin: CoinType): ByteArray {
        lateinit var sig: ByteArray
        val time = measureTimeMillis { sig = delegate.sign(message, privateKey, coin) }
        println("[Metrics] sign took ${time}ms")
        return sig
    }
}
