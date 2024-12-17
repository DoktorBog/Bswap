package com.bswap.server.data.solana.transaction

import foundation.metaplex.rpc.Account
import foundation.metaplex.rpc.BlockhashWithExpiryBlockHeight
import foundation.metaplex.rpc.RpcGetAccountInfoConfiguration
import foundation.metaplex.rpc.RpcGetBalanceConfiguration
import foundation.metaplex.rpc.RpcGetLatestBlockhashConfiguration
import foundation.metaplex.rpc.RpcGetMultipleAccountsConfiguration
import foundation.metaplex.rpc.RpcGetProgramAccountsConfiguration
import foundation.metaplex.rpc.RpcGetSlotConfiguration
import foundation.metaplex.rpc.RpcRequestAirdropConfiguration
import foundation.metaplex.rpc.RpcSendTransactionConfiguration
import foundation.metaplex.rpc.SerializedTransaction
import foundation.metaplex.rpc.TransactionSignature
import foundation.metaplex.solanapublickeys.PublicKey
import kotlinx.serialization.KSerializer


interface RpcInterface {

    suspend fun <T> getAccountInfo(
        publicKey: PublicKey,
        configuration: RpcGetAccountInfoConfiguration?,
        serializer: KSerializer<T>,
    ): Account<T>?

    suspend fun <T> getMultipleAccounts(
        publicKeys: List<PublicKey>,
        configuration: RpcGetMultipleAccountsConfiguration?,
        serializer: KSerializer<T>,
    ): List<Account<T>?>?

    suspend fun <T> getProgramAccounts(
        programId: PublicKey,
        configuration: RpcGetProgramAccountsConfiguration?,
        serializer: KSerializer<T>
    ): List<Account<T>?>?

    suspend fun getLatestBlockhash(
        configuration: RpcGetLatestBlockhashConfiguration?
    ): BlockhashWithExpiryBlockHeight

    suspend fun getSlot(
        configuration: RpcGetSlotConfiguration?
    ): ULong

    suspend fun getMinimumBalanceForRentExemption(
        usize: ULong
    ): ULong

    suspend fun requestAirdrop(
        configuration: RpcRequestAirdropConfiguration
    ): TransactionSignature

    suspend fun getBalance(
        publicKey: PublicKey,
        configuration: RpcGetBalanceConfiguration?
    ): Long

    suspend fun sendTransaction(
        transaction: SerializedTransaction,
        configuration: RpcSendTransactionConfiguration?
    ): TransactionSignature
}