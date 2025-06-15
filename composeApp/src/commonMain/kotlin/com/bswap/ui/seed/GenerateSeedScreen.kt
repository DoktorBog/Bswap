package com.bswap.ui.seed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.push
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import com.bswap.ui.PrimaryButton
import com.bswap.ui.UiTheme

/**
 * Screen displaying generated seed phrase.
 *
 * @param backStack navigation back stack
 */
@Composable
fun GenerateSeedScreen(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val seedWords = remember { List(12) { "word${'$'}{it + 1}" } }
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(NavKey.GenerateSeed::class.simpleName!!),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(seedWords) { word ->
                SeedWordChip(word = word, focused = false, onClick = {})
            }
        }
        TextButton(onClick = {}) { androidx.compose.material3.Text("Copy") }
        PrimaryButton(text = "Next", onClick = { backStack.push(NavKey.ConfirmSeed(seedWords)) }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "GenerateSeedScreen", device = "id:pixel_4", showBackground = true)
@Composable
private fun GenerateSeedScreenPreview() {
    UiTheme {
        GenerateSeedScreen(rememberBackStack())
    }
}
