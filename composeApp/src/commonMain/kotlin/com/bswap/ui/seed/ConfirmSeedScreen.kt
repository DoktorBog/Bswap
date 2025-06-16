package com.bswap.ui.seed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.replaceAll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme
import com.bswap.seed.SeedUtils
import com.bswap.seed.JitoService
import com.bswap.data.seedStorage
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.consume
import androidx.compose.runtime.mutableIntStateOf

/**
 * Screen for confirming seed phrase order via drag and drop.
 *
 * @param words shuffled seed words
 * @param backStack navigation back stack
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfirmSeedScreen(
    words: List<String>,
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val items = remember { mutableStateListOf(*words.shuffled().toTypedArray()) }
    var draggingIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(NavKey.ConfirmSeed::class.simpleName!!),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f)
        ) {
            items(items.size) { index ->
                val word = items[index]
                SeedWordChip(
                    word = word,
                    focused = draggingIndex == index,
                    onClick = {},
                    modifier = Modifier.pointerInput(index) {
                        detectDragGestures(onDragStart = { draggingIndex = index },
                            onDragEnd = { draggingIndex = -1 },
                            onDragCancel = { draggingIndex = -1 }) { change, drag ->
                            change.consume()
                            val rowSize = 40f
                            val newIndex = (index + drag.y / rowSize).toInt().coerceIn(0, items.lastIndex)
                            if (newIndex != index) {
                                items.removeAt(index)
                                items.add(newIndex, word)
                            }
                        }
                    }
                )
            }
        }
        val scope = rememberCoroutineScope()
        UiButton(
            text = "Confirm",
            onClick = {
                val keypair = JitoService.generateKeypair()
                scope.launch { seedStorage().savePublicKey(keypair.publicKey.base58()) }
                backStack.replaceAll(NavKey.WalletHome(keypair.publicKey.base58()))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items == words
        )
    }
}

@Preview
@Composable
private fun ConfirmSeedScreenPreview() {
    UiTheme {
        ConfirmSeedScreen(words = List(12) { "word${it+1}" }, backStack = rememberBackStack())
    }
}
