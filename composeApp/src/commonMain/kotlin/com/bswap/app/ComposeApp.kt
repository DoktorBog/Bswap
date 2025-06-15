package com.bswap.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.BswapNavHost
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import com.bswap.ui.UiTheme
import androidx.compose.ui.tooling.preview.Preview

/**
 * Entry composable launching the Bswap navigation flow.
 */
@Composable
fun ComposeApp(backStack: SnapshotStateList<NavKey> = rememberBackStack()) {
    UiTheme {
        BswapNavHost(backStack)
    }
}

/** Preview of [ComposeApp]. */
@Preview
@Composable
private fun ComposeAppPreview() {
    ComposeApp()
}
