package com.bswap.wallet

import com.bswap.seed.MnemonicValidator
import com.bswap.shared.wallet.Keypair

actual class Bip44WalletDerivationStrategy : WalletDerivationStrategy {
    override fun deriveKeypair(mnemonic: List<String>, accountIndex: Int, passphrase: String): Keypair {
        require(MnemonicValidator.isValidMnemonic(mnemonic)) { "Invalid BIP-39 mnemonic" }
        // Desktop implementation - simplified for now
        // In a real implementation, you'd use a JVM-compatible BIP44 library
        throw UnsupportedOperationException("HDWallet not supported on desktop, use Android build")
    }
}