package com.bswap.seed

import foundation.metaplex.solanaeddsa.Keypair
import foundation.metaplex.solanaeddsa.SolanaEddsa

object JitoService {
    fun generateKeypair(): Keypair = SolanaEddsa.generateKeypair()
}
