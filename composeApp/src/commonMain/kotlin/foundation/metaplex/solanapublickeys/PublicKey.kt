package foundation.metaplex.solanapublickeys

import foundation.metaplex.base58.encodeToBase58String

data class PublicKey(val bytes: ByteArray) {
    fun toBase58(): String = bytes.encodeToBase58String()
}
