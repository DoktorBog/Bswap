package com.bswap.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Platform navigator implementation.
 */
expect class Navigator(backStack: SnapshotStateList<NavKey>) {
    /** Push a [key] onto the back stack. */
    fun push(key: NavKey)

    /** Pop the last key off the stack if possible. */
    fun pop()
}

/**
 * Remember a [Navigator] instance bound to the given [backStack].
 */
@Composable
expect fun rememberNavigator(backStack: SnapshotStateList<NavKey>): Navigator
