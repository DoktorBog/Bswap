package com.bswap.shared.wallet

/**
 * Wallet engine that provides wallet configuration from seed files
 * Reads seed phrases from files in the shared folder and generates wallet configs
 */
expect object WalletEngine {
    
    /**
     * Initialize wallet from seed file in shared folder
     * @param seedFileName Name of the seed file (e.g., "wallet.seed")
     * @param accountIndex BIP44 account index (default: 0)
     * @param passphrase Optional BIP39 passphrase (default: empty)
     * @return Initialized WalletConfig
     */
    fun initializeFromSeedFile(
        seedFileName: String = "wallet.seed",
        accountIndex: Int = 0,
        passphrase: String = ""
    ): WalletConfig
    
    /**
     * Create a new seed file with a generated mnemonic
     * @param seedFileName Name of the seed file to create
     * @param wordCount Number of mnemonic words (12 or 24)
     * @return Generated seed phrase
     */
    fun createSeedFile(
        seedFileName: String = "wallet.seed",
        wordCount: Int = 12
    ): String
    
    /**
     * Check if seed file exists
     * @param seedFileName Name of the seed file to check
     * @return True if file exists
     */
    fun seedFileExists(seedFileName: String = "wallet.seed"): Boolean
    
    /**
     * Get the path to the shared folder where seed files are stored
     * @return Path to shared folder
     */
    fun getSharedFolderPath(): String
    
    /**
     * Initialize wallet engine and set up wallet configuration
     * This will look for existing seed file or create a new one if needed
     * @param autoCreate Whether to create a new seed file if none exists (default: true)
     * @return Initialized WalletConfig
     */
    fun initialize(autoCreate: Boolean = true): WalletConfig
}