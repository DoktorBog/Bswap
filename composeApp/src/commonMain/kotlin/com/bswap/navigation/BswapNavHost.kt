package com.bswap.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.NavDisplay
import com.bswap.navigation.replaceAll
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
import com.bswap.trading.TradingApp
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

@Composable
fun BswapNavHost(backStack: SnapshotStateList<NavKey>) {
    // Create HTTP client for trading API
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }
    
    NavDisplay(backStack, enter = { slideInHorizontally(initialOffsetX = { it }) }, exit = { slideOutHorizontally(targetOffsetX = { -it }) }) { key ->
        when (key) {
            NavKey.Welcome -> OnboardingWelcomeScreen(backStack)
            NavKey.BotDashboard -> BotDashboardScreen(
                onNavigateToSettings = { backStack.push(NavKey.BotSettings) },
                onNavigateToAnalytics = { backStack.push(NavKey.BotAnalytics) },
                onNavigateToWallet = { backStack.push(NavKey.BotWallet) },
                onNavigateToHistory = { backStack.push(NavKey.BotHistory) },
                onNavigateToAlerts = { backStack.push(NavKey.BotAlerts) },
                onNavigateToTrading = { backStack.push(NavKey.Trading) }
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
            NavKey.Trading -> TradingApp(
                httpClient = httpClient,
                onBackClick = { backStack.pop() },
                modifier = androidx.compose.ui.Modifier.fillMaxSize()
            )
            is NavKey.WalletTransactionHistory -> TransactionHistoryScreen(
                publicKey = key.publicKey,
                onBack = { backStack.pop() }
            )
        }
    }
}
