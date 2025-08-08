package com.bswap.shared.wallet

/**
 * Usage examples for WalletEngine - file-based wallet configuration
 */
object WalletEngineUsage {
    
    /**
     * Example 1: Simple initialization - automatically creates seed file if none exists
     */
    fun quickStart() {
        try {
            // This will look for wallet.seed in shared folder, create if not found
            val wallet = WalletInitializer.initializeFromFile()
            
            println("Wallet initialized from file:")
            println("Public Key: ${wallet.publicKey}")
            println("Seed file location: ${WalletEngine.getSharedFolderPath()}")
            
        } catch (e: Exception) {
            println("Failed to initialize wallet: ${e.message}")
        }
    }
    
    /**
     * Example 2: Check if seed file exists before initializing
     */
    fun conditionalInitialization() {
        val seedFileName = "my_wallet.seed"
        
        if (WalletEngine.seedFileExists(seedFileName)) {
            println("Loading existing wallet...")
            val wallet = WalletInitializer.initializeFromSeedFile(seedFileName)
            println("Loaded wallet with public key: ${wallet.publicKey}")
        } else {
            println("No seed file found, would you like to create one? (y/n)")
            // In a real app, you'd get user input here
            val createNew = true // For demo purposes
            
            if (createNew) {
                println("Creating new seed file...")
                val seedPhrase = WalletEngine.createSeedFile(seedFileName, 12)
                println("Generated seed phrase: $seedPhrase")
                
                val wallet = WalletInitializer.initializeFromSeedFile(seedFileName)
                println("New wallet initialized with public key: ${wallet.publicKey}")
            }
        }
    }
    
    /**
     * Example 3: Manual seed file management
     */
    fun manualSeedManagement() {
        val seedFileName = "backup_wallet.seed"
        
        // Create a new 24-word seed
        val seedPhrase = WalletEngine.createSeedFile(seedFileName, 24)
        println("Created new 24-word seed: $seedPhrase")
        
        // Initialize wallet from the new file
        val wallet = WalletInitializer.initializeFromSeedFile(seedFileName)
        println("Wallet initialized:")
        println("  Public Key: ${wallet.publicKey}")
        println("  Private Key: ${wallet.privateKey}")
        println("  File Path: ${WalletEngine.getSharedFolderPath()}/$seedFileName")
    }
    
    /**
     * Example 4: Server/Application startup pattern
     */
    fun applicationStartup() {
        println("Starting Bswap application...")
        
        try {
            // Try to initialize from existing seed file
            val wallet = if (WalletEngine.seedFileExists()) {
                println("Found existing wallet seed file")
                WalletInitializer.initializeFromFile(autoCreate = false)
            } else {
                println("No wallet found, creating new one...")
                WalletInitializer.initializeFromFile(autoCreate = true)
            }
            
            println("Application wallet ready:")
            println("  Public Key: ${wallet.publicKey}")
            println("  Shared Folder: ${WalletEngine.getSharedFolderPath()}")
            
            // Now all components using WalletConfig.current() will work
            println("Wallet configuration is now available globally")
            
        } catch (e: Exception) {
            println("FATAL: Failed to initialize wallet - ${e.message}")
            // In real app, you'd exit gracefully
        }
    }
    
    /**
     * Example 5: Multiple wallet support
     */
    fun multipleWallets() {
        val wallets = mapOf(
            "trading" to "trading_wallet.seed",
            "backup" to "backup_wallet.seed",
            "testing" to "test_wallet.seed"
        )
        
        wallets.forEach { (name, fileName) ->
            if (!WalletEngine.seedFileExists(fileName)) {
                val seedPhrase = WalletEngine.createSeedFile(fileName, 12)
                println("Created $name wallet: ${seedPhrase.split(" ").take(3).joinToString(" ")}...")
            }
            
            val wallet = WalletEngine.initializeFromSeedFile(fileName)
            println("$name wallet: ${wallet.publicKey}")
        }
    }
}