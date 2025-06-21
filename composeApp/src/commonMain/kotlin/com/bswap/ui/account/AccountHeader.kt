package com.bswap.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.WalletTheme
import com.bswap.app.copyToClipboard

/**
 * Header displaying account avatar initials and short public key.
 *
 * @param publicKey full public key
 * @param onCopy copy callback
 */
@Composable
fun AccountHeader(
    publicKey: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .padding(16.dp)
            .testTag("AccountHeader")
    ) {
        val initials = publicKey.take(2).uppercase()
        Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = publicKey.take(4) + "..." + publicKey.takeLast(4),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        UiButton(
            text = "Copy",
            onClick = {
                copyToClipboard(publicKey)
                onCopy()
            },
            modifier = Modifier.size(60.dp)
        )
    }
}