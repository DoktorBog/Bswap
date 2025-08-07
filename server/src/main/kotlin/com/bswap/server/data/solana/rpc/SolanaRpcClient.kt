package com.bswap.server.data.solana.rpc

import com.bswap.server.RPC_URL
import com.bswap.server.service.TokenMetadata
import com.bswap.shared.model.HistoryPage
import com.bswap.shared.model.SolanaTx
import com.bswap.shared.model.TokenInfo
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import foundation.metaplex.rpc.Commitment
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.RpcGetBalanceConfiguration
import foundation.metaplex.rpc.networking.NetworkDriver
import foundation.metaplex.solanapublickeys.PublicKey
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.math.min
import kotlin.random.Random

class SolanaRpcClient(
    private val http: HttpClient,
    private val rpcUrl: String = RPC_URL,
    private val tokenMetadataService: com.bswap.server.service.TokenMetadataService? = null
) {

    private object Conf {
        const val SIGN_PAGE_LIMIT_DEFAULT = 50
        const val BATCH_LIMIT = 40
        const val PARALLEL_BATCHES = 3
        const val PAGE_TTL_SEC = 30L
        const val TX_TTL_MIN = 10L
        const val NEGATIVE_TTL_MIN = 3L
        const val RETRIES = 3
        const val RETRY_BASE_DELAY_MS = 200
        const val RETRY_MAX_DELAY_MS = 1500
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(javaClass)
    private val rpc = RPC(rpcUrl, NetworkDriver(http))

    private val pageCache: Cache<String, HistoryPage> = Caffeine.newBuilder()
        .maximumSize(5_000)
        .expireAfterWrite(Duration.ofSeconds(Conf.PAGE_TTL_SEC))
        .build()

    private val txCache: Cache<String, List<SolanaTx>> = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(Duration.ofMinutes(Conf.TX_TTL_MIN))
        .build()

    private val negativeTxCache: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(Duration.ofMinutes(Conf.NEGATIVE_TTL_MIN))
        .build()

    private val guard = Semaphore(Conf.PARALLEL_BATCHES)

    suspend fun getBalance(address: String): Long {
        val key = PublicKey(address)
        return rpc.getBalance(key, RpcGetBalanceConfiguration(commitment = Commitment.finalized))
    }

    suspend fun getSPLTokens(owner: String): List<TokenInfo> = coroutineScope {
        val req = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getTokenAccountsByOwner")
            putJsonArray("params") {
                add(JsonPrimitive(owner))
                add(buildJsonObject { put("programId", "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA") })
                add(buildJsonObject { put("encoding", "jsonParsed") })
            }
        }
        val body = rpcPost(req) ?: return@coroutineScope emptyList()
        val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return@coroutineScope emptyList()
        val values = el.jsonObject["result"]?.jsonObject?.get("value")?.jsonArray ?: return@coroutineScope emptyList()
        values.mapNotNull { item ->
            runCatching {
                val info = item.jsonObject["account"]!!.jsonObject["data"]!!.jsonObject["parsed"]!!.jsonObject["info"]!!.jsonObject
                val mint = info["mint"]!!.jsonPrimitive.content
                val amount = info["tokenAmount"]!!.jsonObject["amount"]!!.jsonPrimitive.content
                val decimals = info["tokenAmount"]!!.jsonObject["decimals"]!!.jsonPrimitive.int
                TokenInfo(mint, amount, decimals = decimals)
            }.getOrNull()
        }
    }

    suspend fun getHistory(
        address: String,
        limit: Int = Conf.SIGN_PAGE_LIMIT_DEFAULT,
        before: String? = null
    ): HistoryPage =
        coroutineScope {
            val key = "$address:$before:$limit"
            pageCache.getIfPresent(key)?.let { return@coroutineScope it }
            val page = loadHistory(address, limit, before)
            pageCache.put(key, page)
            page
        }

    suspend fun getAllHistory(address: String, maxTransactions: Int = 500): HistoryPage = coroutineScope {
        val all = mutableListOf<SolanaTx>()
        var cursor: String? = null
        while (all.size < maxTransactions) {
            val page = getHistory(address, min(50, maxTransactions - all.size), cursor)
            if (page.transactions.isEmpty()) break
            all += page.transactions
            cursor = page.nextCursor ?: break
        }
        HistoryPage(all, cursor)
    }

    private suspend fun loadHistory(address: String, limit: Int, before: String?): HistoryPage = coroutineScope {
        val signatures = getSignatures(address, limit, before)
        if (signatures.isEmpty()) return@coroutineScope HistoryPage(emptyList(), null)
        val batches = signatures.chunked(Conf.BATCH_LIMIT)
        val fetched = batches.map { batch ->
            guard.withPermit {
                async(Dispatchers.IO) { fetchBatch(batch, address) }
            }
        }.let { jobs -> jobs.map { it.await() } }.flatten()

        // Enrich SPL tokens with metadata
        val enriched = if (tokenMetadataService != null) {
            enrichWithTokenMetadata(fetched)
        } else {
            fetched
        }

        HistoryPage(enriched, signatures.lastOrNull())
    }

    private suspend fun getSignatures(address: String, limit: Int, before: String?): List<String> {
        val req = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getSignaturesForAddress")
            putJsonArray("params") {
                add(JsonPrimitive(address))
                add(buildJsonObject {
                    put("limit", limit)
                    put("commitment", "finalized")
                    before?.let { put("before", it) }
                })
            }
        }
        val body = rpcPost(req) ?: return emptyList()
        val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        if (el.jsonObject["error"] != null) return emptyList()
        return el.jsonObject["result"]?.jsonArray
            ?.mapNotNull { it.jsonObject["signature"]?.jsonPrimitive?.content }
            ?: emptyList()
    }

    private suspend fun fetchBatch(signatures: List<String>, address: String): List<SolanaTx> {
        val cachedPairs = signatures.mapNotNull { sig ->
            txCache.getIfPresent(sig)?.let { sig to it }
        }
        val cachedEvents = cachedPairs.flatMap { it.second }
        val alreadyHave = cachedPairs.map { it.first }.toSet() +
            signatures.filter { negativeTxCache.getIfPresent(it) == true }.toSet()
        val remaining = signatures.filterNot { alreadyHave.contains(it) }
        if (remaining.isEmpty()) return cachedEvents
        val viaNew = tryMultiGetTransactionsMap(remaining, address)
        val still = remaining - viaNew.keys
        val viaBatch = if (still.isNotEmpty()) tryBatchGetTransactionCallsMap(still, address) else emptyMap()
        val tailStill = still - viaBatch.keys
        val tail = if (tailStill.isNotEmpty()) fetchIndividuallyMap(tailStill, address) else emptyMap()
        (viaNew + viaBatch + tail).forEach { (sig, events) -> txCache.put(sig, events) }
        val allGot = viaNew.keys + viaBatch.keys + tail.keys
        (remaining - allGot).forEach { negativeTxCache.put(it, true) }
        return cachedEvents + viaNew.values.flatten() + viaBatch.values.flatten() + tail.values.flatten()
    }

    private suspend fun tryMultiGetTransactionsMap(
        signatures: List<String>,
        address: String
    ): Map<String, List<SolanaTx>> {
        if (signatures.isEmpty()) return emptyMap()
        val req = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", 1); put("method", "getTransactions")
            putJsonArray("params") {
                add(json.encodeToJsonElement(signatures))
                add(buildJsonObject {
                    put("encoding", "jsonParsed")
                    put("maxSupportedTransactionVersion", 0)
                })
            }
        }
        val body = rpcPost(req) ?: return emptyMap()
        val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyMap()
        if (el.jsonObject["error"] != null) return emptyMap()
        val results = el.jsonObject["result"]?.jsonArray ?: return emptyMap()
        return results.mapIndexedNotNull { idx, jsonEl ->
            val sig = signatures[idx]
            val obj = jsonEl.takeIf { it !is JsonNull }?.jsonObject ?: return@mapIndexedNotNull null
            val events = parseTransactions(obj, sig, address)
            sig to events
        }.toMap()
    }

    private suspend fun tryBatchGetTransactionCallsMap(
        signatures: Collection<String>,
        address: String
    ): Map<String, List<SolanaTx>> {
        if (signatures.isEmpty()) return emptyMap()
        val calls: List<JsonObject> = signatures.mapIndexed { idx, sig ->
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", idx + 1)
                put("method", "getTransaction")
                putJsonArray("params") {
                    add(JsonPrimitive(sig))
                    add(buildJsonObject {
                        put("encoding", "jsonParsed")
                        put("maxSupportedTransactionVersion", 0)
                    })
                }
            }
        }
        val body = rpcPostBatch(calls) ?: return emptyMap()
        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return emptyMap()
        val byId = arr.mapNotNull { it as? JsonObject }.associateBy { it["id"]!!.jsonPrimitive.int }
        return calls.mapNotNull { call ->
            val id = call["id"]!!.jsonPrimitive.int
            val sig = call["params"]!!.jsonArray[0].jsonPrimitive.content
            val resp = byId[id] ?: return@mapNotNull null
            val result = resp["result"] ?: return@mapNotNull null
            sig to parseTransactions(result.jsonObject, sig, address)
        }.toMap()
    }

    private suspend fun fetchIndividuallyMap(
        signatures: Collection<String>,
        address: String
    ): Map<String, List<SolanaTx>> = coroutineScope {
        signatures.map { sig ->
            async(Dispatchers.IO) {
                val req = buildJsonObject {
                    put("jsonrpc", "2.0"); put("id", 1); put("method", "getTransaction")
                    putJsonArray("params") {
                        add(JsonPrimitive(sig))
                        add(buildJsonObject {
                            put("encoding", "jsonParsed")
                            put("maxSupportedTransactionVersion", 0)
                        })
                    }
                }
                val body = rpcPost(req) ?: return@async null
                val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return@async null
                val result = el.jsonObject["result"] ?: return@async null
                sig to parseTransactions(result.jsonObject, sig, address)
            }
        }.mapNotNull { it.await() }.toMap()
    }

    private fun parseTransactions(obj: JsonObject, sig: String, address: String): List<SolanaTx> {
        val events = mutableListOf<SolanaTx>()
        val meta = obj["meta"]?.jsonObject ?: return emptyList()
        if (meta["err"] != null && meta["err"] !is JsonNull) return emptyList()
        val solIdx = run {
            val txn = obj["transaction"]?.jsonObject ?: return@run -1
            val message = txn["message"]?.jsonObject ?: return@run -1
            val keys = message["accountKeys"]?.jsonArray ?: return@run -1
            keys.indexOfFirst { el ->
                when (el) {
                    is JsonPrimitive -> el.content.equals(address, true)
                    is JsonObject -> el["pubkey"]?.jsonPrimitive?.content.equals(address, true)
                    else -> false
                }
            }
        }
        if (solIdx >= 0) {
            val pre = meta["preBalances"]?.jsonArray?.getOrNull(solIdx)?.jsonPrimitive?.long
            val post = meta["postBalances"]?.jsonArray?.getOrNull(solIdx)?.jsonPrimitive?.long
            if (pre != null && post != null) {
                val change = post - pre
                if (change != 0L) {
                    val amountSol = kotlin.math.abs(change.toDouble()) / 1_000_000_000.0
                    events += SolanaTx(
                        signature = sig,
                        address = address,
                        amount = amountSol,
                        incoming = change > 0,
                        asset = SolanaTx.Asset.SOL
                    )
                }
            }
        }
        events += parseSPLDeltas(meta, address, sig)
        return events
    }

    private fun parseSPLDeltas(meta: JsonObject, ownerAddress: String, sig: String): List<SolanaTx> {
        val pre = meta["preTokenBalances"]?.jsonArray ?: JsonArray(emptyList())
        val post = meta["postTokenBalances"]?.jsonArray ?: JsonArray(emptyList())

        data class Key(val owner: String?, val mint: String, val accountIndex: Int?)
        data class TB(val owner: String?, val mint: String, val accountIndex: Int?, val ui: Double, val decimals: Int)

        fun toTB(el: JsonElement): TB? = runCatching {
            val o = el.jsonObject
            val owner = o["owner"]?.jsonPrimitive?.contentOrNull
            val mint = o["mint"]!!.jsonPrimitive.content
            val idx = o["accountIndex"]?.jsonPrimitive?.intOrNull
            val uiObj = o["uiTokenAmount"]!!.jsonObject
            val decimals = uiObj["decimals"]!!.jsonPrimitive.int
            val ui = uiObj["uiAmount"]?.jsonPrimitive?.doubleOrNull
                ?: uiObj["uiAmountString"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
                    ?.movePointLeft(decimals)?.toDouble()
                ?: run {
                    val raw = uiObj["amount"]!!.jsonPrimitive.content
                    raw.toBigDecimal().movePointLeft(decimals).toDouble()
                }
            TB(owner, mint, idx, ui, decimals)
        }.getOrNull()

        val preMap = pre.mapNotNull(::toTB).associateBy { Key(it.owner, it.mint, it.accountIndex) }
        val postMap = post.mapNotNull(::toTB).associateBy { Key(it.owner, it.mint, it.accountIndex) }
        val keys = (preMap.keys + postMap.keys)
            .filter { k -> k.owner?.equals(ownerAddress, true) == true }
            .distinct()
        if (keys.isEmpty()) return emptyList()

        val out = mutableListOf<SolanaTx>()
        for (k in keys) {
            val preUi = preMap[k]?.ui ?: 0.0
            val postUi = postMap[k]?.ui ?: 0.0
            val dec = (postMap[k]?.decimals ?: preMap[k]?.decimals) ?: continue
            val delta = postUi - preUi
            if (delta == 0.0) continue
            out += SolanaTx(
                signature = sig,
                address = ownerAddress,
                amount = kotlin.math.abs(delta),
                incoming = delta > 0,
                asset = SolanaTx.Asset.SPL,
                mint = k.mint,
                decimals = dec
            )
        }
        return out
    }

    private suspend fun rpcPost(payload: JsonObject, attempts: Int = Conf.RETRIES): String? =
        rpcPostRaw(json.encodeToString(JsonObject.serializer(), payload), attempts)

    private suspend fun rpcPostBatch(payload: List<JsonObject>, attempts: Int = Conf.RETRIES): String? =
        rpcPostRaw(json.encodeToString(ListSerializer(JsonObject.serializer()), payload), attempts)

    private suspend fun rpcPostRaw(body: String, attempts: Int): String? {
        repeat(attempts) { attempt ->
            try {
                val resp: HttpResponse = http.post(rpcUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                if (resp.status.value in 200..299) {
                    return resp.bodyAsText()
                }
                if (resp.status.value == 429 || resp.status.value >= 500) {
                    backoffDelay(attempt); return@repeat
                }
                return null
            } catch (e: HttpRequestTimeoutException) {
                backoffDelay(attempt)
            } catch (e: Exception) {
                backoffDelay(attempt)
            }
        }
        return null
    }

    private suspend fun backoffDelay(attempt: Int) {
        val base = Conf.RETRY_BASE_DELAY_MS shl attempt
        val jitter = Random.nextInt(0, 200)
        delay(min(Conf.RETRY_MAX_DELAY_MS, base + jitter).toLong())
    }

    private suspend fun enrichWithTokenMetadata(transactions: List<SolanaTx>): List<SolanaTx> {
        val service = tokenMetadataService ?: return transactions

        val mints: Set<String> = transactions.asSequence()
            .filter { it.asset == SolanaTx.Asset.SPL }
            .mapNotNull { it.mint }
            .map { it.lowercase() }
            .toSet()

        if (mints.isEmpty()) return transactions

        val metadataMapLower: Map<String, TokenMetadata> = try {
            val raw = service.batchGetTokenMetadata(mints.toList())
            raw.entries.associate { (mint, meta) -> mint.lowercase() to meta }
        } catch (t: Throwable) {
            logger.warn("enrichWithTokenMetadata: failed to fetch token metadata: ${t.message}")
            emptyMap()
        }

        if (metadataMapLower.isEmpty()) return transactions
        return transactions.map { tx ->
            if (tx.asset != SolanaTx.Asset.SPL) return@map tx
            val mintKey = tx.mint?.lowercase() ?: return@map tx
            val md = metadataMapLower[mintKey] ?: return@map tx

            tx.copy(
                tokenName = tx.tokenName ?: md.name,
                tokenSymbol = tx.tokenSymbol ?: md.symbol,
                tokenLogo = tx.tokenLogo ?: md.logoUri
            )
        }
    }
}
