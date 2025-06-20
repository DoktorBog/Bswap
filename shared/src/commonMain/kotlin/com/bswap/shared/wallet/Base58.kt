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
    val input = ByteArray(length) { this[it].code.toByte() }
    var zeros = 0
    while (zeros < input.size && input[zeros].toInt() == ALPHABET[0].code) zeros++
    val decoded = ByteArray(length)
    var outLen = 0
    var start = zeros
    while (start < input.size) {
        var carry = ALPHABET_INDICES[input[start].toInt()]
        var i = 0
        for (j in decoded.size - 1 downTo 0) {
            carry += (58 * (decoded[j].toInt() and 0xFF))
            decoded[j] = (carry % 256).toByte()
            carry /= 256
            i = j
        }
        outLen = decoded.size - i
        while (start < input.size && input[start].toInt() == ALPHABET[0].code) start++
    }
    val output = ByteArray(outLen + zeros)
    System.arraycopy(decoded, decoded.size - outLen, output, zeros, outLen)
    return output
}
