package com.bswap.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavRoute
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiTheme

/**
 * Settings screen listing wallet actions.
 */
@Composable
fun AccountSettingsScreen(
    onExportSeed: () -> Unit,
    onChangeLanguage: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        "Export Seed" to onExportSeed,
        "Change Language" to onChangeLanguage,
        "Logout" to onLogout
    )
    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .testTag(NavRoute.ACCOUNT_SETTINGS),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { (title, callback) ->
            ListItem(
                headlineContent = { androidx.compose.material3.Text(title) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                onClick = callback
            )
        }
    }
}

@Preview(name = "AccountSettingsScreen", device = "id:pixel_4", showBackground = true)
@Composable
private fun AccountSettingsScreenPreview() {
    UiTheme {
        AccountSettingsScreen(onExportSeed = {}, onChangeLanguage = {}, onLogout = {})
    }
}
