package com.bswap.ui.seed

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.bswap.ui.UiTheme

/**
 * Text field for entering a seed phrase with validation and indicator.
 *
 * @param value current text value
 * @param onValueChange callback with updated text
 */
@Composable
fun SeedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lower = value.lowercase()
    val words = lower.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val valid = words.size == 12 || words.size == 24
    Crossfade(targetState = valid, label = "seed") { isValid ->
        OutlinedTextField(
            value = lower,
            onValueChange = { onValueChange(it.lowercase()) },
            modifier = modifier
                .testTag("SeedInputField"),
            label = { Text("Seed Phrase") },
            singleLine = false,
            maxLines = 4,
            trailingIcon = {
                Icon(
                    imageVector = if (isValid) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null
                )
            },
            isError = !isValid,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
        )
    }
}

@Preview(name = "SeedInput", device = "id:pixel_4", showBackground = true)
@Composable
private fun SeedInputFieldPreview() {
    var text by remember { mutableStateOf("word1 word2 word3") }
    UiTheme {
        SeedInputField(value = text, onValueChange = { text = it })
    }
}
