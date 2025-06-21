package com.bswap.ui

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun UiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)
    val m = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    if (secondary) {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = shape, modifier = m) {
            Text(text)
        }
    } else {
        FilledTonalButton(onClick = onClick, enabled = enabled, shape = shape, modifier = m) {
            Text(text)
        }
    }
}

@Preview
@Composable
fun UiButtonPreview() {
    WalletTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UiButton(text = "Primary", onClick = {})
            UiButton(text = "Secondary", onClick = {}, secondary = true)
        }
    }
}

