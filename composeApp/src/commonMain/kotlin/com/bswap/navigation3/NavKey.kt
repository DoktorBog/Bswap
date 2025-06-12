package com.bswap.navigation3

import kotlinx.serialization.Serializable
import kotlinx.parcelize.Parcelize
import androidx.compose.runtime.Immutable
import android.os.Parcelable

/**
 * Keys describing navigation destinations.
 */
@Immutable
sealed interface NavKey : Parcelable {
    @Serializable
    @Parcelize
    object Welcome : NavKey

    @Serializable
    @Parcelize
    object ChoosePath : NavKey

    @Serializable
    @Parcelize
    object GenerateSeed : NavKey

    @Serializable
    @Parcelize
    data class ConfirmSeed(val seed: List<String>) : NavKey

    @Serializable
    @Parcelize
    object ImportWallet : NavKey

    @Serializable
    @Parcelize
    data class WalletHome(val publicKey: String) : NavKey

    @Serializable
    @Parcelize
    object AccountSettings : NavKey
}
