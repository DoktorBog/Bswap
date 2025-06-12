package com.bswap.navigation3

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

actual class Navigator internal constructor(private val backStack: SnapshotStateList<NavKey>) {
    actual fun push(key: NavKey) {
        backStack.add(key)
    }

    actual fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}

@Composable
actual fun rememberNavigator(backStack: SnapshotStateList<NavKey>): Navigator {
    val navigator = remember(backStack) { Navigator(backStack) }
    BackHandler(enabled = backStack.size > 1) { navigator.pop() }
    return navigator
}
