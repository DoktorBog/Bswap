package com.bswap.server.data.solana.rpc

import com.bswap.server.RPC_URL
import com.bswap.shared.model.TokenInfo
import com.bswap.shared.model.SolanaTx
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import foundation.metaplex.rpc.RPC
import foundation.metaplex.rpc.Commitment
import foundation.metaplex.rpc.RpcGetBalanceConfiguration
import foundation.metaplex.rpc.networking.NetworkDriver
import foundation.metaplex.solanapublickeys.PublicKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.long
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

class SolanaRpcClient(
    private val client: HttpClient,
    private val rpcUrl: String = RPC_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(javaClass)
    private val rpc = RPC(rpcUrl, NetworkDriver(client))

    suspend fun getBalance(address: String): Long {
        val key = PublicKey(address)
        var balance: Long = 0
        val time = measureTimeMillis {
            balance = rpc.getBalance(
                key,
                RpcGetBalanceConfiguration(commitment = Commitment.finalized)
            )
        }
        logger.debug("getBalance latency=${time}ms")
        return balance
    }

    suspend fun getSPLTokens(owner: String): List<TokenInfo> {
        val req = """{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getTokenAccountsByOwner\",\"params\":[\"$owner\",{\"programId\":\"TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA\"},{\"encoding\":\"jsonParsed\"}]}"""
        lateinit var text: String
        val time = measureTimeMillis {
            text = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(req)
            }.bodyAsText()
        }
        logger.debug("getSPLTokens latency=${time}ms")
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val values = element.jsonObject["result"]?.jsonObject?.get("value")?.jsonArray ?: return emptyList()
        return values.mapNotNull { item ->
            runCatching {
                val info = item.jsonObject["account"]!!.jsonObject["data"]!!.jsonObject["parsed"]!!.jsonObject["info"]!!.jsonObject
                val mint = info["mint"]!!.jsonPrimitive.content
                val amount = info["tokenAmount"]!!.jsonObject["amount"]!!.jsonPrimitive.content
                val decimals = info["tokenAmount"]!!.jsonObject["decimals"]!!.jsonPrimitive.int
                TokenInfo(mint = mint, amount = amount, decimals = decimals)
            }.getOrNull()
        }
    }

    suspend fun getHistory(address: String, limit: Int = 10): List<SolanaTx> {
        val req =
            """{"jsonrpc":"2.0","id":1,"method":"getSignaturesForAddress","params":["$address",{"limit":$limit}]}"""
        val text = client.post(rpcUrl) {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.bodyAsText()
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val values = element.jsonObject["result"]?.jsonArray ?: return emptyList()
        val signatures = values.mapNotNull { it.jsonObject["signature"]?.jsonPrimitive?.content }
        val txs = mutableListOf<SolanaTx>()
        for (sig in signatures) {
            val txReq =
                """{"jsonrpc":"2.0","id":1,"method":"getTransaction","params":["$sig",{"encoding":"jsonParsed"}]}"""
            val txText = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(txReq)
            }.bodyAsText()
            val txEl = runCatching { json.parseToJsonElement(txText) }.getOrNull() ?: continue
            val meta = txEl.jsonObject["result"]?.jsonObject?.get("meta")?.jsonObject ?: continue
            val pre = meta["preBalances"]?.jsonArray?.getOrNull(0)?.jsonPrimitive?.long ?: continue
            val post = meta["postBalances"]?.jsonArray?.getOrNull(0)?.jsonPrimitive?.long ?: continue
            val change = post - pre
            val amount = change.toDouble() / 1_000_000_000.0
            val incoming = change > 0
            txs += SolanaTx(signature = sig, address = address, amount = amount, incoming = incoming)
        }
        return txs
    }
}
