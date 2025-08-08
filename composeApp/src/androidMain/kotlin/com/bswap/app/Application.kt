package com.bswap.app

import android.app.Application

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeAppContext(this)
        // Ensure native libraries from wallet-core are available before any
        // wallet APIs are used.
        System.loadLibrary("TrustWalletCore")
    }
}