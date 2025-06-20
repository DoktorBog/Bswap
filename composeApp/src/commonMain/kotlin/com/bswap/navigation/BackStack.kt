package com.bswap.navigation

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable

@Composable
fun rememberBackStack(start: NavKey = NavKey.Welcome): SnapshotStateList<NavKey> = remember {
    mutableStateListOf(start)
}

fun SnapshotStateList<NavKey>.push(key: NavKey) = add(key)

fun SnapshotStateList<NavKey>.pop() { if (size > 1) removeLast() }

fun SnapshotStateList<NavKey>.replaceAll(vararg keys: NavKey) {
    clear(); addAll(keys)
}
