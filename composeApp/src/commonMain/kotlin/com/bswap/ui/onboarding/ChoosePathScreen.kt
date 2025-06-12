package com.bswap.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme

/**
 * Screen allowing the user to choose between creating a new wallet or importing one.
 */
@Composable
fun ChoosePathScreen(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .testTag("ChoosePath"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UiButton(text = "Create", onClick = onCreate, modifier = Modifier.fillMaxWidth())
        UiButton(text = "Import", onClick = onImport, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "ChoosePathScreen", device = "id:pixel_4", showBackground = true)
@Composable
private fun ChoosePathScreenPreview() {
    UiTheme {
        ChoosePathScreen(onCreate = {}, onImport = {})
    }
}
