package com.bswap.shared.wallet

actual object SeedToWalletConverter {
    actual fun fromSeedPhrase(
        seedPhrase: List<String>,
        accountIndex: Int,
        passphrase: String
    ): WalletConfig {
        val mnemonic = seedPhrase.joinToString(" ")

        // Try to use Trust Wallet Core (wallet-core) if it's available on the classpath
        try {
            val hdClass = Class.forName("wallet.core.jni.HDWallet")
            val coinClass = Class.forName("wallet.core.jni.CoinType")
            val ctor = hdClass.getConstructor(String::class.java, String::class.java)
            val hd = ctor.newInstance(mnemonic, passphrase)
            val solField = coinClass.getField("SOLANA").get(null)
            val getKey = hdClass.getMethod("getKey", coinClass, String::class.java)
            val path = "m/44'/501'/${accountIndex}'/0'"
            val privateKeyObj = getKey.invoke(hd, solField, path)
            val getPublic = privateKeyObj.javaClass.getMethod("getPublicKeyEd25519")
            val publicObj = getPublic.invoke(privateKeyObj)
            val dataMethod = publicObj.javaClass.getMethod("data")
            val pubBytes = dataMethod.invoke(publicObj) as ByteArray
            val privData = privateKeyObj.javaClass.getMethod("data")
            val privBytes = privData.invoke(privateKeyObj) as ByteArray

            return WalletConfig(
                publicKey = base58Encode(pubBytes),
                privateKey = base58Encode(privBytes)
            )
        } catch (t: Throwable) {
            // Fall through to pure-JVM derivation
        }

        // Pure-JVM fallback: derive seed from mnemonic (BIP39 -> seed) and SLIP-0010 (Ed25519)
        try {
            val seed = mnemonicToSeed(mnemonic, passphrase)
            val hardened = (1 shl 31)
            val path = listOf(44 or hardened, 501 or hardened, accountIndex or hardened, 0 or hardened)

            var (k, c) = slip10MasterKeyFromSeed(seed)
            for (index in path) {
                val derived = slip10DeriveChild(k, c, index)
                k = derived.first
                c = derived.second
            }

            val privKey = k
            val priv = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privKey, 0)
            val pub = priv.generatePublicKey().encoded
            val secretKey = privKey + pub

            return WalletConfig(
                publicKey = base58Encode(pub),
                privateKey = base58Encode(secretKey)
            )
        } catch (e: Throwable) {
            return WalletConfig()
        }
    }

    actual fun fromSeedPhraseString(
        seedPhrase: String,
        accountIndex: Int,
        passphrase: String
    ): WalletConfig {
        return fromSeedPhrase(seedPhrase.split(" "), accountIndex, passphrase)
    }

    private fun base58Encode(input: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var bi = java.math.BigInteger(1, input)
        val sb = StringBuilder()
        val base = java.math.BigInteger.valueOf(58)
        while (bi.compareTo(java.math.BigInteger.ZERO) > 0) {
            val divRem = bi.divideAndRemainder(base)
            bi = divRem[0]
            val rem = divRem[1].intValueExact()
            sb.append(alphabet[rem])
        }
        for (b in input) {
            if (b.toInt() == 0) sb.append(alphabet[0]) else break
        }
        return sb.reverse().toString()
    }

    private fun mnemonicToSeed(mnemonic: String, passphrase: String): ByteArray {
        val password = mnemonic.toCharArray()
        val salt = ("mnemonic" + passphrase).toByteArray(Charsets.UTF_8)
        val spec = javax.crypto.spec.PBEKeySpec(password, salt, 2048, 512)
        val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return skf.generateSecret(spec).encoded
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA512")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    private fun slip10MasterKeyFromSeed(seed: ByteArray): Pair<ByteArray, ByteArray> {
        val I = hmacSha512("ed25519 seed".toByteArray(Charsets.UTF_8), seed)
        val IL = I.copyOfRange(0, 32)
        val IR = I.copyOfRange(32, 64)
        return IL to IR
    }

    private fun slip10DeriveChild(kPar: ByteArray, cPar: ByteArray, index: Int): Pair<ByteArray, ByteArray> {
        val data = ByteArray(1 + kPar.size + 4)
        data[0] = 0x00
        System.arraycopy(kPar, 0, data, 1, kPar.size)
        val idx = index
        data[1 + kPar.size] = ((idx ushr 24) and 0xFF).toByte()
        data[1 + kPar.size + 1] = ((idx ushr 16) and 0xFF).toByte()
        data[1 + kPar.size + 2] = ((idx ushr 8) and 0xFF).toByte()
        data[1 + kPar.size + 3] = (idx and 0xFF).toByte()
        val I = hmacSha512(cPar, data)
        val IL = I.copyOfRange(0, 32)
        val IR = I.copyOfRange(32, 64)
        return IL to IR
    }
}
