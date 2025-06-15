package com.bswap.ui.seed

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiTheme

/**
 * Single word of a seed phrase represented as a chip.
 *
 * @param word seed word text
 * @param focused whether chip is focused/dragged
 * @param onClick callback on chip press
 */
@Composable
fun SeedWordChip(
    word: String,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AssistChipDefaults.assistChipColors(
        containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        labelColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    )
    AssistChip(
        onClick = onClick,
        label = { Text(word) },
        modifier = modifier.testTag("SeedWordChip"),
        colors = colors
    )
}

@Preview
@Composable
private fun SeedWordChipPreview() {
    UiTheme {
        SeedWordChip(word = "solana", focused = false, onClick = {})
    }
}
