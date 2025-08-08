package com.bswap.app

import android.app.Application
import com.bswap.shared.wallet.WalletEngine
import com.bswap.shared.wallet.WalletInitializer

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeAppContext(this)
        // Ensure native libraries from wallet-core are available before any
        // wallet APIs are used.
        System.loadLibrary("TrustWalletCore")
        
        // Initialize wallet configuration for Android app
        initializeWallet()
    }
    
    private fun initializeWallet() {
        try {
            // Initialize WalletEngine with Android context
            WalletEngine.init(this)
            
            // Initialize wallet from file (creates if doesn't exist)
            val wallet = WalletInitializer.initializeFromFile(autoCreate = true)
            
            println("App wallet initialized successfully:")
            println("  Public Key: ${wallet.publicKey}")
            println("  Seed file location: ${WalletEngine.getSharedFolderPath()}")
        } catch (e: Exception) {
            println("ERROR: Failed to initialize app wallet - ${e.message}")
            // Don't crash the app, but log the issue
            e.printStackTrace()
        }
    }
}