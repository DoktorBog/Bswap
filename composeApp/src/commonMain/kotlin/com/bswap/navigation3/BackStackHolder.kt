package com.bswap.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Remember a mutable navigation back stack starting with [start].
 */
@Composable
fun <T : NavKey> rememberNavBackStack(start: T): SnapshotStateList<T> =
    remember { mutableStateListOf(start) }
