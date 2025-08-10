package com.bswap.app

import android.app.Application
import com.bswap.data.seedStorage
import com.bswap.shared.wallet.WalletConfig
import com.bswap.shared.wallet.WalletEngine
import com.bswap.shared.wallet.WalletInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Application : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        initializeAppContext(this)
        // Ensure native libraries from wallet-core are available before any
        // wallet APIs are used.
        System.loadLibrary("TrustWalletCore")
        
        // Initialize wallet configuration for Android app only if not already set by server-side flow
        initializeWallet()
    }
    
    private fun initializeWallet() {
        try {
            // Initialize WalletEngine with Android context
            WalletEngine.init(this)
            
            // Initialize wallet from file (creates if doesn't exist)
            val wallet = WalletInitializer.initializeFromFile(autoCreate = true)
            
            // Also try to initialize WalletConfig from stored seed
            applicationScope.launch {
                try {
                    val storedSeed = seedStorage().loadSeed()
                    if (storedSeed != null) {
                        WalletConfig.initializeFromSeed(storedSeed)
                        println("WalletConfig initialized from stored seed")
                    }
                } catch (e: Exception) {
                    println("Could not initialize WalletConfig from stored seed: ${e.message}")
                }
            }
            
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