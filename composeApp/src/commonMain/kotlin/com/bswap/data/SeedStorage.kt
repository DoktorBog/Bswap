package com.bswap.data

import foundation.metaplex.solanaeddsa.Keypair

interface SeedStorage {
    suspend fun saveSeed(words: List<String>)
    suspend fun loadSeed(): List<String>?
    suspend fun savePublicKey(key: String)
    suspend fun loadPublicKey(): String?

    /**
     * Create a deterministic wallet from the given mnemonic and persist the
     * resulting secret and public keys securely.
     */
    suspend fun createWallet(mnemonic: List<String>): Keypair

    /** Return the previously stored secret key, or null if absent. */
    suspend fun loadPrivateKey(): ByteArray?
}

expect fun seedStorage(): SeedStorage
