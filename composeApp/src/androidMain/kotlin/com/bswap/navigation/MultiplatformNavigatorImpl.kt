package com.bswap.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.accompanist.navigation.animation.rememberAnimatedNavController

/** Android backed implementation using [NavHostController]. */
actual class MultiplatformNavigator internal constructor(
    internal val navController: NavHostController
) {
    /** Currently visible route. */
    @Composable
    actual val currentRoute: String
        get() {
            val entry by navController.currentBackStackEntryAsState()
            return entry?.destination?.route ?: NavRoute.ONBOARD_WELCOME
        }

    actual fun navigate(route: String, popUpTo: String?, inclusive: Boolean) {
        navController.navigate(route) {
            popUpTo?.let { popUpTo(it) { this.inclusive = inclusive } }
        }
    }

    actual fun popBackStack() {
        navController.popBackStack()
    }
}

/** Remember an Android [MultiplatformNavigator] instance. */
@Composable
actual fun rememberMultiplatformNavigator(startRoute: String): MultiplatformNavigator {
    val controller = rememberAnimatedNavController()
    return remember(controller) { MultiplatformNavigator(controller) }
}
