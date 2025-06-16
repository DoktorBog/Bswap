package com.bswap.seed

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeedConfirmLogicTest {
    @Test
    fun selected_order_must_match_seed() {
        val seed = listOf("one","two","three")
        val selected = mutableListOf<String>()
        selected += "one"
        selected += "two"
        selected += "three"
        assertTrue(SeedUtils.validateSeed(seed, selected))
    }

    @Test
    fun invalid_order_fails_validation() {
        val seed = listOf("one","two","three")
        val selected = listOf("two","one","three")
        assertFalse(SeedUtils.validateSeed(seed, selected))
    }
}
