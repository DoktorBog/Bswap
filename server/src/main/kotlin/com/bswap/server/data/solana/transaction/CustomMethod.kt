package com.bswap.server.data.solana.transaction

import com.bswap.server.RPC_URL
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpccore.JsonRpc20Request
import com.solana.rpccore.get
import foundation.metaplex.rpc.serializers.SolanaResponseSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.random.nextUInt

suspend fun getTokenAccountsByOwner(
    httpNetworkDriver: HttpNetworkDriver,
    publicKey: SolanaPublicKey,
): List<TokenAccount>? {
    val json = Json {
        ignoreUnknownKeys = true
    }
    // Create a list to hold JSON elements for RPC request parameters
    val params: MutableList<JsonElement> = mutableListOf()
    params.add(json.encodeToJsonElement(publicKey.base58()))
    params.add(
        buildJsonObject {
            put("programId", "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
        }
    )
    params.add(
        buildJsonObject {
            put("encoding", "jsonParsed")
        }
    )
    val rpcRequest = JsonRpc20Request(
        "getTokenAccountsByOwner",
        id = "${Random.nextUInt()}",
        params = JsonArray(content = params)
    )
    val rpcDriver = Rpc20Driver(RPC_URL, httpNetworkDriver)

    // Execute the RPC request and deserialize the response using the provided serializer
    return rpcDriver.get(
        rpcRequest,
        SolanaResponseSerializer(ListSerializer(TokenAccount.serializer()))
    ).getOrThrow()
}

@Serializable
data class TokenAccount(
    val account: Account,
    val pubkey: String
)

@Serializable
data class Account(
    val data: AccountData,
    val executable: Boolean,
    val lamports: Long,
    val owner: String,
    val space: Int
)

@Serializable
data class AccountData(
    val parsed: ParsedData,
    val program: String,
    val space: Int
)

@Serializable
data class ParsedData(
    val info: TokenInfo,
    val type: String
)

@Serializable
data class TokenInfo(
    val isNative: Boolean,
    val mint: String,
    val owner: String,
    val state: String,
    val tokenAmount: TokenAmount
)

@Serializable
data class TokenAmount(
    val amount: String,
    val decimals: Int,
    val uiAmount: Double?,
    @SerialName("uiAmountString")
    val uiAmountString: String
)
