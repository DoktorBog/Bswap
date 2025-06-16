package com.bswap.seed

object SeedPhraseValidator {
    fun isValid(mnemonic: List<String>, selected: List<String>): Boolean {
        return mnemonic.joinToString(" ") == selected.joinToString(" ")
    }
}
