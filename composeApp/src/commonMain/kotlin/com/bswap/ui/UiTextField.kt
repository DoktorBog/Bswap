package com.bswap.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.desktop.ui.tooling.preview.Preview

@Composable
fun UiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } }
    )
}

@Preview
@Composable
fun UiTextFieldPreview() {
    UiTheme {
        UiTextField(value = "", onValueChange = {}, label = "Label")
    }
}


