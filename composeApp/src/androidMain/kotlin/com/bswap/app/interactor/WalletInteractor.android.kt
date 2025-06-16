package com.bswap.app.interactor

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bswap.app.appContext
import org.sol4k.Keypair

private const val PREF_NAME = "wallet_prefs"
private const val KEY_MNEMONIC = "mnemonic"

actual fun walletInteractor(): WalletInteractor = AndroidWalletInteractor(appContext)

private class AndroidWalletInteractor(private val context: Context) : WalletInteractor {
    override suspend fun createWallet(mnemonic: List<String>): Keypair {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs.edit().putString(KEY_MNEMONIC, mnemonic.joinToString(" ")).apply()
        return Keypair.generate()
    }
}
