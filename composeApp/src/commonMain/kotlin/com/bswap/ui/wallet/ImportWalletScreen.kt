package com.bswap.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavRoute
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme
import com.bswap.ui.seed.SeedInputField

/**
 * Screen for manual wallet import via seed phrase.
 */
@Composable
fun ImportWalletScreen(
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (text, setText) = remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(NavRoute.IMPORT_WALLET),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SeedInputField(value = text, onValueChange = setText, modifier = Modifier.fillMaxWidth())
        UiButton(text = "Import", onClick = { onImport(text) }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "ImportWalletScreen", device = "id:pixel_4", showBackground = true)
@Composable
private fun ImportWalletScreenPreview() {
    UiTheme {
        ImportWalletScreen(onImport = {})
    }
}
