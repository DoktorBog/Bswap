package com.bswap.shared.wallet

/**
 * Wallet configuration containing both public key and private key for Solana operations.
 */
data class WalletConfig(
    val publicKey: String = "",
    val privateKey: String = ""
) {
    companion object {
        /**
         * Global wallet configuration instance
         */
        @Volatile
        private var instance: WalletConfig? = null
        
        /**
         * Initialize wallet configuration from a seed phrase
         * This should be called once at application startup
         */
        fun initializeFromSeed(
            seedPhrase: String,
            accountIndex: Int = 0,
            passphrase: String = ""
        ): WalletConfig {
            val config = SeedToWalletConverter.fromSeedPhraseString(seedPhrase, accountIndex, passphrase)
            instance = config
            return config
        }
        
        /**
         * Initialize wallet configuration from a seed phrase (list of words)
         * This should be called once at application startup
         */
        fun initializeFromSeed(
            seedPhrase: List<String>,
            accountIndex: Int = 0,
            passphrase: String = ""
        ): WalletConfig {
            val config = SeedToWalletConverter.fromSeedPhrase(seedPhrase, accountIndex, passphrase)
            instance = config
            return config
        }
        
        /**
         * Get the current wallet configuration
         * Returns the initialized instance or default if not initialized
         */
        fun current(): WalletConfig = instance ?: WalletConfig()
        
        /**
         * Check if wallet has been initialized from a seed
         */
        fun isInitialized(): Boolean = instance != null
    }
}
