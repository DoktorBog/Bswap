package com.bswap.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList

@Composable
fun <T> NavDisplay(
    backStack: SnapshotStateList<T>,
    enter: (AnimatedContentTransitionScope<T>.() -> EnterTransition)? = null,
    exit: (AnimatedContentTransitionScope<T>.() -> ExitTransition)? = null,
    content: @Composable (T) -> Unit
) {
    val current = backStack.last()
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val ent = enter?.invoke(this) ?: EnterTransition.None
            val ex = exit?.invoke(this) ?: ExitTransition.None
            ent togetherWith ex
        }
    ) { target ->
        content(target)
    }
}
