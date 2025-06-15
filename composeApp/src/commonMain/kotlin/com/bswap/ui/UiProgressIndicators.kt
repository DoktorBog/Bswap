package com.bswap.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun UiCircularProgressIndicator(
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(modifier = modifier)
}

@Composable
fun UiLinearProgressIndicator(
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier)
    } else {
        LinearProgressIndicator(progress = progress, modifier = modifier)
    }
}

@Preview
@Composable
fun UiProgressPreview() {
    UiTheme {
        UiLinearProgressIndicator()
    }
}
