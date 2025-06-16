package com.bswap.data

actual fun seedStorage(): SeedStorage = InMemorySeedStorage

private object InMemorySeedStorage : SeedStorage {
    private var seed: List<String>? = null
    private var publicKey: String? = null

    override suspend fun saveSeed(words: List<String>) { seed = words }
    override suspend fun loadSeed(): List<String>? = seed
    override suspend fun savePublicKey(key: String) { publicKey = key }
    override suspend fun loadPublicKey(): String? = publicKey
}
