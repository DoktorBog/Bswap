package com.bswap.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Desktop/JS implementation storing navigation stack in memory. */
actual class MultiplatformNavigator actual constructor(startRoute: String) {
    private val stack = mutableStateListOf(startRoute)
    private var routeState by mutableStateOf(startRoute)

    actual val currentRoute: String
        get() = routeState

    actual fun navigate(route: String, popUpTo: String?, inclusive: Boolean) {
        popUpTo?.let { dest ->
            val index = stack.indexOfLast { it == dest }
            if (index >= 0) {
                val removeIndex = if (inclusive) index else index + 1
                for (i in stack.lastIndex downTo removeIndex) stack.removeAt(i)
            }
        }
        stack.add(route)
        routeState = route
    }

    actual fun popBackStack() {
        if (stack.size > 1) {
            stack.removeLast()
            routeState = stack.last()
        }
    }
}

/** Remember a [MultiplatformNavigator] for Desktop/JS. */
@Composable
actual fun rememberMultiplatformNavigator(startRoute: String): MultiplatformNavigator =
    remember { MultiplatformNavigator(startRoute) }
