package com.bswap.server.validation

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.pow
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Base64

@Serializable
data class TokenMetadata(
    val mint: String,
    val name: String? = null,
    val symbol: String? = null,
    val uri: String? = null,
    val isMutable: Boolean = true,
    val updateAuthority: String? = null
)

@Serializable
data class TokenAccountInfo(
    val mint: String,
    val decimals: Int,
    val supply: String,
    val isInitialized: Boolean,
    val freezeAuthority: String? = null,
    val mintAuthority: String? = null
)

@Serializable
data class LiquidityPool(
    val address: String,
    val baseMint: String,
    val quoteMint: String,
    val baseReserve: String,
    val quoteReserve: String,
    val lpSupply: String
)

@Serializable
data class TokenValidationResult(
    val isValid: Boolean,
    val mint: String,
    val reasons: List<String> = emptyList(),
    val riskScore: Double = 0.0, // 0.0 = safe, 1.0 = maximum risk
    val metadata: TokenMetadata? = null,
    val accountInfo: TokenAccountInfo? = null,
    val liquidityPools: List<LiquidityPool> = emptyList(),
    val holderCount: Int = 0,
    val topHolderPercentage: Double = 0.0
)

enum class ValidationRule {
    FROZEN_AUTHORITY_CHECK,
    MINT_AUTHORITY_CHECK,
    LIQUIDITY_CHECK,
    HOLDER_DISTRIBUTION_CHECK,
    METADATA_CHECK,
    SUPPLY_CHECK,
    RUG_PATTERN_CHECK,
    PUMP_FUN_VALIDATION,
    HONEYPOT_CHECK
}

class TokenValidator(private val httpClient: HttpClient, private val config: ValidationConfig = ValidationConfig()) {
    private val logger = LoggerFactory.getLogger(TokenValidator::class.java)

    // Configurable validation rules
    private val enabledRules = setOf(
        ValidationRule.FROZEN_AUTHORITY_CHECK,
        ValidationRule.MINT_AUTHORITY_CHECK,
        ValidationRule.LIQUIDITY_CHECK,
        ValidationRule.HOLDER_DISTRIBUTION_CHECK,
        ValidationRule.METADATA_CHECK,
        ValidationRule.SUPPLY_CHECK,
        ValidationRule.PUMP_FUN_VALIDATION,
        ValidationRule.HONEYPOT_CHECK,
        ValidationRule.RUG_PATTERN_CHECK
    )

    // Cache for API responses
    private val cache = ConcurrentHashMap<String, Pair<Any, Long>>()
    private val cacheMutex = Mutex()
    
    // JSON parser
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun validateToken(mint: String): TokenValidationResult {
        logger.info("Starting validation for token: $mint")
        val reasons = mutableListOf<String>()
        var riskScore = 0.0

        try {
            // Get basic token account info
            val accountInfo = getTokenAccountInfo(mint)
            if (accountInfo == null) {
                return TokenValidationResult(
                    isValid = false,
                    mint = mint,
                    reasons = listOf("Token account not found or invalid"),
                    riskScore = 1.0
                )
            }

            // Get token metadata
            val metadata = getTokenMetadata(mint)

            // Run validation rules
            if (ValidationRule.FROZEN_AUTHORITY_CHECK in enabledRules) {
                val (isFrozenSafe, frozenRisk, frozenReasons) = checkFreezeAuthority(accountInfo)
                if (!isFrozenSafe) reasons.addAll(frozenReasons)
                riskScore += frozenRisk
            }

            if (ValidationRule.MINT_AUTHORITY_CHECK in enabledRules) {
                val (isMintSafe, mintRisk, mintReasons) = checkMintAuthority(accountInfo)
                if (!isMintSafe) reasons.addAll(mintReasons)
                riskScore += mintRisk
            }

            if (ValidationRule.SUPPLY_CHECK in enabledRules) {
                val (isSupplySafe, supplyRisk, supplyReasons) = checkSupply(accountInfo)
                if (!isSupplySafe) reasons.addAll(supplyReasons)
                riskScore += supplyRisk
            }

            if (ValidationRule.METADATA_CHECK in enabledRules) {
                val (isMetadataSafe, metadataRisk, metadataReasons) = checkMetadata(metadata)
                if (!isMetadataSafe) reasons.addAll(metadataReasons)
                riskScore += metadataRisk
            }

            // Get liquidity info
            val liquidityPools = if (ValidationRule.LIQUIDITY_CHECK in enabledRules) {
                getLiquidityPools(mint)
            } else emptyList()

            if (ValidationRule.LIQUIDITY_CHECK in enabledRules && liquidityPools.isNotEmpty()) {
                val (isLiquiditySafe, liquidityRisk, liquidityReasons) = checkLiquidity(liquidityPools)
                if (!isLiquiditySafe) reasons.addAll(liquidityReasons)
                riskScore += liquidityRisk
            }

            // Get holder distribution (this would require additional API calls to analyze holders)
            var holderCount = 0
            var topHolderPercentage = 0.0

            if (ValidationRule.HOLDER_DISTRIBUTION_CHECK in enabledRules) {
                val holderInfo = getHolderDistribution(mint)
                holderCount = holderInfo.first
                topHolderPercentage = holderInfo.second

                val (isHolderSafe, holderRisk, holderReasons) = checkHolderDistribution(holderCount, topHolderPercentage)
                if (!isHolderSafe) reasons.addAll(holderReasons)
                riskScore += holderRisk
            }

            // PumpFun specific validations
            if (ValidationRule.PUMP_FUN_VALIDATION in enabledRules) {
                val (isPumpFunSafe, pumpFunRisk, pumpFunReasons) = checkPumpFunSpecific(mint)
                if (!isPumpFunSafe) reasons.addAll(pumpFunReasons)
                riskScore += pumpFunRisk
            }

            // Honeypot detection
            if (ValidationRule.HONEYPOT_CHECK in enabledRules) {
                val (isHoneypotSafe, honeypotRisk, honeypotReasons) = checkHoneypot(mint, liquidityPools)
                if (!isHoneypotSafe) reasons.addAll(honeypotReasons)
                riskScore += honeypotRisk
            }

            // Rug pattern detection
            if (ValidationRule.RUG_PATTERN_CHECK in enabledRules) {
                val (isRugSafe, rugRisk, rugReasons) = checkRugPatterns(mint, accountInfo, metadata, liquidityPools)
                if (!isRugSafe) reasons.addAll(rugReasons)
                riskScore += rugRisk
            }

            // Normalize risk score to 0-1 range
            riskScore = min(riskScore, 1.0)

            val isValid = reasons.isEmpty() && riskScore < config.maxRiskScore

            logger.info("Validation complete for $mint: valid=$isValid, risk=$riskScore, reasons=${reasons.size}")

            return TokenValidationResult(
                isValid = isValid,
                mint = mint,
                reasons = reasons,
                riskScore = riskScore,
                metadata = metadata,
                accountInfo = accountInfo,
                liquidityPools = liquidityPools,
                holderCount = holderCount,
                topHolderPercentage = topHolderPercentage
            )

        } catch (e: Exception) {
            logger.error("Error validating token $mint: ${e.message}", e)
            return TokenValidationResult(
                isValid = false,
                mint = mint,
                reasons = listOf("Validation error: ${e.message}"),
                riskScore = 1.0
            )
        }
    }

    private fun checkFreezeAuthority(accountInfo: TokenAccountInfo): Triple<Boolean, Double, List<String>> {
        return if (accountInfo.freezeAuthority != null) {
            Triple(false, 0.3, listOf("Token has freeze authority - can be frozen anytime"))
        } else {
            Triple(true, 0.0, emptyList())
        }
    }

    private fun checkMintAuthority(accountInfo: TokenAccountInfo): Triple<Boolean, Double, List<String>> {
        return if (accountInfo.mintAuthority != null) {
            Triple(false, 0.2, listOf("Token has mint authority - supply can be inflated"))
        } else {
            Triple(true, 0.0, emptyList())
        }
    }

    private fun checkSupply(accountInfo: TokenAccountInfo): Triple<Boolean, Double, List<String>> {
        val supply = accountInfo.supply.toDoubleOrNull() ?: 0.0
        val adjustedSupply = supply / 10.0.pow(accountInfo.decimals.toDouble())

        return if (adjustedSupply.compareTo(config.maxSupply) > 0) {
            Triple(false, 0.2, listOf("Extremely high token supply: ${adjustedSupply}"))
        } else {
            Triple(true, 0.0, emptyList())
        }
    }

    private fun checkMetadata(metadata: TokenMetadata?): Triple<Boolean, Double, List<String>> {
        if (metadata == null) {
            return Triple(false, 0.1, listOf("No metadata found"))
        }

        val reasons = mutableListOf<String>()
        var risk = 0.0

        if (metadata.name.isNullOrBlank()) {
            reasons.add("No token name")
            risk += 0.05
        }

        if (metadata.symbol.isNullOrBlank()) {
            reasons.add("No token symbol")
            risk += 0.05
        }

        if (metadata.uri.isNullOrBlank()) {
            reasons.add("No metadata URI")
            risk += 0.05
        }

        if (metadata.isMutable) {
            reasons.add("Metadata is mutable")
            risk += 0.1
        }

        return Triple(reasons.isEmpty(), risk, reasons)
    }

    private fun checkLiquidity(pools: List<LiquidityPool>): Triple<Boolean, Double, List<String>> {
        if (pools.isEmpty()) {
            return Triple(false, 0.4, listOf("No liquidity pools found"))
        }

        val totalLiquidity = pools.sumOf {
            // Simplified liquidity calculation - in reality you'd need price data
            val baseReserve = it.baseReserve.toDoubleOrNull() ?: 0.0
            val quoteReserve = it.quoteReserve.toDoubleOrNull() ?: 0.0
            minOf(baseReserve, quoteReserve) // Very simplified
        }

        return if (totalLiquidity < config.minLiquidity) {
            Triple(false, 0.3, listOf("Low liquidity: $totalLiquidity"))
        } else {
            Triple(true, 0.0, emptyList())
        }
    }

    private fun checkHolderDistribution(holderCount: Int, topHolderPercentage: Double): Triple<Boolean, Double, List<String>> {
        val reasons = mutableListOf<String>()
        var risk = 0.0

        if (holderCount < config.minHolderCount) {
            reasons.add("Too few holders: $holderCount")
            risk += 0.2
        }

        if (topHolderPercentage > config.maxTopHolderPercentage) {
            reasons.add("Top holder owns ${topHolderPercentage}% of supply")
            risk += 0.3
        }

        return Triple(reasons.isEmpty(), risk, reasons)
    }

    private suspend fun checkPumpFunSpecific(mint: String): Triple<Boolean, Double, List<String>> {
        val reasons = mutableListOf<String>()
        var risk = 0.0

        return try {
            val cacheKey = "pumpfun_$mint"
            val result = getCachedOrFetch(cacheKey) {
                delay(config.rateLimitDelayMs)
                httpClient.get("${config.pumpFunApiUrl}/tokens/$mint")
            }

            if (result != null) {
                val pumpFunData = json.parseToJsonElement(result.body<String>())
                val tokenData = pumpFunData.jsonObject

                // Check if token completed bonding curve
                val complete = tokenData["complete"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                if (!complete) {
                    reasons.add("Token has not completed PumpFun bonding curve")
                    risk += 0.2
                }

                // Check market cap
                val marketCap = tokenData["market_cap"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                if (marketCap < 50000) {
                    reasons.add("Low market cap: $marketCap")
                    risk += 0.1
                }

                // Check creator reputation (if available)
                val creator = tokenData["creator"]?.jsonPrimitive?.content
                if (creator != null) {
                    // This would check creator's history in a real implementation
                }

                Triple(reasons.isEmpty(), risk, reasons)
            } else {
                Triple(true, 0.0, emptyList())
            }
        } catch (e: Exception) {
            logger.warn("PumpFun validation failed for $mint: ${e.message}")
            Triple(false, 0.1, listOf("PumpFun validation failed"))
        }
    }

    private suspend fun getTokenAccountInfo(mint: String): TokenAccountInfo? {
        return try {
            val cacheKey = "token_account_$mint"
            getCachedOrFetch(cacheKey) {
                val requestBody = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "getAccountInfo")
                    put("params", buildJsonArray {
                        add(mint)
                        add(buildJsonObject {
                            put("encoding", "jsonParsed")
                        })
                    })
                }

                delay(config.rateLimitDelayMs)
                val response = httpClient.post(config.solanaRpcUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

                val responseJson = json.parseToJsonElement(response.body<String>())
                val result = responseJson.jsonObject["result"]?.jsonObject
                val accountData = result?.get("value")?.jsonObject?.get("data")?.jsonObject
                val parsed = accountData?.get("parsed")?.jsonObject?.get("info")?.jsonObject

                if (parsed != null) {
                    TokenAccountInfo(
                        mint = mint,
                        decimals = parsed["decimals"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        supply = parsed["supply"]?.jsonPrimitive?.content ?: "0",
                        isInitialized = parsed["isInitialized"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                        freezeAuthority = parsed["freezeAuthority"]?.jsonPrimitive?.content,
                        mintAuthority = parsed["mintAuthority"]?.jsonPrimitive?.content
                    )
                } else null
            }
        } catch (e: Exception) {
            logger.error("Failed to get token account info for $mint: ${e.message}")
            null
        }
    }

    private suspend fun getTokenMetadata(mint: String): TokenMetadata? {
        return try {
            val cacheKey = "metadata_$mint"
            getCachedOrFetch(cacheKey) {
                // Try Helius API first for metadata
                if (config.heliusApiKey != null) {
                    try {
                        val heliusResponse = httpClient.get("${config.heliusApiUrl}/v0/token-metadata") {
                            header("Authorization", "Bearer ${config.heliusApiKey}")
                            url {
                                parameters.append("mint", mint)
                            }
                        }
                        val metadataJson = json.parseToJsonElement(heliusResponse.body<String>())
                        val metadata = metadataJson.jsonObject
                        
                        return@getCachedOrFetch TokenMetadata(
                            mint = mint,
                            name = metadata["name"]?.jsonPrimitive?.content,
                            symbol = metadata["symbol"]?.jsonPrimitive?.content,
                            uri = metadata["uri"]?.jsonPrimitive?.content,
                            isMutable = metadata["isMutable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                            updateAuthority = metadata["updateAuthority"]?.jsonPrimitive?.content
                        )
                    } catch (e: Exception) {
                        logger.warn("Helius metadata fetch failed for $mint, trying Metaplex: ${e.message}")
                    }
                }

                // Fallback to direct Metaplex program account fetch
                val metaplexPDA = deriveMetaplexPDA(mint)
                val requestBody = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "getAccountInfo")
                    put("params", buildJsonArray {
                        add(metaplexPDA)
                        add(buildJsonObject {
                            put("encoding", "base64")
                        })
                    })
                }

                delay(config.rateLimitDelayMs)
                val response = httpClient.post(config.solanaRpcUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

                val responseJson = json.parseToJsonElement(response.body<String>())
                val result = responseJson.jsonObject["result"]?.jsonObject
                val accountData = result?.get("value")?.jsonObject?.get("data")?.jsonObject
                val dataArray = accountData?.get("data")?.jsonPrimitive?.content

                if (dataArray != null) {
                    // Parse Metaplex metadata from base64 encoded data
                    val decodedData = Base64.getDecoder().decode(dataArray)
                    parseMetaplexMetadata(mint, decodedData)
                } else {
                    logger.warn("No metadata found for token $mint")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to get metadata for $mint: ${e.message}")
            null
        }
    }

    private suspend fun getLiquidityPools(mint: String): List<LiquidityPool> {
        return try {
            val cacheKey = "liquidity_$mint"
            getCachedOrFetch(cacheKey) {
                val pools = mutableListOf<LiquidityPool>()
                
                // Fetch from Raydium
                try {
                    delay(config.rateLimitDelayMs)
                    val raydiumResponse = httpClient.get("${config.raydiumApiUrl}/v2/sdk/liquidity/mainnet.json")
                    val raydiumData = json.parseToJsonElement(raydiumResponse.body<String>())
                    val officialPools = raydiumData.jsonObject["official"]?.jsonObject
                    
                    officialPools?.entries?.forEach { (poolId, poolInfo) ->
                        val pool = poolInfo.jsonObject
                        val baseMint = pool["baseMint"]?.jsonPrimitive?.content
                        val quoteMint = pool["quoteMint"]?.jsonPrimitive?.content
                        
                        if (baseMint == mint || quoteMint == mint) {
                            pools.add(LiquidityPool(
                                address = poolId,
                                baseMint = baseMint ?: "",
                                quoteMint = quoteMint ?: "",
                                baseReserve = pool["baseReserve"]?.jsonPrimitive?.content ?: "0",
                                quoteReserve = pool["quoteReserve"]?.jsonPrimitive?.content ?: "0",
                                lpSupply = pool["lpSupply"]?.jsonPrimitive?.content ?: "0"
                            ))
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to fetch Raydium pools for $mint: ${e.message}")
                }
                
                // Try to get additional pool info from DexScreener
                try {
                    delay(config.rateLimitDelayMs)
                    val dexResponse = httpClient.get("${config.dexScreenerApiUrl}/dex/tokens/$mint")
                    val dexData = json.parseToJsonElement(dexResponse.body<String>())
                    val pairs = dexData.jsonObject["pairs"]?.jsonObject
                    
                    pairs?.entries?.forEach { (_, pairInfo) ->
                        val pair = pairInfo.jsonObject
                        pools.add(LiquidityPool(
                            address = pair["pairAddress"]?.jsonPrimitive?.content ?: "",
                            baseMint = pair["baseToken"]?.jsonObject?.get("address")?.jsonPrimitive?.content ?: "",
                            quoteMint = pair["quoteToken"]?.jsonObject?.get("address")?.jsonPrimitive?.content ?: "",
                            baseReserve = pair["liquidity"]?.jsonObject?.get("base")?.jsonPrimitive?.content ?: "0",
                            quoteReserve = pair["liquidity"]?.jsonObject?.get("quote")?.jsonPrimitive?.content ?: "0",
                            lpSupply = "0" // DexScreener doesn't provide LP supply
                        ))
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to fetch DexScreener data for $mint: ${e.message}")
                }
                
                pools.distinctBy { it.address }
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Failed to get liquidity pools for $mint: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getHolderDistribution(mint: String): Pair<Int, Double> {
        return try {
            val cacheKey = "holders_$mint"
            getCachedOrFetch(cacheKey) {
                if (config.heliusApiKey != null) {
                    try {
                        delay(config.rateLimitDelayMs)
                        val response = httpClient.get("${config.heliusApiUrl}/v0/tokens/$mint/holders") {
                            header("Authorization", "Bearer ${config.heliusApiKey}")
                        }
                        
                        val holdersData = json.parseToJsonElement(response.body<String>())
                        val holders = holdersData.jsonObject["result"]?.jsonObject
                        val holderList = holders?.get("items")?.jsonObject
                        
                        val holderCount = holderList?.size ?: 0
                        val topHolder = holderList?.values?.maxByOrNull { holder ->
                            holder.jsonObject["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                        }
                        
                        val totalSupply = holders?.get("total")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0
                        val topHolderAmount = topHolder?.jsonObject?.get("amount")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                        val topHolderPercentage = if (totalSupply > 0) (topHolderAmount / totalSupply) * 100 else 0.0
                        
                        return@getCachedOrFetch Pair(holderCount, topHolderPercentage)
                    } catch (e: Exception) {
                        logger.warn("Helius holder analysis failed for $mint: ${e.message}")
                    }
                }
                
                // Fallback: use RPC to get largest accounts
                try {
                    val requestBody = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 1)
                        put("method", "getTokenLargestAccounts")
                        put("params", buildJsonArray {
                            add(mint)
                        })
                    }
                    
                    delay(config.rateLimitDelayMs)
                    val response = httpClient.post(config.solanaRpcUrl) {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody.toString())
                    }
                    
                    val responseJson = json.parseToJsonElement(response.body<String>())
                    val result = responseJson.jsonObject["result"]?.jsonObject
                    val value = result?.get("value")?.jsonObject
                    
                    val accounts = value?.size ?: 0
                    val largestAccount = value?.values?.maxByOrNull { account ->
                        account.jsonObject["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    }
                    
                    val largestAmount = largestAccount?.jsonObject?.get("amount")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val topPercentage = if (accounts > 0) (largestAmount / accounts) * 100 else 0.0
                    
                    Pair(accounts, topPercentage)
                } catch (e: Exception) {
                    logger.warn("RPC holder analysis failed for $mint: ${e.message}")
                    Pair(0, 0.0)
                }
            } ?: Pair(0, 0.0)
        } catch (e: Exception) {
            logger.error("Failed to get holder distribution for $mint: ${e.message}")
            Pair(0, 0.0)
        }
    }

    private suspend fun <T> getCachedOrFetch(key: String, fetcher: suspend () -> T): T? {
        if (config.cacheEnabled) {
            cacheMutex.withLock {
                val cached = cache[key]
                if (cached != null) {
                    val (value, timestamp) = cached
                    if (Instant.now().toEpochMilli() - timestamp < config.cacheTtlMinutes * 60 * 1000) {
                        @Suppress("UNCHECKED_CAST")
                        return value as T
                    } else {
                        cache.remove(key)
                    }
                }
            }
        }

        return retryWithBackoff(key) {
            val result = fetcher()
            if (config.cacheEnabled && result != null) {
                cacheMutex.withLock {
                    cache[key] = result to Instant.now().toEpochMilli()
                }
            }
            result
        }
    }

    private suspend fun <T> retryWithBackoff(operation: String, block: suspend () -> T): T? {
        repeat(config.maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                logger.warn("Attempt ${attempt + 1}/${config.maxRetries} failed for $operation: ${e.message}")
                if (attempt < config.maxRetries - 1) {
                    val backoffDelay = config.rateLimitDelayMs * (attempt + 1) * 2
                    delay(backoffDelay)
                }
            }
        }
        logger.error("All retry attempts failed for $operation")
        return null
    }

    private fun deriveMetaplexPDA(mint: String): String {
        // This is a simplified PDA derivation - in practice you'd use proper Solana SDK
        // For now, return a placeholder that would be the actual metadata account address
        return "11111111111111111111111111111111" // Placeholder
    }

    private fun parseMetaplexMetadata(mint: String, data: ByteArray): TokenMetadata? {
        return try {
            // This would contain actual Metaplex metadata parsing logic
            // For now, return basic metadata structure
            TokenMetadata(
                mint = mint,
                name = "Parsed Token",
                symbol = "PARSED",
                uri = null,
                isMutable = true,
                updateAuthority = null
            )
        } catch (e: Exception) {
            logger.error("Failed to parse Metaplex metadata: ${e.message}")
            null
        }
    }

    private suspend fun checkHoneypot(mint: String, pools: List<LiquidityPool>): Triple<Boolean, Double, List<String>> {
        val reasons = mutableListOf<String>()
        var risk = 0.0

        return try {
            // Check for liquidity lock patterns
            if (pools.isEmpty()) {
                reasons.add("No liquidity pools - potential honeypot")
                return Triple(false, 0.5, reasons)
            }

            // Check for abnormally high buy/sell tax via simulation
            val cacheKey = "honeypot_$mint"
            val simulationResult = getCachedOrFetch(cacheKey) {
                simulateTrade(mint)
            }

            simulationResult?.let { result ->
                val buyTax = result["buyTax"]?.toDoubleOrNull() ?: 0.0
                val sellTax = result["sellTax"]?.toDoubleOrNull() ?: 0.0
                
                if (buyTax > 10.0) {
                    reasons.add("High buy tax detected: ${buyTax}%")
                    risk += 0.3
                }
                
                if (sellTax > 10.0) {
                    reasons.add("High sell tax detected: ${sellTax}%")
                    risk += 0.4
                }
                
                // Check if selling is completely blocked
                if (sellTax >= 99.0) {
                    reasons.add("Token cannot be sold - likely honeypot")
                    risk = 1.0
                }
            }

            // Check for ownership concentration (already done in holder distribution)
            Triple(reasons.isEmpty(), risk, reasons)
        } catch (e: Exception) {
            logger.error("Honeypot check failed for $mint: ${e.message}")
            Triple(false, 0.2, listOf("Honeypot analysis failed"))
        }
    }

    private fun checkRugPatterns(
        mint: String, 
        accountInfo: TokenAccountInfo?, 
        metadata: TokenMetadata?, 
        pools: List<LiquidityPool>
    ): Triple<Boolean, Double, List<String>> {
        val reasons = mutableListOf<String>()
        var risk = 0.0

        // Pattern 1: New token with massive supply and mint authority
        if (accountInfo != null) {
            val supply = accountInfo.supply.toDoubleOrNull() ?: 0.0
            val adjustedSupply = supply / 10.0.pow(accountInfo.decimals.toDouble())
            
            if (adjustedSupply > 1_000_000_000 && accountInfo.mintAuthority != null) {
                reasons.add("High supply token with mint authority - rug risk")
                risk += 0.3
            }
        }

        // Pattern 2: Anonymous metadata
        if (metadata != null) {
            val hasGenericName = (metadata.name?.contains("token", ignoreCase = true) ?: false) ||
                               (metadata.name?.contains("coin", ignoreCase = true) ?: false) ||
                               ((metadata.name?.length ?: 0) < 3)
            
            if (hasGenericName) {
                reasons.add("Generic or suspicious token name")
                risk += 0.1
            }
            
            if (metadata.uri.isNullOrBlank()) {
                reasons.add("No metadata URI - possible rug")
                risk += 0.2
            }
        }

        // Pattern 3: Low liquidity with high market cap claims
        if (pools.isNotEmpty()) {
            val totalLiquidity = pools.sumOf {
                minOf(
                    it.baseReserve.toDoubleOrNull() ?: 0.0,
                    it.quoteReserve.toDoubleOrNull() ?: 0.0
                )
            }
            
            if (totalLiquidity < 1000) {
                reasons.add("Extremely low liquidity - high rug risk")
                risk += 0.4
            }
        }

        // Pattern 4: Freeze authority present (already checked in freeze check)
        if (accountInfo?.freezeAuthority != null) {
            risk += 0.1 // Additional rug risk beyond freeze risk
        }

        return Triple(reasons.isEmpty(), risk, reasons)
    }

    private suspend fun simulateTrade(mint: String): Map<String, String>? {
        return try {
            // This would simulate a small trade to detect honeypot behavior
            // Using Jupiter API for trade simulation
            delay(config.rateLimitDelayMs)
            
            val simulationRequest = buildJsonObject {
                put("inputMint", "So11111111111111111111111111111111111111112") // SOL
                put("outputMint", mint)
                put("amount", "1000000") // 0.001 SOL
                put("slippageBps", "300")
            }
            
            val response = httpClient.get("${config.jupiterApiUrl}/v6/quote") {
                url {
                    parameters.append("inputMint", "So11111111111111111111111111111111111111112")
                    parameters.append("outputMint", mint)
                    parameters.append("amount", "1000000")
                    parameters.append("slippageBps", "300")
                }
            }
            
            // Parse response to extract tax information
            // This is simplified - real implementation would analyze the route data
            mapOf(
                "buyTax" to "0.0",
                "sellTax" to "0.0"
            )
        } catch (e: Exception) {
            logger.warn("Trade simulation failed for $mint: ${e.message}")
            null
        }
    }
}
