package com.bswap.shared.wallet

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Android implementation of WalletEngine
 * Uses app's private files directory for secure seed storage
 */
actual object WalletEngine {
    
    private const val SHARED_FOLDER_NAME = "bswap_wallet"
    private const val DEFAULT_SEED_FILE = "wallet.seed"
    
    private lateinit var appContext: Context
    
    /**
     * Initialize the engine with Android context
     * Must be called before using other methods
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }
    
    actual fun initializeFromSeedFile(
        seedFileName: String,
        accountIndex: Int,
        passphrase: String
    ): WalletConfig {
        require(::appContext.isInitialized) { "WalletEngine must be initialized with init(context) first" }
        
        val seedFile = File(getSharedFolderPath(), seedFileName)
        
        if (!seedFile.exists()) {
            throw IllegalStateException("Seed file not found: ${seedFile.absolutePath}")
        }
        
        val seedPhrase = seedFile.readText().trim()
        val walletConfig = SeedToWalletConverter.fromSeedPhraseString(seedPhrase, accountIndex, passphrase)
        
        // Initialize the global wallet configuration
        WalletConfig.initializeFromSeed(seedPhrase, accountIndex, passphrase)
        
        return walletConfig
    }
    
    actual fun createSeedFile(seedFileName: String, wordCount: Int): String {
        require(::appContext.isInitialized) { "WalletEngine must be initialized with init(context) first" }
        require(wordCount in listOf(12, 24)) { "Word count must be 12 or 24" }
        
        val sharedFolder = File(getSharedFolderPath())
        if (!sharedFolder.exists()) {
            sharedFolder.mkdirs()
        }
        
        val seedFile = File(sharedFolder, seedFileName)
        
        // Generate seed phrase using secure random selection from BIP39 word list
        // Note: In production, use proper BIP39 library with checksum validation
        val bip39Words = listOf(
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
            "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
            "acoustic", "acquire", "across", "action", "actor", "actress", "actual", "adapt"
        )
        
        val random = java.security.SecureRandom()
        val seedWords = (1..wordCount).map { 
            bip39Words[random.nextInt(bip39Words.size)]
        }
        val seedPhrase = seedWords.joinToString(" ")
        
        // Write to app's private files directory
        seedFile.writeText(seedPhrase)
        
        // Set file permissions to be readable only by the app
        seedFile.setReadable(true, true)
        seedFile.setWritable(true, true)
        seedFile.setExecutable(false, false)
        
        println("Created seed file: ${seedFile.absolutePath}")
        println("IMPORTANT: Keep this seed phrase secure and backed up!")
        
        return seedPhrase
    }
    
    actual fun seedFileExists(seedFileName: String): Boolean {
        if (!::appContext.isInitialized) return false
        
        val seedFile = File(getSharedFolderPath(), seedFileName)
        return seedFile.exists() && seedFile.isFile
    }
    
    actual fun getSharedFolderPath(): String {
        require(::appContext.isInitialized) { "WalletEngine must be initialized with init(context) first" }
        
        // Use app's private files directory for security
        val walletDir = File(appContext.filesDir, SHARED_FOLDER_NAME)
        return walletDir.absolutePath
    }
    
    actual fun initialize(autoCreate: Boolean): WalletConfig {
        require(::appContext.isInitialized) { "WalletEngine must be initialized with init(context) first" }
        
        val sharedFolder = File(getSharedFolderPath())
        
        // Ensure shared folder exists
        if (!sharedFolder.exists()) {
            sharedFolder.mkdirs()
        }
        
        // Check if seed file exists
        if (seedFileExists(DEFAULT_SEED_FILE)) {
            println("Loading wallet from existing seed file...")
            return initializeFromSeedFile(DEFAULT_SEED_FILE)
        } else if (autoCreate) {
            println("No seed file found, creating new wallet...")
            val seedPhrase = createSeedFile(DEFAULT_SEED_FILE)
            return initializeFromSeedFile(DEFAULT_SEED_FILE)
        } else {
            throw IllegalStateException("No seed file found and auto-creation is disabled")
        }
    }
}