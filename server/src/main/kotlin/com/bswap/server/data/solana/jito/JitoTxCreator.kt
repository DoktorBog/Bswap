package com.bswap.server.data.solana.jito

import com.bswap.server.data.solana.transaction.createSolTransaction
import com.bswap.server.rpc
import foundation.metaplex.base58.encodeToBase58String
import foundation.metaplex.solanapublickeys.PublicKey

object JitoTxCreator {
    suspend fun createTipTx(
        lamports: Long,
        toPubkey: String,
    ): String {
        return createSolTransaction(
            rpc = rpc,
            lamports = lamports,
            toPublicKey = PublicKey(toPubkey),
        ).serialize().encodeToBase58String()
    }
}