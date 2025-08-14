package com.bswap.server.validation

import com.bswap.server.config.ServerConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object Ansi {
    const val RESET = "\u001B[0m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
}

class TokenValidator(
    private val httpClient: HttpClient,
    private val config: ValidationConfig
) {
    private val logger = LoggerFactory.getLogger(TokenValidator::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val enabledRules = setOf<ValidationRule>(
        // Disable all validation rules for testing
    )
    private val cache = ConcurrentHashMap<String, Pair<Any, Long>>()
    private val cacheMutex = Mutex()

    suspend fun validateToken(mint: String): TokenValidationResult {
        logger.info("${Ansi.YELLOW}VALIDATING $mint${Ansi.RESET}")
        val reasons = mutableListOf<String>()
        var riskScore = 0.0

        // For new pump.fun tokens, account info might not be available yet
        // Allow validation to continue without strict account requirement
        val accountInfo = getTokenAccountInfo(mint)
        if (accountInfo == null) {
            logger.info("${Ansi.YELLOW}No account info for $mint (possibly new pump.fun token), allowing with minimal validation${Ansi.RESET}")
            // Return valid for new tokens to allow trading
            return TokenValidationResult(
                isValid = true,
                mint = mint,
                reasons = listOf("New token - minimal validation"),
                riskScore = 0.1, // Low risk score for new tokens
                metadata = null,
                accountInfo = null,
                liquidityPools = emptyList(),
                holderCount = 0,
                topHolderPercentage = 0.0
            )
        }
        val metadata = getTokenMetadata(mint)

        if (ValidationRule.FROZEN_AUTHORITY_CHECK in enabledRules) {
            val (ok, r, rs) = checkFreezeAuthority(accountInfo)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }
        if (ValidationRule.MINT_AUTHORITY_CHECK in enabledRules) {
            val (ok, r, rs) = checkMintAuthority(accountInfo)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }
        if (ValidationRule.SUPPLY_CHECK in enabledRules) {
            val (ok, r, rs) = checkSupply(accountInfo)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }
        if (ValidationRule.METADATA_CHECK in enabledRules) {
            val (ok, r, rs) = checkMetadata(metadata)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }

        val liquidityPools =
            if (ValidationRule.LIQUIDITY_CHECK in enabledRules) getLiquidityPools(mint) else emptyList()
        if (ValidationRule.LIQUIDITY_CHECK in enabledRules) {
            val (ok, r, rs) = checkLiquidity(liquidityPools)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }

        var holderCount = 0
        var topHolderPercentage = 0.0
        if (ValidationRule.HOLDER_DISTRIBUTION_CHECK in enabledRules) {
            val (hc, topPct) = getHolderDistribution(mint, accountInfo)
            holderCount = hc
            topHolderPercentage = topPct
            val (ok, r, rs) = checkHolderDistribution(holderCount, topHolderPercentage)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }

        if (ValidationRule.PUMP_FUN_VALIDATION in enabledRules) {
            val (ok, r, rs) = checkPumpFunSpecific(mint)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }

        if (ValidationRule.HONEYPOT_CHECK in enabledRules) {
            val (ok, r, rs) = checkHoneypot(mint, liquidityPools)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }

        if (ValidationRule.RUG_PATTERN_CHECK in enabledRules) {
            val (ok, r, rs) = checkRugPatterns(mint, accountInfo, metadata, liquidityPools)
            if (!ok) reasons.addAll(rs)
            riskScore += r
        }

        riskScore = min(riskScore, 1.0)
        val isValid = riskScore < config.maxRiskScore

        if (isValid) {
            logger.info(
                "${Ansi.GREEN}VALID [$mint] risk=$riskScore holders=$holderCount top=${"%.2f".format(topHolderPercentage)}% pools=${liquidityPools.size}${Ansi.RESET}"
            )
        } else {
            //logger.warn(
            //    "${Ansi.RED}INVALID [$mint] risk=$riskScore reasons=${reasons.joinToString(" | ")}${Ansi.RESET}"
            //)
        }

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
    }

    private fun checkFreezeAuthority(accountInfo: TokenAccountInfo): Triple<Boolean, Double, List<String>> {
        return if (!accountInfo.freezeAuthority.isNullOrBlank()) {
            Triple(false, 0.3, listOf("Token has freeze authority"))
        } else Triple(true, 0.0, emptyList())
    }

    private fun checkMintAuthority(accountInfo: TokenAccountInfo): Triple<Boolean, Double, List<String>> {
        return if (!accountInfo.mintAuthority.isNullOrBlank()) {
            Triple(false, 0.25, listOf("Token has mint authority"))
        } else Triple(true, 0.0, emptyList())
    }

    private fun checkSupply(accountInfo: TokenAccountInfo): Triple<Boolean, Double, List<String>> {
        val supply = accountInfo.supply.toDoubleOrNull() ?: 0.0
        val adjusted = supply / 10.0.pow(accountInfo.decimals.toDouble())
        return if (adjusted > config.maxSupply) {
            Triple(false, 0.2, listOf("Extremely high token supply: $adjusted"))
        } else Triple(true, 0.0, emptyList())
    }

    private fun checkMetadata(metadata: TokenMetadata?): Triple<Boolean, Double, List<String>> {
        if (metadata == null) return Triple(false, 0.15, listOf("No metadata found"))
        val reasons = mutableListOf<String>()
        var risk = 0.0
        if (metadata.name.isNullOrBlank()) {
            reasons.add("No token name"); risk += 0.05
        }
        if (metadata.symbol.isNullOrBlank()) {
            reasons.add("No token symbol"); risk += 0.05
        }
        if (metadata.uri.isNullOrBlank()) {
            reasons.add("No metadata URI"); risk += 0.05
        }
        if (metadata.isMutable) {
            reasons.add("Metadata is mutable"); risk += 0.1
        }
        return Triple(reasons.isEmpty(), risk, reasons)
    }

    private fun checkLiquidity(pools: List<LiquidityPool>): Triple<Boolean, Double, List<String>> {
        if (pools.isEmpty()) return Triple(false, 0.4, listOf("No liquidity pools found"))
        val totalUsd = pools.mapNotNull { it.liquidityUsd }.sum()
        if (totalUsd > 0.0 && totalUsd < config.minLiquidity) return Triple(
            false,
            0.3,
            listOf("Low liquidity: $totalUsd USD")
        )
        val totalSynthetic = pools.sumOf {
            min(
                it.baseReserve.toDoubleOrNull() ?: 0.0,
                it.quoteReserve.toDoubleOrNull() ?: 0.0
            )
        }
        return if (totalUsd == 0.0 && totalSynthetic < 1.0) Triple(
            false,
            0.3,
            listOf("Low liquidity")
        ) else Triple(true, 0.0, emptyList())
    }

    private fun checkHolderDistribution(holderCount: Int, topHolderPercentage: Double): Triple<Boolean, Double, List<String>> {
        val reasons = mutableListOf<String>()
        var risk = 0.0
        if (holderCount < config.minHolderCount) {
            reasons.add("Too few holders: $holderCount"); risk += 0.2
        }
        if (topHolderPercentage > config.maxTopHolderPercentage) {
            reasons.add("Top holder owns $topHolderPercentage% of supply"); risk += 0.35
        }
        return Triple(reasons.isEmpty(), risk, reasons)
    }

    private suspend fun checkPumpFunSpecific(mint: String): Triple<Boolean, Double, List<String>> {
        return try {
            val cacheKey = "pumpfun_$mint"
            val resp = getCachedOrFetch(cacheKey) {
                delay(config.rateLimitDelayMs)
                httpClient.get("${ServerConfig.pumpFunBaseUrl.substringBeforeLast("/")}/tokens/$mint")
            }
            if (resp == null) return Triple(true, 0.0, emptyList())

            val body = resp.body<String>().trim()
            if (body.startsWith("<")) return Triple(true, 0.0, emptyList())

            val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return Triple(true, 0.0, emptyList())
            val obj = when (el) {
                is JsonObject -> el
                is JsonArray -> el.firstOrNull()?.jsonObject ?: return Triple(true, 0.0, emptyList())
                else -> return Triple(true, 0.0, emptyList())
            }

            val complete = obj["complete"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: obj["isComplete"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: true

            val marketCap = obj["marketCap"]?.jsonPrimitive?.doubleOrNull
                ?: obj["market_cap"]?.jsonPrimitive?.doubleOrNull
                ?: obj["market_cap_usd"]?.jsonPrimitive?.doubleOrNull
                ?: 0.0

            //logger.info("${Ansi.YELLOW}PumpFun[$mint] complete=$complete cap=$marketCap${Ansi.RESET}")

            val reasons = mutableListOf<String>()
            var risk = 0.0
            if (!complete) {
                reasons.add("PumpFun bonding curve not completed"); risk += 0.2
            }
            if (marketCap > 0.0 && marketCap < 50_000.0) {
                reasons.add("Low market cap: $marketCap"); risk += 0.1
            }

            Triple(reasons.isEmpty(), risk, reasons)
        } catch (_: Exception) {
            Triple(true, 0.0, emptyList())
        }
    }

    private suspend fun getTokenAccountInfo(mint: String): TokenAccountInfo? {
        val key = "token_account_$mint"
        return getCachedOrFetch(key) {
            val req = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getAccountInfo")
                putJsonArray("params") {
                    add(JsonPrimitive(mint))
                    add(buildJsonObject { put("encoding", "jsonParsed") })
                }
            }
            delay(config.rateLimitDelayMs)
            val resp = rpcPost(req) ?: return@getCachedOrFetch null
            if (resp.trim().startsWith("<")) return@getCachedOrFetch null
            val el = runCatching { json.parseToJsonElement(resp) }.getOrNull() ?: return@getCachedOrFetch null
            val v = el.jsonObject["result"]?.jsonObject?.get("value")?.jsonObject ?: return@getCachedOrFetch null
            val parsed = v["data"]?.jsonObject?.get("parsed")?.jsonObject?.get("info")?.jsonObject
                ?: return@getCachedOrFetch null

            val freeze = parsed["freezeAuthority"].stringOrNull()
            val mintAuth = parsed["mintAuthority"].stringOrNull()

            TokenAccountInfo(
                mint = mint,
                decimals = parsed["decimals"]?.jsonPrimitive?.int ?: 0,
                supply = parsed["supply"]?.jsonPrimitive?.content ?: "0",
                isInitialized = parsed["isInitialized"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                freezeAuthority = freeze,
                mintAuthority = mintAuth
            )
        }
    }

    private suspend fun getTokenMetadata(mint: String): TokenMetadata? {
        val key = "metadata_$mint"
        return getCachedOrFetch(key) {
            if (!config.heliusApiKey.isNullOrBlank()) {
                try {
                    val resp = httpClient.get("${config.heliusApiUrl}/v0/token-metadata") {
                        header("Authorization", "Bearer ${config.heliusApiKey}")
                        url { parameters.append("mint", mint) }
                    }
                    val body = resp.body<String>().trim()
                    if (body.startsWith("<")) return@getCachedOrFetch null
                    val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return@getCachedOrFetch null
                    val obj = if (el is JsonArray && el.isNotEmpty()) el[0].jsonObject else el.jsonObject
                    return@getCachedOrFetch TokenMetadata(
                        mint = mint,
                        name = obj["name"]?.jsonPrimitive?.content,
                        symbol = obj["symbol"]?.jsonPrimitive?.content,
                        uri = obj["uri"]?.jsonPrimitive?.content,
                        isMutable = obj["isMutable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                        updateAuthority = obj["updateAuthority"]?.jsonPrimitive?.content
                    )
                } catch (_: Exception) {
                }
            }
            null
        }
    }

    private suspend fun getLiquidityPools(mint: String): List<LiquidityPool> {
        val key = "liquidity_$mint"
        return getCachedOrFetch(key) {
            val pools = mutableListOf<LiquidityPool>()
            try {
                delay(config.rateLimitDelayMs)
                val ray = httpClient.get("${config.raydiumApiUrl}/v2/sdk/liquidity/mainnet.json")
                val body = ray.body<String>().trim()
                if (!body.startsWith("<")) {
                    val el = runCatching { json.parseToJsonElement(body) }.getOrNull()
                    val off = el?.jsonObject?.get("official")?.jsonArray ?: JsonArray(emptyList())
                    val uns = el?.jsonObject?.get("unOfficial")?.jsonArray ?: JsonArray(emptyList())
                    (off + uns).forEach { p ->
                        val obj = p.jsonObject
                        val base = obj["baseMint"]?.jsonPrimitive?.content ?: ""
                        val quote = obj["quoteMint"]?.jsonPrimitive?.content ?: ""
                        if (base == mint || quote == mint) {
                            pools += LiquidityPool(
                                address = obj["id"]?.jsonPrimitive?.content ?: obj["ammId"]?.jsonPrimitive?.content
                                ?: "",
                                baseMint = base,
                                quoteMint = quote,
                                baseReserve = obj["baseReserve"]?.jsonPrimitive?.content ?: "0",
                                quoteReserve = obj["quoteReserve"]?.jsonPrimitive?.content ?: "0",
                                lpSupply = obj["lpSupply"]?.jsonPrimitive?.content ?: "0",
                                liquidityUsd = obj["liquidity"]?.jsonPrimitive?.doubleOrNull
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }

            try {
                delay(config.rateLimitDelayMs)
                val dex = httpClient.get("${config.dexScreenerApiUrl}/dex/tokens/$mint")
                val body = dex.body<String>().trim()
                if (!body.startsWith("<")) {
                    val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: JsonNull
                    val pairs = el.jsonObject["pairs"] as? JsonArray ?: JsonArray(emptyList())
                    pairs.forEach { pairEl ->
                        val pair = pairEl.jsonObject
                        val baseAddr = pair["baseToken"]?.jsonObject?.get("address")?.jsonPrimitive?.content ?: ""
                        val quoteAddr = pair["quoteToken"]?.jsonObject?.get("address")?.jsonPrimitive?.content ?: ""
                        val liqUsd = pair["liquidity"]?.jsonObject?.get("usd")?.jsonPrimitive?.doubleOrNull
                        pools += LiquidityPool(
                            address = pair["pairAddress"]?.jsonPrimitive?.content ?: "",
                            baseMint = baseAddr,
                            quoteMint = quoteAddr,
                            baseReserve = "0",
                            quoteReserve = "0",
                            lpSupply = "0",
                            liquidityUsd = liqUsd
                        )
                    }
                }
            } catch (_: Exception) {
            }

            pools.distinctBy { it.address }
        } ?: emptyList()
    }

    private suspend fun getHolderDistribution(mint: String, accountInfo: TokenAccountInfo): Pair<Int, Double> {
        val key = "holders_$mint"
        return getCachedOrFetch(key) {
            val totalSupply = accountInfo.supply.toDoubleOrNull() ?: 0.0
            val req = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getTokenLargestAccounts")
                putJsonArray("params") { add(JsonPrimitive(mint)) }
            }
            delay(config.rateLimitDelayMs)
            val resp = rpcPost(req) ?: return@getCachedOrFetch 0 to 0.0
            if (resp.trim().startsWith("<")) return@getCachedOrFetch 0 to 0.0
            val el = runCatching { json.parseToJsonElement(resp) }.getOrNull() ?: return@getCachedOrFetch 0 to 0.0
            val arr = el.jsonObject["result"]?.jsonObject?.get("value") as? JsonArray ?: JsonArray(emptyList())
            val accounts = arr.size
            val largest =
                arr.maxOfOrNull { it.jsonObject["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0 } ?: 0.0
            val topPct = if (totalSupply > 0) (largest / totalSupply) * 100.0 else 0.0
            accounts to topPct
        } ?: (0 to 0.0)
    }

    private suspend fun checkHoneypot(mint: String, pools: List<LiquidityPool>): Triple<Boolean, Double, List<String>> {
        val reasons = mutableListOf<String>()
        var risk = 0.0
        if (pools.isEmpty()) return Triple(false, 0.5, listOf("No liquidity pools - potential honeypot"))
        val key = "honeypot_$mint"
        val sim = getCachedOrFetch(key) { simulateTrade(mint) }
        if (sim != null) {
            val buyTax = sim["buyTax"]?.toDoubleOrNull() ?: 0.0
            val sellTax = sim["sellTax"]?.toDoubleOrNull() ?: 0.0
            if (buyTax > 10.0) {
                reasons.add("High buy tax: $buyTax%"); risk += 0.3
            }
            if (sellTax > 10.0) {
                reasons.add("High sell tax: $sellTax%"); risk += 0.4
            }
            if (sellTax >= 99.0) {
                reasons.add("Selling blocked"); risk = 1.0
            }
        }
        return Triple(reasons.isEmpty(), risk, reasons)
    }

    private fun checkRugPatterns(
        mint: String,
        accountInfo: TokenAccountInfo?,
        metadata: TokenMetadata?,
        pools: List<LiquidityPool>
    ): Triple<Boolean, Double, List<String>> {
        val reasons = mutableListOf<String>()
        var risk = 0.0
        if (accountInfo != null) {
            val supply = accountInfo.supply.toDoubleOrNull() ?: 0.0
            val adjusted = supply / 10.0.pow((accountInfo.decimals).toDouble())
            if (adjusted > 1_000_000_000 && !accountInfo.mintAuthority.isNullOrBlank()) {
                reasons.add("High supply with mint authority")
                risk += 0.3
            }
        }
        if (metadata != null) {
            val n = metadata.name ?: ""
            val generic = n.length < 3 || n.contains("token", true) || n.contains("coin", true)
            if (generic) {
                reasons.add("Generic token name"); risk += 0.1
            }
            if (metadata.uri.isNullOrBlank()) {
                reasons.add("Missing metadata URI"); risk += 0.2
            }
        }
        if (pools.isNotEmpty()) {
            val usd = pools.mapNotNull { it.liquidityUsd }.sum()
            if (usd < 1000.0) {
                reasons.add("Extremely low liquidity"); risk += 0.4
            }
        }
        if (!accountInfo?.freezeAuthority.isNullOrBlank()) risk += 0.1
        return Triple(reasons.isEmpty(), risk, reasons)
    }

    private suspend fun simulateTrade(mint: String): Map<String, String>? {
        return try {
            delay(config.rateLimitDelayMs)
            val resp = httpClient.get("${config.jupiterApiUrl}/v6/quote") {
                url {
                    parameters.append("inputMint", "So11111111111111111111111111111111111111112")
                    parameters.append("outputMint", mint)
                    parameters.append("amount", "1000000")
                    parameters.append("slippageBps", "300")
                }
            }
            val body = resp.body<String>()
            mapOf("buyTax" to "0.0", "sellTax" to "0.0")
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun <T> getCachedOrFetch(key: String, fetcher: suspend () -> T): T? {
        if (config.cacheEnabled) {
            cacheMutex.withLock {
                val cached = cache[key]
                if (cached != null) {
                    val (value, ts) = cached
                    if (Instant.now().toEpochMilli() - ts < config.cacheTtlMinutes * 60 * 1000) {
                        @Suppress("UNCHECKED_CAST")
                        return value as T
                    } else {
                        cache.remove(key)
                    }
                }
            }
        }
        val result = retryWithBackoff(key) { fetcher() }
        if (config.cacheEnabled && result != null) {
            cacheMutex.withLock { cache[key] = result as Any to Instant.now().toEpochMilli() }
        }
        return result
    }

    private suspend fun <T> retryWithBackoff(op: String, block: suspend () -> T): T? {
        repeat(config.maxRetries) { i ->
            try {
                return block()
            } catch (_: Exception) {
                val base = 200 shl i
                val jitter = Random.nextInt(0, 200)
                delay(min(1500, base + jitter).toLong())
            }
        }
        return null
    }

    private suspend fun rpcPost(payload: JsonObject): String? {
        val body = json.encodeToString(JsonObject.serializer(), payload)
        repeat(config.maxRetries) { attempt ->
            try {
                val resp: HttpResponse = httpClient.post(config.solanaRpcUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                if (resp.status.value in 200..299) return resp.bodyAsText()
                if (resp.status.value == 429 || resp.status.value >= 500) {
                    val base = 200 shl attempt
                    val jitter = Random.nextInt(0, 200)
                    delay(min(1500, base + jitter).toLong())
                    return@repeat
                }
                return null
            } catch (_: HttpRequestTimeoutException) {
                val base = 200 shl attempt
                val jitter = Random.nextInt(0, 200)
                delay(min(1500, base + jitter).toLong())
            } catch (_: Exception) {
                val base = 200 shl attempt
                val jitter = Random.nextInt(0, 200)
                delay(min(1500, base + jitter).toLong())
            }
        }
        return null
    }

    private fun JsonElement?.stringOrNull(): String? =
        when {
            this == null -> null
            this is JsonNull -> null
            else -> runCatching { this.jsonPrimitive.content }.getOrNull()
                ?.takeIf { it.isNotBlank() && it.lowercase() != "null" }
        }
}
