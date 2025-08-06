package com.bswap.navigation

import kotlinx.serialization.Serializable


@Serializable
sealed interface NavKey {
    @Serializable object Welcome : NavKey
    @Serializable object ChoosePath : NavKey
    @Serializable object GenerateSeed : NavKey
    @Serializable data class ConfirmSeed(val words: List<String>) : NavKey
    @Serializable object ImportWallet : NavKey
    @Serializable data class WalletHome(val publicKey: String) : NavKey
    @Serializable data class AccountSettings(val publicKey: String) : NavKey
    @Serializable data class TransactionHistory(val publicKey: String) : NavKey
    @Serializable object BotControl : NavKey
}
