package foundation.metaplex.solanaeddsa

import foundation.metaplex.solanapublickeys.PublicKey
import java.security.SecureRandom

object SolanaEddsa {
    private val random = SecureRandom()

    fun generateKeypair(): Keypair {
        val secret = ByteArray(32)
        random.nextBytes(secret)
        val pub = PublicKey(secret.copyOf())
        return Keypair(pub, secret)
    }

    fun createKeypairFromSeed(seed: ByteArray): Keypair {
        val secret = seed.copyOf(32)
        val pub = PublicKey(secret.copyOf())
        return Keypair(pub, secret)
    }

    fun sign(message: ByteArray, keypair: Keypair): ByteArray {
        // Dummy implementation
        val result = ByteArray(64)
        random.nextBytes(result)
        return result
    }
}
