package com.bswap.ui.seed

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
 * @param selected whether chip is part of the selected phrase
 * @param onClick callback on chip press
 */
@Composable
fun SeedWordChip(
    word: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val border = AssistChipDefaults.assistChipBorder(
        borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    )
    AssistChip(
        onClick = onClick,
        label = { Text(word) },
        enabled = enabled,
        interactionSource = interactionSource,
        border = border,
        modifier = modifier
            .testTag("SeedWordChip")
            .indication(interactionSource, rememberRipple())
    )
}

@Preview
@Composable
private fun SeedWordChipPreview() {
    UiTheme {
        SeedWordChip(word = "solana", selected = false, onClick = {})
    }
}
