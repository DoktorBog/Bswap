package com.bswap.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedUtilsTest {
    @Test
    fun generateSeedHas12UniqueWords() {
        val seed = SeedUtils.generateSeed()
        assertEquals(12, seed.size)
        assertEquals(seed.toSet().size, seed.size)
    }

    @Test
    fun validateSeedMatches() {
        val seed = listOf("one","two","three")
        assertTrue(SeedUtils.validateSeed(seed, seed))
    }
}
