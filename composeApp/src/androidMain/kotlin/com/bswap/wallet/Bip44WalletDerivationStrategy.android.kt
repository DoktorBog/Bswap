package com.bswap.wallet

import com.bswap.seed.MnemonicValidator
import com.bswap.shared.wallet.Keypair
import wallet.core.jni.HDWallet
import wallet.core.jni.CoinType

actual class Bip44WalletDerivationStrategy : WalletDerivationStrategy {
    override fun deriveKeypair(mnemonic: List<String>, accountIndex: Int, passphrase: String): Keypair {
        require(MnemonicValidator.isValidMnemonic(mnemonic)) { "Invalid BIP-39 mnemonic" }
        val wallet = HDWallet(mnemonic.joinToString(" "), passphrase)
        val privateKey = wallet.getKey(CoinType.SOLANA, "m/44'/501'/${accountIndex}'/0'")
        val publicKey = privateKey.getPublicKeyEd25519()
        return Keypair(publicKey.data(), privateKey.data())
    }
}