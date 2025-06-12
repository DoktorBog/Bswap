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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme

/**
 * Screen displaying generated seed phrase.
 *
 * @param seedWords ordered seed words
 * @param onCopy invoked when Copy button pressed
 * @param onNext invoked when Next button pressed
 */
@Composable
fun GenerateSeedScreen(
    seedWords: List<String>,
    onCopy: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag("GenerateSeedScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(seedWords) { word ->
                SeedWordChip(word = word, focused = false, onClick = {})
            }
        }
        UiButton(text = "Copy", onClick = onCopy, modifier = Modifier.fillMaxWidth())
        UiButton(text = "Next", onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "GenerateSeedScreen", device = "id:pixel_4", showBackground = true)
@Composable
private fun GenerateSeedScreenPreview() {
    UiTheme {
        GenerateSeedScreen(seedWords = List(12) { "word${it+1}" }, onCopy = {}, onNext = {})
    }
}
