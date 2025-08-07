package com.bswap.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.BswapNavHost
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import com.bswap.navigation.replaceAll
import com.bswap.ui.WalletTheme
import com.bswap.data.seedStorage
import com.bswap.app.di.appModule
import org.koin.compose.KoinApplication
import org.koin.compose.KoinApplicationPreview

/**
 * Entry composable launching the Bswap navigation flow.
 */
@Composable
fun ComposeApp(backStack: SnapshotStateList<NavKey> = rememberBackStack()) {
    LaunchedEffect(Unit) {
        // Auto-navigate to bot dashboard if starting fresh
        if (backStack.firstOrNull() == NavKey.Welcome) {
            // Start at welcome screen, user can choose to proceed to bot dashboard
        }
    }
    WalletTheme {
        BswapNavHost(backStack)
    }
}

@Composable
fun BswapApp(backStack: SnapshotStateList<NavKey> = rememberBackStack()) {
    KoinApplication(application = { modules(appModule) }) {
        ComposeApp(backStack)
    }
}

