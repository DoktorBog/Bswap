package com.bswap.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.NavDisplay
import com.bswap.ui.onboarding.OnboardingWelcomeScreen
import com.bswap.ui.bot.BotDashboardScreen
import com.bswap.ui.bot.BotSettingsScreen
import com.bswap.ui.bot.BotAnalyticsScreen
import com.bswap.ui.bot.BotWalletScreen
import com.bswap.ui.bot.BotHistoryScreen
import com.bswap.ui.bot.BotAlertsScreen
import com.bswap.ui.history.TransactionHistoryScreen
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

@Composable
fun BswapNavHost(backStack: SnapshotStateList<NavKey>) {
    NavDisplay(backStack, enter = { slideInHorizontally(initialOffsetX = { it }) }, exit = { slideOutHorizontally(targetOffsetX = { -it }) }) { key ->
        when (key) {
            NavKey.Welcome -> OnboardingWelcomeScreen(backStack)
            NavKey.BotDashboard -> BotDashboardScreen(
                onNavigateToSettings = { backStack.push(NavKey.BotSettings) },
                onNavigateToAnalytics = { backStack.push(NavKey.BotAnalytics) },
                onNavigateToWallet = { backStack.push(NavKey.BotWallet) },
                onNavigateToHistory = { backStack.push(NavKey.BotHistory) },
                onNavigateToAlerts = { backStack.push(NavKey.BotAlerts) }
            )
            NavKey.BotSettings -> BotSettingsScreen(
                onBack = { backStack.pop() }
            )
            NavKey.BotAnalytics -> BotAnalyticsScreen(
                onBack = { backStack.pop() }
            )
            NavKey.BotWallet -> BotWalletScreen(
                onBack = { backStack.pop() },
                onNavigateToTransactionHistory = { publicKey -> 
                    backStack.push(NavKey.WalletTransactionHistory(publicKey))
                }
            )
            NavKey.BotHistory -> BotHistoryScreen(
                onBack = { backStack.pop() }
            )
            NavKey.BotAlerts -> BotAlertsScreen(
                onBack = { backStack.pop() }
            )
            is NavKey.WalletTransactionHistory -> TransactionHistoryScreen(
                publicKey = key.publicKey,
                onBack = { backStack.pop() }
            )
        }
    }
}
