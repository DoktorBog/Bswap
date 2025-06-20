package com.bswap.shared.wallet

import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey

/** Simple adapter exposing the WalletCore functionality we rely on. */
interface WalletCoreAdapter {
    fun generateKeypair(coin: CoinType): Keypair
    fun addressFromPrivateKey(privateKey: ByteArray, coin: CoinType): String
    fun sign(message: ByteArray, privateKey: ByteArray, coin: CoinType): ByteArray
}
