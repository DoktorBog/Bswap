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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.push
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme
import com.bswap.app.copyToClipboard
import com.bswap.seed.SeedUtils
import com.bswap.data.seedStorage

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
    val seedWords = remember { SeedUtils.generateSeed() }
    LaunchedEffect(Unit) { seedStorage().saveSeed(seedWords) }
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(NavKey.GenerateSeed::class.simpleName!!),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(seedWords) { word ->
                SeedWordChip(text = word, onClick = {}, enabled = false)
            }
        }
        UiButton(
            text = "Copy",
            onClick = { copyToClipboard(seedWords.joinToString(" ")) },
            modifier = Modifier.fillMaxWidth()
        )
        UiButton(text = "Next", onClick = { backStack.push(NavKey.ConfirmSeed(seedWords)) }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview
@Composable
private fun GenerateSeedScreenPreview() {
    UiTheme {
        GenerateSeedScreen(rememberBackStack())
    }
}
