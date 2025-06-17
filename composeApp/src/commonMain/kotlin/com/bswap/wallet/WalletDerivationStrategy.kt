package com.bswap.wallet

import foundation.metaplex.solanaeddsa.Keypair

/**
 * Abstraction for deriving Solana [Keypair]s from a BIP-39 mnemonic.
 */
interface WalletDerivationStrategy {
    /**
     * Derive a deterministic [Keypair] using the supplied [mnemonic].
     *
     * @param mnemonic the BIP-39 word list
     * @param accountIndex the BIP-44 account index to derive. Defaults to `0`.
     * @param passphrase optional extra passphrase as described in BIP-39.
     */
    fun deriveKeypair(mnemonic: List<String>, accountIndex: Int = 0, passphrase: String = ""): Keypair
}
