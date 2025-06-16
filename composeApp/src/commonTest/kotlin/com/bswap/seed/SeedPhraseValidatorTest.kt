package com.bswap.seed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeedPhraseValidatorTest {
    @Test
    fun valid_phrase_matches() {
        val words = listOf("one", "two", "three")
        assertTrue(SeedPhraseValidator.isValid(words, words))
    }

    @Test
    fun invalid_phrase_fails() {
        val words = listOf("one", "two", "three")
        val entered = listOf("two", "one", "three")
        assertFalse(SeedPhraseValidator.isValid(words, entered))
    }
}
