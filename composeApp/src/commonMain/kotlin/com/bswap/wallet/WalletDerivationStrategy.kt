package com.bswap.wallet

import com.bswap.shared.wallet.Keypair

interface WalletDerivationStrategy {
    fun deriveKeypair(mnemonic: List<String>, accountIndex: Int = 0, passphrase: String = ""): Keypair
}
