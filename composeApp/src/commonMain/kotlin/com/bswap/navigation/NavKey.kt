package com.bswap.navigation

import kotlinx.serialization.Serializable


@Serializable
sealed interface NavKey {
    @Serializable object Welcome : NavKey
    @Serializable object BotDashboard : NavKey
    @Serializable object BotSettings : NavKey
    @Serializable object BotAnalytics : NavKey
    @Serializable object BotWallet : NavKey
    @Serializable object BotHistory : NavKey
    @Serializable object BotAlerts : NavKey
}
