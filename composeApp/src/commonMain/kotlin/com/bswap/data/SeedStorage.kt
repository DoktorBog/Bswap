package com.bswap.data

import com.bswap.shared.wallet.Keypair
import com.bswap.wallet.Bip44WalletDerivationStrategy
import com.bswap.wallet.WalletDerivationStrategy
import wallet.core.jni.CoinType

interface SeedStorage {
    suspend fun saveSeed(words: List<String>)
    suspend fun loadSeed(): List<String>?
    suspend fun savePublicKey(key: String)
    suspend fun loadPublicKey(): String?

    suspend fun createWallet(
        mnemonic: List<String>,
        accountIndex: Int = 0,
        strategy: WalletDerivationStrategy = Bip44WalletDerivationStrategy(),
        coin: CoinType = CoinType.SOLANA,
    ): Keypair

    suspend fun loadPrivateKey(): ByteArray?
}

expect fun seedStorage(): SeedStorage
