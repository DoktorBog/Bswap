package com.bswap.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface SeedStorage {
    suspend fun saveSeed(words: List<String>)
    suspend fun loadSeed(): List<String>?
    suspend fun savePublicKey(key: String)
    suspend fun loadPublicKey(): String?
}

expect fun seedStorage(): SeedStorage
