package com.bswap.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.bswap.app.BswapApp
import com.bswap.navigation.rememberBackStack
import com.bswap.shared.wallet.WalletInitializer

fun main() = application {
    // Initialize wallet configuration for desktop app
    try {
        val wallet = WalletInitializer.initializeFromFile(autoCreate = true)
        println("Desktop wallet initialized successfully:")
        println("  Public Key: ${wallet.publicKey}")
    } catch (e: Exception) {
        println("ERROR: Failed to initialize desktop wallet - ${e.message}")
        e.printStackTrace()
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Bswap",
    ) {
        val backStack = rememberBackStack()
        BswapApp(backStack)
    }
}