package com.bswap.wallet

import com.bswap.crypto.Mnemonic
import com.bswap.crypto.Slip10
import com.bswap.seed.MnemonicValidator
import foundation.metaplex.solanaeddsa.Keypair
import foundation.metaplex.solanaeddsa.SolanaEddsa

/**
 * Default wallet derivation using the canonical Solana BIP-44 path `m/44'/501'/<accountIndex>'/0'`.
 */
class Bip44WalletDerivationStrategy : WalletDerivationStrategy {
    override fun deriveKeypair(mnemonic: List<String>, accountIndex: Int, passphrase: String): Keypair {
        require(MnemonicValidator.isValidMnemonic(mnemonic)) { "Invalid BIP-39 mnemonic" }
        val seed = Mnemonic.toSeed(mnemonic, passphrase)
        val derived = Slip10.derivePath(intArrayOf(44, 501, accountIndex, 0), seed)
        return SolanaEddsa.createKeypairFromSeed(derived)
    }
}
