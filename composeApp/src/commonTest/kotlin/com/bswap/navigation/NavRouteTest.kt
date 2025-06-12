package com.bswap.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavRouteTest {
    @Test
    fun walletHomeRouteBuildsCorrectly() {
        val key = "abc"
        assertEquals("wallet_home/$key", NavRoute.walletHome(key))
    }

    @Test
    fun confirmSeedRouteRoundTrip() {
        val seed = listOf("one", "two", "three")
        val path = NavRoute.confirmSeed(seed)
        assertEquals(seed, NavRoute.parseSeed(path))
    }
}
