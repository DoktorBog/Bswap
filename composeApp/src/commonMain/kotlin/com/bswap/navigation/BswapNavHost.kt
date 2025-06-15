package com.bswap.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.NavDisplay
import com.bswap.ui.account.AccountSettingsScreen
import com.bswap.ui.onboarding.ChoosePathScreen
import com.bswap.ui.onboarding.OnboardingWelcomeScreen
import com.bswap.ui.seed.ConfirmSeedScreen
import com.bswap.ui.seed.GenerateSeedScreen
import com.bswap.ui.wallet.ImportWalletScreen
import com.bswap.ui.wallet.WalletHomeScreen
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
            is NavKey.WalletHome -> WalletHomeScreen(key.publicKey, backStack)
            NavKey.AccountSettings -> AccountSettingsScreen(backStack)
        }
    }
}
