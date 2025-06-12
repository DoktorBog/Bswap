package com.bswap.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bswap.navigation.BswapNavHost
import com.bswap.ui.UiTheme

/**
 * Entry composable launching the Bswap navigation flow.
 */
@Composable
fun ComposeApp() {
    UiTheme {
        BswapNavHost()
    }
}

/** Preview of [ComposeApp]. */
@Preview
@Composable
private fun ComposeAppPreview() {
    ComposeApp()
}
