package com.bswap.data

import com.bswap.shared.wallet.Keypair
import com.bswap.shared.wallet.SeedToWalletConverter
import com.bswap.shared.wallet.WalletConfig
import com.bswap.wallet.WalletDerivationStrategy

interface SeedStorage {
    suspend fun saveSeed(words: List<String>)
    suspend fun loadSeed(): List<String>?
    suspend fun savePublicKey(key: String)
    suspend fun loadPublicKey(): String?

    suspend fun createWallet(
        mnemonic: List<String>,
        accountIndex: Int = 0,
        strategy: WalletDerivationStrategy = SharedWalletStrategy(),
        coin: String = "SOLANA", // Simplified to avoid CoinType dependency
    ): Keypair

    suspend fun loadPrivateKey(): ByteArray?
}

/**
 * Implementation that uses the shared wallet system
 */
class SharedWalletStrategy : WalletDerivationStrategy {
    override fun deriveKeypair(mnemonic: List<String>, accountIndex: Int, passphrase: String): Keypair {
        val walletConfig = SeedToWalletConverter.fromSeedPhrase(mnemonic, accountIndex, passphrase)
        // Convert from WalletConfig to legacy Keypair format for compatibility
        return Keypair(
            publicKey = walletConfig.publicKey.encodeToByteArray(), // Simplified conversion
            secretKey = walletConfig.privateKey.encodeToByteArray()  // Simplified conversion
        )
    }
}

expect fun seedStorage(): SeedStorage
