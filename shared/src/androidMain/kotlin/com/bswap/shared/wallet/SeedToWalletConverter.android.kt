package com.bswap.shared.wallet

import wallet.core.jni.HDWallet
import wallet.core.jni.CoinType

/**
 * Android implementation of SeedToWalletConverter using WalletCore
 */
actual object SeedToWalletConverter {
    actual fun fromSeedPhrase(
        seedPhrase: List<String>,
        accountIndex: Int,
        passphrase: String
    ): WalletConfig {
        val mnemonicString = seedPhrase.joinToString(" ")
        val wallet = HDWallet(mnemonicString, passphrase)
        
        // Derive Solana keys using BIP44 path: m/44'/501'/accountIndex'/0'
        val privateKey = wallet.getKey(CoinType.SOLANA, "m/44'/501'/${accountIndex}'/0'")
        val publicKey = privateKey.getPublicKeyEd25519()
        
        return WalletConfig(
            publicKey = publicKey.data().toBase58(),
            privateKey = privateKey.data().toBase58()
        )
    }
    
    actual fun fromSeedPhraseString(
        seedPhrase: String,
        accountIndex: Int,
        passphrase: String
    ): WalletConfig {
        return fromSeedPhrase(seedPhrase.split(" "), accountIndex, passphrase)
    }
    
    actual fun getEthereumAddress(
        seedPhrase: String,
        accountIndex: Int,
        passphrase: String
    ): String {
        val wallet = HDWallet(seedPhrase, passphrase)
        val privateKey = wallet.getKey(CoinType.ETHEREUM, "m/44'/60'/${accountIndex}'/0/0")
        val publicKey = privateKey.getPublicKeySecp256k1(false)
        
        // Get address from public key
        val address = wallet.getAddressForCoin(CoinType.ETHEREUM)
        return address
    }
    
    actual fun getEthereumPrivateKey(
        seedPhrase: String,
        accountIndex: Int,
        passphrase: String
    ): String {
        val wallet = HDWallet(seedPhrase, passphrase)
        val privateKey = wallet.getKey(CoinType.ETHEREUM, "m/44'/60'/${accountIndex}'/0/0")
        return "0x" + privateKey.data().joinToString("") { "%02x".format(it) }
    }
}