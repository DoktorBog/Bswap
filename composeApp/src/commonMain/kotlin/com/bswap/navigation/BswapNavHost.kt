package com.bswap.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.NavDisplay
import com.bswap.ui.settings.SettingsScreen
import com.bswap.ui.onboarding.ChoosePathScreen
import com.bswap.ui.onboarding.OnboardingWelcomeScreen
import com.bswap.ui.seed.ConfirmSeedScreen
import com.bswap.ui.seed.GenerateSeedScreen
import com.bswap.ui.wallet.ImportWalletScreen
import com.bswap.ui.home.HomeScreen
import com.bswap.ui.history.TransactionHistoryScreen
import com.bswap.ui.bot.BotControlScreen
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

@Composable
fun BswapNavHost(backStack: SnapshotStateList<NavKey>) {
    NavDisplay(backStack, enter = { slideInHorizontally(initialOffsetX = { it }) }, exit = { slideOutHorizontally(targetOffsetX = { -it }) }) { key ->
        when (key) {
            NavKey.Welcome -> OnboardingWelcomeScreen(backStack)
            NavKey.ChoosePath -> ChoosePathScreen(backStack)
            NavKey.GenerateSeed -> GenerateSeedScreen(backStack)
            is NavKey.ConfirmSeed -> ConfirmSeedScreen(key.words, backStack)
            NavKey.ImportWallet -> ImportWalletScreen(backStack)
            is NavKey.WalletHome -> HomeScreen(
                publicKey = key.publicKey,
                onSettings = { backStack.push(NavKey.AccountSettings(key.publicKey)) },
                onHistory = { backStack.push(NavKey.TransactionHistory(key.publicKey)) },
                onBotControl = { backStack.push(NavKey.BotControl) }
            )
            is NavKey.AccountSettings -> SettingsScreen(
                publicKey = key.publicKey,
                onBack = { backStack.pop() },
                onLogout = { backStack.replaceAll(NavKey.Welcome) }
            )
            is NavKey.TransactionHistory -> TransactionHistoryScreen(
                publicKey = key.publicKey,
                onBack = { backStack.pop() }
            )
            NavKey.BotControl -> BotControlScreen(
                onBack = { backStack.pop() }
            )
        }
    }
}
