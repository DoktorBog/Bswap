package com.bswap.ui.seed

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
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
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        labelColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    )
    FilterChip(
        selected = focused,
        onClick = onClick,
        interactionSource = interactionSource,
        label = { Text(word) },
        modifier = modifier
            .testTag("SeedWordChip")
            .indication(interactionSource, rememberRipple()),
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
