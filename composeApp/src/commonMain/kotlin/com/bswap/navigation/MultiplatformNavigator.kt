package com.bswap.navigation

import androidx.compose.runtime.Composable

/**
 * Basic cross-platform navigator used on Desktop and Web.
 */
expect class MultiplatformNavigator(startRoute: String = NavRoute.ONBOARD_WELCOME) {
    /** Current active route. */
    val currentRoute: String

    /** Navigate to the given [route]. */
    fun navigate(route: String, popUpTo: String? = null, inclusive: Boolean = false)

    /** Pop the last destination if possible. */
    fun popBackStack()
}

/** Remember a [MultiplatformNavigator] instance. */
@Composable
expect fun rememberMultiplatformNavigator(startRoute: String = NavRoute.ONBOARD_WELCOME): MultiplatformNavigator
