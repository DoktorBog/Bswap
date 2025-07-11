package com.bswap.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.BswapNavHost
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import com.bswap.navigation.replaceAll
import com.bswap.ui.WalletTheme
import androidx.compose.ui.tooling.preview.Preview
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
        val pubKey = seedStorage().loadPublicKey()
        if (pubKey != null && backStack.firstOrNull() == NavKey.Welcome) {
            backStack.replaceAll(NavKey.WalletHome(pubKey))
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

/** Preview of [ComposeApp]. */
@Preview
@Composable
private fun ComposeAppPreview() {
    KoinApplicationPreview(application = { modules(appModule) }) {
        ComposeApp()
    }
}
