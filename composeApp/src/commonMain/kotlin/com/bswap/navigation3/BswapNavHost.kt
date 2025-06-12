package com.bswap.navigation3

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bswap.ui.onboarding.OnboardingWelcomeScreen
import com.bswap.ui.onboarding.ChoosePathScreen
import com.bswap.ui.seed.GenerateSeedScreen
import com.bswap.ui.seed.ConfirmSeedScreen
import com.bswap.ui.wallet.ImportWalletScreen
import com.bswap.ui.wallet.WalletHomeScreen
import com.bswap.ui.account.AccountSettingsScreen

/**
 * Root navigation host using Navigation 3 style back stack.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BswapNavHost() {
    val backStack = rememberNavBackStack(NavKey.Welcome)
    val navigator = rememberNavigator(backStack)
    val current = backStack.last()
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
        }
    ) { key ->
        when (key) {
            NavKey.Welcome -> OnboardingWelcomeScreen(onStart = { navigator.push(NavKey.ChoosePath) })
            NavKey.ChoosePath -> ChoosePathScreen(
                onCreate = { navigator.push(NavKey.GenerateSeed) },
                onImport = { navigator.push(NavKey.ImportWallet) }
            )
            NavKey.GenerateSeed -> {
                val seed = remember { List(12) { "word${'$'}{it + 1}" } }
                GenerateSeedScreen(seedWords = seed, onCopy = {}, onNext = { navigator.push(NavKey.ConfirmSeed(seed)) })
            }
            is NavKey.ConfirmSeed -> {
                ConfirmSeedScreen(words = key.seed) {
                    navigator.push(NavKey.WalletHome("pubKey"))
                }
            }
            NavKey.ImportWallet -> ImportWalletScreen(onImport = { navigator.push(NavKey.WalletHome("pubKey")) })
            is NavKey.WalletHome -> WalletHomeScreen(publicKey = key.publicKey, onSettings = { navigator.push(NavKey.AccountSettings) })
            NavKey.AccountSettings -> AccountSettingsScreen(onExportSeed = {}, onChangeLanguage = {}, onLogout = { navigator.pop(); navigator.pop(); })
        }
    }
}
