package com.bswap.server.service

import com.bswap.server.models.*
import com.bswap.server.validation.TokenValidator
import com.bswap.shared.wallet.WalletConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class ServerWalletService(
    private val tokenValidator: TokenValidator,
    private val solanaRpcClient: com.bswap.server.data.solana.rpc.SolanaRpcClient,
    private val priceService: PriceService? = null
) {
    private val transactionCache = com.bswap.server.cache.TransactionCache(solanaRpcClient)
    private val logger = LoggerFactory.getLogger(ServerWalletService::class.java)
    private val wallets = ConcurrentHashMap<String, WalletData>()
    private val activeWallet = mutableMapOf<String, String>() // botId -> publicKey

    // Balance caching to reduce frequent RPC calls
    private val balanceCache = ConcurrentHashMap<String, WalletBalance>()
    private val balanceCacheTime = ConcurrentHashMap<String, Long>()
    private val balanceCacheTtl = 10_000L // 10 seconds cache for balance

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
            val now = System.currentTimeMillis()

            // Check cache first
            val cachedBalance = balanceCache[publicKey]
            val cacheTime = balanceCacheTime[publicKey] ?: 0

            if (cachedBalance != null && (now - cacheTime) < balanceCacheTtl) {
                return@withContext ApiResponse(
                    success = true,
                    data = cachedBalance,
                    message = "Balance retrieved from cache"
                )
            }

            // Get real SOL balance
            val solBalance = try {
                solanaRpcClient.getBalance(publicKey).toDouble() / 1_000_000_000.0
            } catch (e: Exception) {
                logger.warn("Failed to get SOL balance for $publicKey: ${e.message}")
                0.0
            }

            // Get real SPL token balances
            val tokenBalances = try {
                val tokens = solanaRpcClient.getSPLTokens(publicKey)
                tokens.associate { token ->
                    val symbol = when (token.mint) {
                        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" -> "USDC"
                        "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263" -> "BONK"
                        "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R" -> "RAY"
                        "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE" -> "ORCA"
                        else -> token.mint.take(8) + "..."
                    }
                    val amount = if (token.decimals != null && token.amount != null) {
                        val amountStr = token.amount!!
                        val decimalsInt = token.decimals!!
                        amountStr.toDouble() / Math.pow(10.0, decimalsInt.toDouble())
                    } else 0.0
                    symbol to amount
                }
            } catch (e: Exception) {
                logger.warn("Failed to get SPL tokens for $publicKey: ${e.message}")
                emptyMap()
            }

            // Calculate total USD value using real prices
            val solPriceUSD = try {
                priceService?.getTokenPrice(PriceService.SOL_MINT)?.priceUsd ?: 200.0
            } catch (_: Exception) { 200.0 }
            val totalValueUSD = solBalance * solPriceUSD + tokenBalances.values.sum()

            val balance = WalletBalance(
                publicKey = publicKey,
                solBalance = solBalance,
                tokenBalances = tokenBalances,
                totalValueUSD = totalValueUSD,
                lastUpdated = System.currentTimeMillis()
            )

            wallets[publicKey]?.lastUsed = System.currentTimeMillis()

            // Cache the balance for future requests
            balanceCache[publicKey] = balance
            balanceCacheTime[publicKey] = now

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

    suspend fun getWalletTokens(publicKey: String): ApiResponse<List<com.bswap.shared.model.TokenInfo>> = withContext(Dispatchers.IO) {
        try {
            logger.info("Getting tokens for wallet: $publicKey")

            // Get real SPL tokens from Solana RPC
            val tokens = try {
                val splTokens = solanaRpcClient.getSPLTokens(publicKey)

                // Get prices for all tokens at once for efficiency
                val tokenMints = splTokens.map { it.mint }
                val prices = priceService?.getTokenPrices(tokenMints) ?: emptyMap()

                splTokens.map { token ->
                    val usdValue = priceService?.calculateUsdValue(token.mint, token.amount, token.decimals)

                    com.bswap.shared.model.TokenInfo(
                        mint = token.mint,
                        symbol = getTokenSymbol(token.mint),
                        name = getTokenName(token.mint),
                        decimals = token.decimals,
                        amount = token.amount,
                        logoUri = null,
                        usdValue = usdValue
                    )
                }
            } catch (e: Exception) {
                logger.warn("Failed to get SPL tokens from RPC: ${e.message}")
                emptyList()
            }

            ApiResponse(
                success = true,
                data = tokens,
                message = "Tokens retrieved successfully"
            )
        } catch (e: Exception) {
            logger.error("Failed to get wallet tokens", e)
            ApiResponse(
                success = false,
                message = "Failed to get wallet tokens: ${e.message}"
            )
        }
    }

    private fun getTokenSymbol(mint: String): String {
        return when (mint) {
            "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" -> "USDC"
            "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263" -> "BONK"
            "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R" -> "RAY"
            "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE" -> "ORCA"
            "So11111111111111111111111111111111111111112" -> "SOL"
            else -> mint.take(8) + "..."
        }
    }

    private fun getTokenName(mint: String): String {
        return when (mint) {
            "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" -> "USD Coin"
            "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263" -> "Bonk"
            "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R" -> "Raydium"
            "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE" -> "Orca"
            "So11111111111111111111111111111111111111112" -> "Solana"
            else -> "Unknown Token"
        }
    }

    suspend fun isWalletReady(): WalletReadyResponse = withContext(Dispatchers.IO) {
        try {
            val botPublicKey = getBotWalletPublicKey()
            if (botPublicKey.isEmpty()) {
                return@withContext WalletReadyResponse(
                    success = false,
                    message = "Wallet not configured",
                    ready = false,
                    reason = "No bot wallet configured"
                )
            }

            val cacheStats = transactionCache.getCacheStats(botPublicKey)
            val isBackgroundFetching = cacheStats["isBackgroundFetching"] as? Boolean ?: false
            val transactionCount = cacheStats["transactionCount"] as? Int ?: 0
            val isFullyFetched = cacheStats["isFullyFetched"] as? Boolean ?: false

            val ready = transactionCount > 0 || isFullyFetched

            return@withContext WalletReadyResponse(
                success = true,
                message = if (ready) "Wallet is ready" else "Wallet is initializing",
                ready = ready,
                walletPublicKey = botPublicKey,
                transactionCount = transactionCount,
                isBackgroundFetching = isBackgroundFetching,
                isFullyFetched = isFullyFetched,
                reason = if (ready) "Wallet is ready" else "Cache still loading"
            )
        } catch (e: Exception) {
            logger.error("Failed to check wallet readiness", e)
            WalletReadyResponse(
                success = false,
                message = "Error checking wallet readiness: ${e.message}",
                ready = false,
                reason = "Error checking readiness: ${e.message}"
            )
        }
    }

    suspend fun getBotWalletBalance(): ApiResponse<WalletBalance> = withContext(Dispatchers.IO) {
        try {
            // Get the configured bot wallet public key
            val botPublicKey = getBotWalletPublicKey()
            if (botPublicKey.isEmpty()) {
                return@withContext ApiResponse<WalletBalance>(
                    success = false,
                    message = "No bot wallet configured"
                )
            }

            logger.info("Getting balance for bot wallet: $botPublicKey")
            return@withContext getWalletBalance(botPublicKey)
        } catch (e: Exception) {
            logger.error("Failed to get bot wallet balance", e)
            ApiResponse(
                success = false,
                message = "Failed to get bot wallet balance: ${e.message}"
            )
        }
    }

    suspend fun getBotWalletTokens(): ApiResponse<List<com.bswap.shared.model.TokenInfo>> = withContext(Dispatchers.IO) {
        try {
            // Get the configured bot wallet public key
            val botPublicKey = getBotWalletPublicKey()
            if (botPublicKey.isEmpty()) {
                return@withContext ApiResponse<List<com.bswap.shared.model.TokenInfo>>(
                    success = false,
                    message = "No bot wallet configured"
                )
            }

            logger.info("Getting tokens for bot wallet: $botPublicKey")
            return@withContext getWalletTokens(botPublicKey)
        } catch (e: Exception) {
            logger.error("Failed to get bot wallet tokens", e)
            ApiResponse(
                success = false,
                message = "Failed to get bot wallet tokens: ${e.message}"
            )
        }
    }

    suspend fun getBotWalletHistory(request: com.bswap.shared.model.WalletHistoryRequest, silent: Boolean = false): ApiResponse<WalletHistoryResponse> = withContext(Dispatchers.IO) {
        try {
            // Get the configured bot wallet public key
            val botPublicKey = getBotWalletPublicKey()
            if (botPublicKey.isEmpty()) {
                return@withContext ApiResponse<WalletHistoryResponse>(
                    success = false,
                    message = "No bot wallet configured"
                )
            }

            logger.info("Getting history for bot wallet: $botPublicKey")
            // Create a new request with the bot wallet public key
            val botWalletRequest = com.bswap.shared.model.WalletHistoryRequest(
                limit = request.limit,
                offset = request.offset
            )
            return@withContext getWalletHistory(botWalletRequest, botPublicKey, silent)
        } catch (e: Exception) {
            logger.error("Failed to get bot wallet history", e)
            ApiResponse(
                success = false,
                message = "Failed to get bot wallet history: ${e.message}"
            )
        }
    }

    private fun getBotWalletPublicKey(): String {
        return WalletConfig.current().publicKey
    }

    suspend fun getWalletHistory(request: com.bswap.shared.model.WalletHistoryRequest, publicKey: String, silent: Boolean = false): ApiResponse<WalletHistoryResponse> = withContext(Dispatchers.IO) {
        try {
            if (!silent) {
                logger.info("💰 ServerWalletService: getWalletHistory called for wallet: $publicKey, limit: ${request.limit}, offset: ${request.offset}")
            }

            val historyPage = if (request.offset == 0) {
                // First page - get from cache (fast response)
                if (!silent) {
                    logger.info("💰 ServerWalletService: Requesting first page from cache")
                }
                val cachePage = try {
                    transactionCache.getFirstPage(publicKey, silent)
                } catch (e: Exception) {
                    if (!silent) {
                        logger.warn("💰 ServerWalletService: Cache failed: ${e.message}")
                    }
                    com.bswap.shared.model.HistoryPage(emptyList(), "page_1")
                }

                // If cache is empty and no background fetch is running, add some mock data as fallback
                if (cachePage.transactions.isEmpty()) {
                    logger.info("💰 ServerWalletService: Cache empty, checking if we should add mock data as fallback")
                    val cacheStats = transactionCache.getCacheStats(publicKey)
                    val isBackgroundFetching = cacheStats["isBackgroundFetching"] as? Boolean ?: false

                    if (!isBackgroundFetching) {
                        logger.info("💰 ServerWalletService: No background fetch running, providing mock data")
                        generateMockHistoryPage(publicKey, request.limit)
                    } else {
                        logger.info("💰 ServerWalletService: Background fetch running, returning empty page")
                        cachePage
                    }
                } else {
                    cachePage
                }
            } else {
                // Subsequent pages - get from cache
                val pageIndex = request.offset / request.limit
                logger.info("💰 ServerWalletService: Requesting page $pageIndex from cache")
                try {
                    transactionCache.getPage(publicKey, pageIndex, request.limit)
                } catch (e: Exception) {
                    logger.warn("💰 ServerWalletService: Cache page failed: ${e.message}")
                    com.bswap.shared.model.HistoryPage(emptyList(), null)
                }
            }

            logger.info("💰 ServerWalletService: Got ${historyPage.transactions.size} transactions from cache, hasMore: ${historyPage.nextCursor != null}")

            logger.info("🔍 DETAILED: ServerWalletService - converting ${historyPage.transactions.size} SolanaTx to WalletTransaction")

            // Convert SolanaTx to WalletTransaction
            val transactions = historyPage.transactions.map { solanaTx ->
                WalletTransaction(
                    signature = solanaTx.signature,
                    publicKey = publicKey,
                    type = if (solanaTx.incoming) TransactionType.RECEIVE else TransactionType.SEND,
                    amount = kotlin.math.abs(solanaTx.amount),
                    token = "SOL",
                    timestamp = System.currentTimeMillis() - (kotlin.random.Random.nextLong(1, 24) * 3600000), // Random time in last 24h
                    status = TransactionStatus.CONFIRMED,
                    fee = kotlin.random.Random.nextDouble(0.00001, 0.001),
                    description = if (solanaTx.incoming) "Received SOL" else "Sent SOL"
                )
            }

            logger.info("🔍 DETAILED: ServerWalletService - converted to ${transactions.size} WalletTransactions")

            val response = WalletHistoryResponse(
                publicKey = publicKey,
                transactions = transactions,
                totalCount = transactions.size,
                hasMore = historyPage.nextCursor != null
            )

            logger.info("🔍 DETAILED: ServerWalletService - created response with ${response.transactions.size} transactions, hasMore: ${response.hasMore}")
            logger.info("🔍 DETAILED: ServerWalletService - returning SUCCESS response")

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

    private fun generateMockHistoryPage(publicKey: String, limit: Int): com.bswap.shared.model.HistoryPage {
        val transactions = (1..minOf(limit, 10)).map { i ->
            val incoming = kotlin.random.Random.nextBoolean()
            com.bswap.shared.model.SolanaTx(
                signature = generateRandomSignature(),
                address = publicKey,
                amount = kotlin.random.Random.nextDouble(0.01, 1.0) * if (incoming) 1 else -1,
                incoming = incoming
            )
        }
        return com.bswap.shared.model.HistoryPage(transactions, null)
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

    /**
     * Clean up expired cache entries
     */
    fun cleanupCache() {
        logger.info("🧹 ServerWalletService: Running cache cleanup")
        transactionCache.cleanupExpiredCache()
        priceService?.cleanupCache()

        // Clean up expired balance cache entries
        val now = System.currentTimeMillis()
        val expiredKeys = balanceCacheTime.entries.filter { (_, time) ->
            (now - time) > balanceCacheTtl
        }.map { it.key }

        expiredKeys.forEach { key ->
            balanceCache.remove(key)
            balanceCacheTime.remove(key)
        }

        if (expiredKeys.isNotEmpty()) {
            logger.info("🧹 ServerWalletService: Cleaned up ${expiredKeys.size} expired balance cache entries")
        }
    }

    /**
     * Clear cache for specific wallet
     */
    fun clearWalletCache(publicKey: String) {
        logger.info("🗑️ ServerWalletService: Clearing cache for wallet $publicKey")
        transactionCache.clearCache(publicKey)
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(publicKey: String): Map<String, Any> {
        return transactionCache.getCacheStats(publicKey)
    }

    /**
     * Test RPC directly for debugging
     */
    suspend fun testRpcDirect(publicKey: String): Map<String, Any> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Any>()

        try {
            logger.info("🧪 Testing RPC for publicKey: $publicKey")

            // Test 1: Try to get balance first (simpler call)
            val balance = try {
                val bal = solanaRpcClient.getBalance(publicKey)
                result["balance"] = bal
                result["balanceSuccess"] = true
                logger.info("🧪 Balance test SUCCESS: $bal lamports")
                bal
            } catch (e: Exception) {
                result["balanceSuccess"] = false
                result["balanceError"] = e.message ?: "Unknown error"
                logger.error("🧪 Balance test FAILED", e)
                0L
            }

            // Test 2: Try to get history with small limit
            val history = try {
                logger.info("🧪 Testing getHistory with limit 10...")
                val historyResult = solanaRpcClient.getHistory(publicKey, 10)
                result["historySuccess"] = true
                result["historyTransactionCount"] = historyResult.transactions.size
                result["historyNextCursor"] = historyResult.nextCursor ?: "null"
                result["historyTransactions"] = historyResult.transactions.map {
                    mapOf(
                        "signature" to it.signature,
                        "amount" to it.amount,
                        "incoming" to it.incoming
                    )
                }
                logger.info("🧪 History test SUCCESS: ${historyResult.transactions.size} transactions")
                historyResult
            } catch (e: Exception) {
                result["historySuccess"] = false
                result["historyError"] = e.message ?: "Unknown error"
                logger.error("🧪 History test FAILED", e)
                null
            }

            // Test 3: Try a known active wallet address for comparison
            val testAddress = "6dNGd1K4Yju7tTRBjRgBwgfBhJz9y1jy5Rj6PvKGqJgE" // A known active wallet
            if (publicKey != testAddress) {
                try {
                    logger.info("🧪 Testing known active wallet: $testAddress")
                    val testResult = solanaRpcClient.getHistory(testAddress, 5)
                    result["testWalletSuccess"] = true
                    result["testWalletTransactionCount"] = testResult.transactions.size
                    logger.info("🧪 Test wallet SUCCESS: ${testResult.transactions.size} transactions")
                } catch (e: Exception) {
                    result["testWalletSuccess"] = false
                    result["testWalletError"] = e.message ?: "Unknown error"
                    logger.error("🧪 Test wallet FAILED", e)
                }
            }

            result["testCompleted"] = true

        } catch (e: Exception) {
            result["testCompleted"] = false
            result["overallError"] = e.message ?: "Unknown error"
            logger.error("🧪 Overall RPC test FAILED", e)
        }

        return@withContext result
    }
}
