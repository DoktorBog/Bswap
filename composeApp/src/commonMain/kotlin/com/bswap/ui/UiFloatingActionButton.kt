package com.bswap.ui

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun UiFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}

@Preview
@Composable
fun UiFloatingActionButtonPreview() {
    UiTheme {
        UiFloatingActionButton(onClick = {}) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
    }
}
