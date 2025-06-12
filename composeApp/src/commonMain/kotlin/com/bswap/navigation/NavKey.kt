package com.bswap.navigation

import kotlinx.serialization.Serializable
import androidx.compose.runtime.saveable.Parcelable

@Serializable
sealed interface NavKey : Parcelable {
    @Serializable object Welcome : NavKey
    @Serializable object ChoosePath : NavKey
    @Serializable object GenerateSeed : NavKey
    @Serializable data class ConfirmSeed(val words: List<String>) : NavKey
    @Serializable object ImportWallet : NavKey
    @Serializable data class WalletHome(val publicKey: String) : NavKey
    @Serializable object AccountSettings : NavKey
}
