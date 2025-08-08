package com.bswap.shared.wallet

/**
 * Utility to convert mnemonic seed phrases to Solana wallet keys
 */
expect object SeedToWalletConverter {
    /**
     * Converts a mnemonic seed phrase to Solana public and private keys
     * @param seedPhrase List of mnemonic words (typically 12 or 24 words)
     * @param accountIndex The account index for derivation (default: 0)
     * @param passphrase Optional passphrase for BIP39 seed generation
     * @return WalletConfig with derived public and private keys
     */
    fun fromSeedPhrase(
        seedPhrase: List<String>,
        accountIndex: Int = 0,
        passphrase: String = ""
    ): WalletConfig
    
    /**
     * Converts a mnemonic seed phrase string to Solana public and private keys
     * @param seedPhrase Space-separated mnemonic words
     * @param accountIndex The account index for derivation (default: 0) 
     * @param passphrase Optional passphrase for BIP39 seed generation
     * @return WalletConfig with derived public and private keys
     */
    fun fromSeedPhraseString(
        seedPhrase: String,
        accountIndex: Int = 0,
        passphrase: String = ""
    ): WalletConfig
}