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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.replaceAll
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme

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
            items(words) { word ->
                SeedWordChip(
                    word = word,
                    focused = false,
                    onClick = {}
                )
            }
        }
        UiButton(
            text = "Confirm",
            onClick = { backStack.replaceAll(NavKey.WalletHome("pubKey")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = false
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
