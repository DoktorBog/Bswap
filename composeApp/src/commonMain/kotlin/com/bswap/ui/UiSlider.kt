package com.bswap.ui

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.desktop.ui.tooling.preview.Preview

@Composable
fun UiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange
    )
}

@Preview
@Composable
fun UiSliderPreview() {
    UiTheme {
        UiSlider(value = 0.5f, onValueChange = {})
    }
}

