package com.bswap.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.bswap.app.BswapApp
import com.bswap.navigation.rememberBackStack
import org.koin.compose.KoinMultiplatformApplication
import com.bswap.app.di.appModule

fun main() = application {
    KoinMultiplatformApplication(application = { modules(appModule) }) {
        Window(
        onCloseRequest = ::exitApplication,
        title = "Bswap",
        ) {
            val backStack = rememberBackStack()
            BswapApp(backStack)
        }
    }
}