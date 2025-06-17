package com.bswap.wallet

import com.bswap.seed.MnemonicValidator
import foundation.metaplex.solanaeddsa.Keypair
import foundation.metaplex.solanapublickeys.PublicKey
import wallet.core.jni.HDWallet

/**
 * Default wallet derivation using the canonical Solana BIP-44 path `m/44'/501'/<accountIndex>'/0'`.
 */
class Bip44WalletDerivationStrategy : WalletDerivationStrategy {
    override fun deriveKeypair(mnemonic: List<String>, accountIndex: Int, passphrase: String): Keypair {
        require(MnemonicValidator.isValidMnemonic(mnemonic)) { "Invalid BIP-39 mnemonic" }
        val wallet = HDWallet(mnemonic.joinToString(" "), passphrase)
        val privateKey = wallet.getKey("m/44'/501'/${accountIndex}'/0'")
        val publicKey = privateKey.getPublicKeyEd25519()
        return Keypair(PublicKey(publicKey.data()), privateKey.data())
    }
}
