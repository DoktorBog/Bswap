package com.bswap.ui.seed

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import com.bswap.ui.UiTheme

/**
 * Single word of a seed phrase represented as a chip.
 *
 * @param text chip text
 * @param onClick callback when chip is pressed
 */
@Composable
fun SeedWordChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, modifier = Modifier.padding(12.dp)) },
        enabled = enabled,
        interactionSource = interactionSource,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp),
        colors = AssistChipDefaults.assistChipColors(containerColor = containerColor),
        modifier = modifier
            .testTag("SeedWordChip")
            .indication(interactionSource, rememberRipple())
    )
}

@Preview
@Composable
private fun SeedWordChipPreview() {
    UiTheme {
        SeedWordChip(text = "1. solana", onClick = {})
    }
}
