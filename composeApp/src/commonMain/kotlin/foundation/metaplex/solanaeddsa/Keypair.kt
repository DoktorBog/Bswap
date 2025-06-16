package foundation.metaplex.solanaeddsa

import foundation.metaplex.solanapublickeys.PublicKey

data class Keypair(
    val publicKey: PublicKey,
    val secretKey: ByteArray
)
