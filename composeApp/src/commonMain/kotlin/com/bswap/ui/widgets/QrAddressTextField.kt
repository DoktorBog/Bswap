package com.bswap.ui.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.bswap.ui.UiTheme

private val base58Regex = Regex("^[1-9A-HJ-NP-Za-km-z]+")

/**
 * Text field for Solana addresses with QR scan action.
 */
@Composable
fun QrAddressTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onQrClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val isError = value.isNotEmpty() && !base58Regex.matches(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag("QrAddressTextField"),
        label = label?.let { { Text(it) } },
        isError = isError,
        trailingIcon = {
            IconButton(onClick = onQrClick) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            }
        }
    )
}

@Preview(name = "QrAddressTextField", device = "id:pixel_4", showBackground = true)
@Composable
private fun QrAddressTextFieldPreview() {
    val (text, setText) = remember { mutableStateOf("") }
    UiTheme {
        QrAddressTextField(value = text, onValueChange = setText, onQrClick = {}, label = "Address")
    }
}
