package com.bswap.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bswap.ui.account.AccountSettingsScreen
import com.bswap.ui.onboarding.ChoosePathScreen
import com.bswap.ui.onboarding.OnboardingWelcomeScreen
import com.bswap.ui.seed.ConfirmSeedScreen
import com.bswap.ui.seed.GenerateSeedScreen
import com.bswap.ui.wallet.ImportWalletScreen
import com.bswap.ui.wallet.WalletHomeScreen

/**
 * JavaScript implementation of [BswapNavHost] using a simple navigator and
 * `AnimatedContent` for animated transitions.
 */

@OptIn(ExperimentalAnimationApi::class)
@Composable
actual fun BswapNavHost() {
    val navigator = rememberMultiplatformNavigator(NavRoute.ONBOARD_WELCOME)
    val route = navigator.currentRoute
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            if (initialState.startsWith("onboard") && targetState.startsWith("onboard")) {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            } else {
                fadeIn() togetherWith fadeOut()
            }
        }
    ) { target ->
        when (target) {
            NavRoute.ONBOARD_WELCOME -> OnboardingWelcomeScreen(onStart = { navigator.navigate(NavRoute.ONBOARD_CHOOSE) })
            NavRoute.ONBOARD_CHOOSE -> ChoosePathScreen(
                onCreate = { navigator.navigate(NavRoute.GENERATE_SEED) },
                onImport = { navigator.navigate(NavRoute.IMPORT_WALLET) }
            )
            NavRoute.GENERATE_SEED -> {
                val seed = remember { List(12) { "word${it + 1}" } }
                GenerateSeedScreen(seedWords = seed, onCopy = {}, onNext = {
                    navigator.navigate(NavRoute.CONFIRM_SEED)
                })
            }
            NavRoute.CONFIRM_SEED -> {
                val seed = remember { List(12) { "word${it + 1}" } }
                BackHandler { }
                ConfirmSeedScreen(words = seed) {
                    navigator.navigate(NavRoute.walletHome("pubKey"), popUpTo = NavRoute.ONBOARD_WELCOME, inclusive = true)
                }
            }
            NavRoute.IMPORT_WALLET -> ImportWalletScreen(onImport = {
                navigator.navigate(NavRoute.walletHome("pubKey"), popUpTo = NavRoute.ONBOARD_WELCOME, inclusive = true)
            })
            NavRoute.ACCOUNT_SETTINGS -> AccountSettingsScreen(onExportSeed = {}, onChangeLanguage = {}, onLogout = {
                navigator.navigate(NavRoute.ONBOARD_WELCOME, popUpTo = NavRoute.ONBOARD_WELCOME, inclusive = true)
            })
            else -> WalletHomeScreen(publicKey = "pubKey", onSettings = { navigator.navigate(NavRoute.ACCOUNT_SETTINGS) })
        }
    }
}
