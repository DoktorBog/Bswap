package com.bswap.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.bswap.app.ComposeApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Bswap",
    ) {
        ComposeApp()
    }
}