package foundation.metaplex.base58

private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
private val ALPHABET_INDICES = IntArray(128) { -1 }.apply {
    for (i in ALPHABET.indices) this[ALPHABET[i].code] = i
}

fun ByteArray.encodeToBase58String(): String {
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
