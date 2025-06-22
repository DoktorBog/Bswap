package com.bswap.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.bswap.app.ComposeApp
import com.bswap.navigation.rememberBackStack
import com.bswap.app.di.initKoin
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    ComposeViewport(document.body!!) {
        val backStack = rememberBackStack()
        ComposeApp(backStack)
    }
}