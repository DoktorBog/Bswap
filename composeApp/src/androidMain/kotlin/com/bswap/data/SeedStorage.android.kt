package com.bswap.data

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bswap.app.appContext
import com.bswap.shared.wallet.Keypair
import com.bswap.shared.wallet.toBase58
import com.bswap.wallet.WalletDerivationStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64

private const val PREF_NAME = "seed_store"
private const val KEY_SEED = "seed"
private const val KEY_PUB = "pub_key"
private const val KEY_SECRET = "secret_key"

actual fun seedStorage(): SeedStorage = AndroidSeedStorage(appContext)

private class AndroidSeedStorage(private val context: Context) : SeedStorage {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun saveSeed(words: List<String>) {
        withContext(Dispatchers.IO) {
            prefs.edit(commit = true) { putString(KEY_SEED, words.joinToString(" ")) }
        }
    }

    override suspend fun loadSeed(): List<String>? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_SEED, null)?.split(" ")
    }

    override suspend fun savePublicKey(key: String) {
        withContext(Dispatchers.IO) {
            prefs.edit(commit = true) { putString(KEY_PUB, key) }
        }
    }

    override suspend fun loadPublicKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_PUB, null)
    }

    override suspend fun createWallet(
        mnemonic: List<String>,
        accountIndex: Int,
        strategy: WalletDerivationStrategy,
        coin: String
    ): Keypair = withContext(Dispatchers.Default) {
        val keypair = strategy.deriveKeypair(mnemonic, accountIndex, "")

        prefs.edit(commit = true) {
            putString(KEY_PUB, keypair.publicKey.toBase58())
            putString(KEY_SECRET, Base64.encodeToString(keypair.secretKey, Base64.DEFAULT))
        }

        keypair
    }

    override suspend fun loadPrivateKey(): ByteArray? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_SECRET, null)?.let { Base64.decode(it, Base64.DEFAULT) }
    }
}
