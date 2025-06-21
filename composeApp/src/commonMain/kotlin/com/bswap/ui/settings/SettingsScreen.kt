package com.bswap.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bswap.app.Strings
import com.bswap.ui.account.AccountHeader
import com.bswap.ui.UiSwitch
import com.bswap.app.openLink
import com.bswap.app.copyToClipboard
import com.bswap.ui.UiButton
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(publicKey: String, onBack: () -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val darkTheme = remember { mutableStateOf(false) }
    val languages = listOf("en", "uk", "ru")
    val expanded = remember { mutableStateOf(false) }
    val language = remember { mutableStateOf(languages.first()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = Strings.back)
                    }
                },
                title = { Text(Strings.settings) }
            )
        }
    ) { inner ->
        Column(
            modifier = modifier
                .padding(inner)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccountHeader(publicKey = publicKey, onCopy = {})
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(Strings.dark_theme)
                UiSwitch(checked = darkTheme.value, onCheckedChange = { darkTheme.value = it })
            }
            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { expanded.value = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(Strings.language)
                Text(language.value)
            }
            DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                languages.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        language.value = it
                        expanded.value = false
                    })
                }
            }
            ListItem(
                headlineContent = { Text(Strings.privacy_policy) },
                modifier = Modifier.clickable { openLink("https://example.com/privacy") }
            )
            FilledTonalButton(onClick = onLogout, modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)) {
                Text(Strings.logout)
            }
        }
    }
}

