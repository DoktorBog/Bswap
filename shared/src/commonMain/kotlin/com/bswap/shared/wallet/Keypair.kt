package com.bswap.shared.wallet

data class Keypair(
    val publicKey: ByteArray,
    val secretKey: ByteArray
)
