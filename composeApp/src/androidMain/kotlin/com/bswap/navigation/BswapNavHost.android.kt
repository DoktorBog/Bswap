package com.bswap.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bswap.ui.onboarding.ChoosePathScreen
import com.bswap.ui.onboarding.OnboardingWelcomeScreen
import com.bswap.ui.seed.ConfirmSeedScreen
import com.bswap.ui.seed.GenerateSeedScreen
import com.bswap.ui.wallet.ImportWalletScreen
import com.bswap.ui.wallet.WalletHomeScreen

/**
 * Android implementation of [BswapNavHost] using `AnimatedNavHost` from
 * Accompanist with horizontal slide transitions between onboarding screens.
 */
import com.bswap.ui.account.AccountSettingsScreen
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController

@OptIn(ExperimentalAnimationApi::class)
@Composable
actual fun BswapNavHost() {
    val navigator = rememberMultiplatformNavigator(NavRoute.ONBOARD_WELCOME)
    val navController = navigator.navController
    AnimatedNavHost(
        navController = navController,
        startDestination = NavRoute.ONBOARD_WELCOME,
        enterTransition = { slideInHorizontally(animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        composable(NavRoute.ONBOARD_WELCOME) {
            OnboardingWelcomeScreen(onStart = { navController.navigateToOnboardChoose() })
        }
        composable(NavRoute.ONBOARD_CHOOSE) {
            ChoosePathScreen(
                onCreate = { navController.navigateToGenerateSeed() },
                onImport = { navController.navigateToImportWallet() }
            )
        }
        composable(NavRoute.GENERATE_SEED) {
            val seed = remember { List(12) { "word${it + 1}" } }
            GenerateSeedScreen(seedWords = seed, onCopy = {}, onNext = {
                navController.navigateToConfirmSeed(seed)
            })
        }
        composable(
            route = "${NavRoute.CONFIRM_SEED}/{${NavRoute.ARG_SEED}}",
            arguments = listOf(navArgument(NavRoute.ARG_SEED) { type = NavType.StringType })
        ) { backStackEntry ->
            val seed = NavRoute.parseSeed(backStackEntry.arguments?.getString(NavRoute.ARG_SEED) ?: "")
            BackHandler(enabled = true) {}
            ConfirmSeedScreen(words = seed) {
                navController.navigateToWalletHome("pubKey", popUpTo = NavRoute.ONBOARD_WELCOME, inclusive = true)
            }
        }
        composable(NavRoute.IMPORT_WALLET) {
            ImportWalletScreen(onImport = {
                navController.navigateToWalletHome("pubKey", popUpTo = NavRoute.ONBOARD_WELCOME, inclusive = true)
            })
        }
        composable(
            route = "${NavRoute.WALLET_HOME}/{publicKey}",
            arguments = listOf(navArgument("publicKey") { type = NavType.StringType })
        ) { backStackEntry ->
            val key = backStackEntry.arguments?.getString("publicKey") ?: ""
            WalletHomeScreen(publicKey = key, onSettings = { navController.navigateToAccountSettings() })
        }
        composable(NavRoute.ACCOUNT_SETTINGS) {
            AccountSettingsScreen(onExportSeed = {}, onChangeLanguage = {}, onLogout = {
                navController.navigateToOnboardWelcome(popUpTo = NavRoute.ONBOARD_WELCOME, inclusive = true)
            })
        }
    }
}
