package com.bswap.shared.wallet

private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
private val ALPHABET_INDICES = IntArray(128) { -1 }.apply {
    for (i in ALPHABET.indices) this[ALPHABET[i].code] = i
}

fun ByteArray.encodeToBase58(): String {
    if (isEmpty()) return ""
    var zeros = 0
    while (zeros < size && this[zeros].toInt() == 0) zeros++
    val encoded = StringBuilder()
    var start = zeros
    val input = this.copyOf()
    while (start < input.size) {
        var carry = 0
        for (i in start until input.size) {
            val value = (input[i].toInt() and 0xFF)
            val num = carry * 256 + value
            input[i] = (num / 58).toByte()
            carry = num % 58
        }
        encoded.append(ALPHABET[carry])
        while (start < input.size && input[start].toInt() == 0) start++
    }
    repeat(zeros) { encoded.append(ALPHABET[0]) }
    return encoded.reverse().toString()
}

fun ByteArray.toBase58(): String = encodeToBase58()
fun String.decodeBase58(): ByteArray {
    if (isEmpty()) return byteArrayOf()
    
    // Validate input characters
    for (c in this) {
        if (c.code >= 128 || ALPHABET_INDICES[c.code] == -1) {
            throw IllegalArgumentException("Invalid character in Base58 string: '$c'")
        }
    }
    
    // Count leading zeros (character '1')
    var zeros = 0
    while (zeros < length && this[zeros] == ALPHABET[0]) zeros++
    
    // Process with BigInteger for accuracy
    var bi = java.math.BigInteger.ZERO
    val base = java.math.BigInteger.valueOf(58)
    
    for (i in zeros until length) {
        val charIndex = ALPHABET_INDICES[this[i].code]
        bi = bi.multiply(base).add(java.math.BigInteger.valueOf(charIndex.toLong()))
    }
    
    // Convert BigInteger back to byte array
    var bytes = bi.toByteArray()
    
    // Remove sign byte if it exists (BigInteger adds it for positive numbers > 127)
    if (bytes.size > 1 && bytes[0] == 0.toByte()) {
        bytes = bytes.copyOfRange(1, bytes.size)
    }
    
    // Handle leading zeros
    val result = ByteArray(zeros + bytes.size)
    // Fill leading zeros
    for (i in 0 until zeros) {
        result[i] = 0
    }
    // Copy the decoded bytes
    System.arraycopy(bytes, 0, result, zeros, bytes.size)
    
    return result
}
