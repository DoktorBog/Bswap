package com.bswap.server.data.solana.rpc

import com.bswap.server.RPC_URL
import com.bswap.shared.model.TokenInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
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

    suspend fun getBalance(address: String): Long {
        val body = """{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getBalance\",\"params\":[\"$address\"]}"""
        lateinit var text: String
        val time = measureTimeMillis {
            text = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()
        }
        logger.debug("getBalance latency=${time}ms")
        return runCatching {
            json.parseToJsonElement(text).jsonObject["result"]!!.jsonObject["value"]!!.jsonPrimitive.long
        }.getOrDefault(0L)
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
}
