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
import androidx.compose.foundation.clickable
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import com.bswap.navigation.replaceAll
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.WalletTheme

/**
 * Settings screen listing wallet actions.
 */
@Composable
fun AccountSettingsScreen(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val onExportSeed = {}
    val onChangeLanguage = {}
    val onLogout = { backStack.replaceAll(NavKey.Welcome) }
    val items = listOf(
        "Export Seed" to onExportSeed,
        "Change Language" to onChangeLanguage,
        "Logout" to onLogout
    )
    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .testTag("AccountSettings"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { (title, callback) ->
            ListItem(
                headlineContent = { androidx.compose.material3.Text(title) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { callback() }
            )
        }
    }
}

@Preview
@Composable
private fun AccountSettingsScreenPreview() {
    WalletTheme {
        AccountSettingsScreen(rememberBackStack())
    }
}
