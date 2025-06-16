package com.bswap.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.MasterKey
import com.bswap.app.appContext
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.dataStore by preferencesDataStore("seed_store")

actual fun seedStorage(): SeedStorage = AndroidSeedStorage(appContext)

private class AndroidSeedStorage(private val context: Context) : SeedStorage {
    private val seedKey = stringPreferencesKey("seed")
    private val pubKey = stringPreferencesKey("pub_key")
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private fun secretKey(): SecretKey {
        val keyBytes = MasterKey.DEFAULT_MASTER_KEY_ALIAS.toByteArray()
        return SecretKeySpec(keyBytes, 0, 32, "AES")
    }

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = secretKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray())
        return (iv + encrypted).joinToString(separator = ",") { it.toString() }
    }

    private fun decrypt(data: String): String {
        val bytes = data.split(',').map { it.toByte() }.toByteArray()
        val iv = bytes.sliceArray(0 until 12)
        val enc = bytes.sliceArray(12 until bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = secretKey()
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(enc))
    }

    override suspend fun saveSeed(words: List<String>) {
        val enc = encrypt(words.joinToString(" "))
        context.dataStore.edit { it[seedKey] = enc }
    }

    override suspend fun loadSeed(): List<String>? {
        val enc = context.dataStore.data.first()[seedKey] ?: return null
        return decrypt(enc).split(" ")
    }

    override suspend fun savePublicKey(key: String) {
        context.dataStore.edit { it[pubKey] = key }
    }

    override suspend fun loadPublicKey(): String? {
        return context.dataStore.data.first()[pubKey]
    }
}
