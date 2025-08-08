package com.bswap.data

import com.bswap.shared.wallet.Keypair
import com.bswap.shared.wallet.toBase58
import com.bswap.wallet.WalletDerivationStrategy

actual fun seedStorage(): SeedStorage = InMemorySeedStorage

private object InMemorySeedStorage : SeedStorage {
    private var seed: List<String>? = null
    private var publicKey: String? = null
    private var secret: ByteArray? = null

    override suspend fun saveSeed(words: List<String>) { seed = words }
    override suspend fun loadSeed(): List<String>? = seed
    override suspend fun savePublicKey(key: String) { publicKey = key }
    override suspend fun loadPublicKey(): String? = publicKey

    override suspend fun createWallet(
        mnemonic: List<String>,
        accountIndex: Int,
        strategy: WalletDerivationStrategy,
        coin: String
    ): Keypair {
        val keypair = strategy.deriveKeypair(mnemonic, accountIndex, "")
        secret = keypair.secretKey
        publicKey = keypair.publicKey.toBase58()
        return keypair
    }

    override suspend fun loadPrivateKey(): ByteArray? = secret
}
