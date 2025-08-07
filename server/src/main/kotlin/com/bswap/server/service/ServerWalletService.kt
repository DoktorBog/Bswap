package com.bswap.server.service

import com.bswap.server.models.*
import com.bswap.server.validation.TokenValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class ServerWalletService(
    private val tokenValidator: TokenValidator
) {
    private val logger = LoggerFactory.getLogger(ServerWalletService::class.java)
    private val wallets = ConcurrentHashMap<String, WalletData>()
    private val activeWallet = mutableMapOf<String, String>() // botId -> publicKey
    
    private data class WalletData(
        val publicKey: String,
        val privateKey: ByteArray,
        val mnemonic: List<String>,
        val name: String?,
        val createdAt: Long,
        var lastUsed: Long = System.currentTimeMillis()
    )

    suspend fun createWallet(request: CreateWalletRequest): ApiResponse<CreateWalletResponse> = withContext(Dispatchers.IO) {
        try {
            logger.info("Creating new wallet")
            
            // Generate mock wallet data (replace with real implementation later)
            val mnemonic = generateMockMnemonic()
            val publicKeyString = generateMockPublicKey()
            val privateKeyBytes = generateMockPrivateKey()
            
            // Store wallet data
            val walletData = WalletData(
                publicKey = publicKeyString,
                privateKey = privateKeyBytes,
                mnemonic = mnemonic,
                name = request.name,
                createdAt = System.currentTimeMillis()
            )
            
            wallets[publicKeyString] = walletData
            
            logger.info("Created wallet: $publicKeyString")
            
            ApiResponse(
                success = true,
                data = CreateWalletResponse(
                    publicKey = publicKeyString,
                    mnemonic = mnemonic,
                    message = "Wallet created successfully"
                ),
                message = "Wallet created successfully"
            )
        } catch (e: Exception) {
            logger.error("Failed to create wallet", e)
            ApiResponse(
                success = false,
                message = "Failed to create wallet: ${e.message}"
            )
        }
    }

    suspend fun importWallet(request: ImportWalletRequest): ApiResponse<ImportWalletResponse> = withContext(Dispatchers.IO) {
        try {
            logger.info("Importing wallet")
            
            // Basic validation - check if mnemonic has 12 or 24 words
            if (request.mnemonic.size !in listOf(12, 24)) {
                return@withContext ApiResponse<ImportWalletResponse>(
                    success = false,
                    message = "Invalid mnemonic phrase: must be 12 or 24 words"
                )
            }
            
            // Generate mock public key from mnemonic (for demo purposes)
            val publicKeyString = generatePublicKeyFromMnemonic(request.mnemonic)
            val privateKeyBytes = generateMockPrivateKey()
            
            // Check if wallet already exists
            if (wallets.containsKey(publicKeyString)) {
                return@withContext ApiResponse<ImportWalletResponse>(
                    success = false,
                    message = "Wallet already exists"
                )
            }
            
            // Store wallet data
            val walletData = WalletData(
                publicKey = publicKeyString,
                privateKey = privateKeyBytes,
                mnemonic = request.mnemonic,
                name = request.name,
                createdAt = System.currentTimeMillis()
            )
            
            wallets[publicKeyString] = walletData
            
            logger.info("Imported wallet: $publicKeyString")
            
            ApiResponse(
                success = true,
                data = ImportWalletResponse(
                    publicKey = publicKeyString,
                    message = "Wallet imported successfully"
                ),
                message = "Wallet imported successfully"
            )
        } catch (e: Exception) {
            logger.error("Failed to import wallet", e)
            ApiResponse(
                success = false,
                message = "Failed to import wallet: ${e.message}"
            )
        }
    }

    suspend fun getWalletBalance(publicKey: String): ApiResponse<WalletBalance> = withContext(Dispatchers.IO) {
        try {
            if (!wallets.containsKey(publicKey)) {
                return@withContext ApiResponse<WalletBalance>(
                    success = false,
                    message = "Wallet not found"
                )
            }

            // TODO: Replace with actual Solana RPC calls
            // For now, return mock data
            val balance = WalletBalance(
                publicKey = publicKey,
                solBalance = Random.nextDouble(0.1, 10.0),
                tokenBalances = mapOf(
                    "USDC" to Random.nextDouble(100.0, 1000.0),
                    "BONK" to Random.nextDouble(1000000.0, 10000000.0)
                ),
                totalValueUSD = Random.nextDouble(200.0, 2000.0),
                lastUpdated = System.currentTimeMillis()
            )

            wallets[publicKey]?.lastUsed = System.currentTimeMillis()

            ApiResponse(
                success = true,
                data = balance,
                message = "Balance retrieved successfully"
            )
        } catch (e: Exception) {
            logger.error("Failed to get wallet balance", e)
            ApiResponse(
                success = false,
                message = "Failed to get wallet balance: ${e.message}"
            )
        }
    }

    suspend fun getWalletHistory(request: com.bswap.shared.model.WalletHistoryRequest): ApiResponse<WalletHistoryResponse> = withContext(Dispatchers.IO) {
        try {
            if (!wallets.containsKey(request.publicKey)) {
                return@withContext ApiResponse<WalletHistoryResponse>(
                    success = false,
                    message = "Wallet not found"
                )
            }

            // TODO: Replace with actual Solana RPC calls
            // For now, return mock data
            val transactions = generateMockTransactions(request.publicKey, request.limit)
            
            val response = WalletHistoryResponse(
                publicKey = request.publicKey,
                transactions = transactions,
                totalCount = transactions.size,
                hasMore = false
            )

            ApiResponse(
                success = true,
                data = response,
                message = "Transaction history retrieved successfully"
            )
        } catch (e: Exception) {
            logger.error("Failed to get wallet history", e)
            ApiResponse(
                success = false,
                message = "Failed to get wallet history: ${e.message}"
            )
        }
    }

    suspend fun setActiveBotWallet(botId: String, publicKey: String): ApiResponse<String> {
        return try {
            if (!wallets.containsKey(publicKey)) {
                ApiResponse(
                    success = false,
                    message = "Wallet not found"
                )
            } else {
                activeWallet[botId] = publicKey
                wallets[publicKey]?.lastUsed = System.currentTimeMillis()
                logger.info("Set active wallet for bot $botId: $publicKey")
                
                ApiResponse(
                    success = true,
                    data = publicKey,
                    message = "Active wallet set successfully"
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to set active bot wallet", e)
            ApiResponse(
                success = false,
                message = "Failed to set active wallet: ${e.message}"
            )
        }
    }

    fun getActiveBotWallet(botId: String): String? {
        return activeWallet[botId]
    }

    fun getAllWallets(): List<WalletInfo> {
        return wallets.values.map { wallet ->
            WalletInfo(
                publicKey = wallet.publicKey,
                balance = Random.nextDouble(0.1, 10.0), // TODO: Get real balance
                isActive = activeWallet.values.contains(wallet.publicKey),
                createdAt = wallet.createdAt,
                lastUpdated = wallet.lastUsed
            )
        }
    }

    fun getWalletPrivateKey(publicKey: String): ByteArray? {
        return wallets[publicKey]?.privateKey
    }

    private fun generateMockMnemonic(): List<String> {
        // BIP-39 word list sample for demo purposes
        val sampleWords = listOf(
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
            "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
            "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
            "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
            "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
            "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album"
        )
        
        return (1..12).map { sampleWords.random() }
    }
    
    private fun generateMockPublicKey(): String {
        val chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return (1..44).map { chars.random() }.joinToString("")
    }
    
    private fun generateMockPrivateKey(): ByteArray {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return bytes
    }
    
    private fun generatePublicKeyFromMnemonic(mnemonic: List<String>): String {
        // Generate deterministic public key from mnemonic (for demo)
        val hash = mnemonic.joinToString("").hashCode()
        val chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val random = Random(hash.toLong())
        return (1..44).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun generateMockTransactions(publicKey: String, limit: Int): List<WalletTransaction> {
        val types = TransactionType.values()
        val statuses = TransactionStatus.values()
        val tokens = listOf("SOL", "USDC", "BONK", "RAY", "ORCA")
        
        return (1..minOf(limit, 20)).map { i ->
            val type = types.random()
            WalletTransaction(
                signature = generateRandomSignature(),
                publicKey = publicKey,
                type = type,
                amount = when(type) {
                    TransactionType.BUY, TransactionType.SELL -> Random.nextDouble(0.1, 5.0)
                    TransactionType.SEND, TransactionType.RECEIVE -> Random.nextDouble(0.01, 1.0)
                    else -> Random.nextDouble(0.1, 2.0)
                },
                token = tokens.random(),
                timestamp = System.currentTimeMillis() - (i * 3600000L), // 1 hour ago per transaction
                status = statuses.random(),
                fee = Random.nextDouble(0.00001, 0.001),
                description = "Trading bot transaction #${i}"
            )
        }
    }

    private fun generateRandomSignature(): String {
        val chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return (1..88).map { chars.random() }.joinToString("")
    }
}