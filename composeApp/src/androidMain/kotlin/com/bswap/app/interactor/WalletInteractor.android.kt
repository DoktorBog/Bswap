package com.bswap.app.interactor

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bswap.app.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.crypto.MnemonicCode
import org.sol4k.derive.Ed25519HDKeyDerivation
import org.sol4k.keys.Keypair
import java.util.Base64

private const val PREF_NAME = "wallet_prefs"
private const val KEY_MNEMONIC = "mnemonic"
private const val KEY_SECRET_KEY = "secret_key"

actual fun walletInteractor(): WalletInteractor = AndroidWalletInteractor(appContext)

private class AndroidWalletInteractor(
    private val context: Context
) : WalletInteractor {

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

    override suspend fun createWallet(mnemonic: List<String>): Keypair =
        withContext(Dispatchers.Default) {
            require(mnemonic.size in setOf(12, 15, 18, 21, 24)) {
                "Mnemonic must have a valid word count"
            }

            val seed = MnemonicCode.INSTANCE.toSeed(mnemonic, "")
            val derived = Ed25519HDKeyDerivation.derivePath(intArrayOf(44, 501, 0, 0), seed)
            val keypair = Keypair.fromSeed(derived.key)

            prefs.edit(commit = true) {
                putString(KEY_MNEMONIC, mnemonic.joinToString(" "))
                putString(KEY_SECRET_KEY, Base64.getEncoder().encodeToString(keypair.secretKey))
            }

            keypair
        }
}
