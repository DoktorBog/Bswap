package com.bswap.shared.wallet

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.security.SecureRandom

/**
 * JVM implementation of WalletEngine for server/desktop environments
 */
actual object WalletEngine {
    
    private const val SHARED_FOLDER_NAME = ".bswap"
    private const val DEFAULT_SEED_FILE = "wallet.seed"
    
    /**
     * BIP39 word list (first 12 words for demo - in production, use complete list)
     */
    private val BIP39_WORDS = listOf(
        "abandon", "ability", "able", "about", "above", "absent", 
        "absorb", "abstract", "absurd", "abuse", "access", "accident"
    )
    
    actual fun initializeFromSeedFile(
        seedFileName: String,
        accountIndex: Int,
        passphrase: String
    ): WalletConfig {
        val seedFile = File(getSharedFolderPath(), seedFileName)
        
        if (!seedFile.exists()) {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(seedFileName)
            if (stream != null) {
                try {
                    val bytes = stream.readAllBytes()
                    val text = String(bytes).trim()
                    // Ensure shared folder exists
                    File(getSharedFolderPath()).mkdirs()
                    seedFile.writeText(text)
                    seedFile.setReadable(true, true)
                    seedFile.setWritable(true, true)
                } catch (e: IOException) {
                    throw IllegalStateException("Failed to copy seed resource to ${seedFile.absolutePath}", e)
                }
            } else {
                throw IllegalStateException("Seed file not found: ${seedFile.absolutePath}")
            }
        }

        val seedPhrase = seedFile.readText().trim()
        val walletConfig = SeedToWalletConverter.fromSeedPhraseString(seedPhrase, accountIndex, passphrase)
        
        // Initialize the global wallet configuration
        WalletConfig.initializeFromSeed(seedPhrase, accountIndex, passphrase)
        
        return walletConfig
    }
    
    actual fun createSeedFile(seedFileName: String, wordCount: Int): String {
        require(wordCount in listOf(12, 24)) { "Word count must be 12 or 24" }
        
        val sharedFolder = File(getSharedFolderPath())
        if (!sharedFolder.exists()) {
            sharedFolder.mkdirs()
        }
        
        val seedFile = File(sharedFolder, seedFileName)
        
        // Generate a simple seed phrase (in production, use proper BIP39 generation)
        val random = SecureRandom()
        val seedWords = (1..wordCount).map { 
            BIP39_WORDS[random.nextInt(BIP39_WORDS.size)]
        }
        val seedPhrase = seedWords.joinToString(" ")
        
        // Write to file with proper permissions
        seedFile.writeText(seedPhrase)
        seedFile.setReadable(true, true)  // Owner read only
        seedFile.setWritable(true, true)  // Owner write only
        seedFile.setExecutable(false, false) // No execute permissions
        
        println("Created seed file: ${seedFile.absolutePath}")
        println("IMPORTANT: Keep this seed phrase secure and backed up!")
        
        return seedPhrase
    }
    
    actual fun seedFileExists(seedFileName: String): Boolean {
        val seedFile = File(getSharedFolderPath(), seedFileName)
        return seedFile.exists() && seedFile.isFile
    }
    
    actual fun getSharedFolderPath(): String {
        val userHome = System.getProperty("user.home")
        return File(userHome, SHARED_FOLDER_NAME).absolutePath
    }
    
    actual fun initialize(autoCreate: Boolean): WalletConfig {
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