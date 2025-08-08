package com.bswap.shared.wallet

/**
 * Utility to initialize the wallet configuration at application startup
 */
object WalletInitializer {

    /**
     * Initialize the global wallet configuration from a seed phrase
     * This should be called once at application startup before using any wallet functionality
     *
     * @param seedPhrase The mnemonic seed phrase (12 or 24 words separated by spaces)
     * @param accountIndex The account index for BIP44 derivation (default: 0)
     * @param passphrase Optional passphrase for BIP39 seed generation (default: empty)
     * @return The initialized WalletConfig
     */
    fun initializeFromSeed(
        seedPhrase: String,
        accountIndex: Int = 0,
        passphrase: String = ""
    ): WalletConfig {
        return WalletConfig.initializeFromSeed(seedPhrase, accountIndex, passphrase)
    }

    /**
     * Initialize the global wallet configuration from environment variable or config file
     * This method looks for WALLET_SEED_PHRASE environment variable first, then falls back to default
     *
     * @param defaultSeedPhrase Fallback seed phrase if environment variable is not set
     * @param accountIndex The account index for BIP44 derivation (default: 0)
     * @param passphrase Optional passphrase for BIP39 seed generation (default: empty)
     * @return The initialized WalletConfig
     */
    fun initializeFromEnvironment(
        defaultSeedPhrase: String = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        accountIndex: Int = 0,
        passphrase: String = ""
    ): WalletConfig {
        // Try to get seed phrase from environment variable
        val seedPhrase = System.getenv("WALLET_SEED_PHRASE") ?: defaultSeedPhrase
        return initializeFromSeed(seedPhrase, accountIndex, passphrase)
    }

    /**
     * Initialize from seed file using WalletEngine
     * This will look for existing seed file or create a new one if needed
     *
     * @param autoCreate Whether to create a new seed file if none exists (default: true)
     * @return The initialized WalletConfig
     */
    fun initializeFromFile(autoCreate: Boolean = false): WalletConfig {
        return WalletEngine.initialize(autoCreate)
    }

    /**
     * Initialize from specific seed file
     *
     * @param seedFileName Name of the seed file to use
     * @param accountIndex The account index for BIP44 derivation (default: 0)
     * @param passphrase Optional passphrase for BIP39 seed generation (default: empty)
     * @return The initialized WalletConfig
     */
    fun initializeFromSeedFile(
        seedFileName: String,
        accountIndex: Int = 0,
        passphrase: String = ""
    ): WalletConfig {
        return WalletEngine.initializeFromSeedFile(seedFileName, accountIndex, passphrase)
    }

    /**
     * Check if the wallet has been properly initialized
     */
    fun isInitialized(): Boolean = WalletConfig.isInitialized()

    /**
     * Get the current wallet configuration
     * Throws an exception if not initialized
     */
    fun requireWallet(): WalletConfig {
        require(isInitialized()) { "Wallet has not been initialized. Call WalletInitializer.initializeFromSeed() first." }
        return WalletConfig.current()
    }
}
