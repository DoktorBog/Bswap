package com.bswap.seed

import com.bswap.shared.wallet.Keypair
import com.bswap.shared.wallet.WalletCoreAdapterImpl
import wallet.core.jni.CoinType

object JitoService {
    private val adapter = WalletCoreAdapterImpl()
    fun generateKeypair(coin: CoinType = CoinType.SOLANA): Keypair = adapter.generateKeypair(coin)
}
