package com.bswap.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.bswap.app.BswapApp
import com.bswap.navigation.rememberBackStack
import org.koin.compose.KoinMultiplatformApplication
import com.bswap.app.di.appModule
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    KoinMultiplatformApplication(application = { modules(appModule) }) {
        ComposeViewport(document.body!!) {
            val backStack = rememberBackStack()
            BswapApp(backStack)
        }
    }
}