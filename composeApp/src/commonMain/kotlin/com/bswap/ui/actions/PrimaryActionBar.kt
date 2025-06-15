package com.bswap.ui.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme

/**
 * Action bar with Send, Receive and Buy buttons.
 */
@Composable
fun PrimaryActionBar(
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(8.dp)
            .testTag("PrimaryActionBar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UiButton(text = "Send", onClick = onSend, modifier = Modifier.weight(1f))
        UiButton(text = "Receive", onClick = onReceive, modifier = Modifier.weight(1f))
        UiButton(text = "Buy", onClick = onBuy, modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
private fun PrimaryActionBarPreview() {
    UiTheme {
        PrimaryActionBar(onSend = {}, onReceive = {}, onBuy = {})
    }
}
