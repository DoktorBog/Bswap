package com.bswap.ui.tx

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiTheme

/** Data class representing a Solana transaction. */
data class SolanaTx(
    val signature: String,
    val address: String,
    val amount: Double,
    val incoming: Boolean
)

/**
 * Row displaying brief information about a transaction.
 */
@Composable
fun TransactionRow(tx: SolanaTx, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("TransactionRow"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = if (tx.incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward
        val amountColor = if (tx.incoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Icon(icon, contentDescription = null, tint = amountColor)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = tx.address, style = MaterialTheme.typography.bodyMedium)
            Text(text = tx.signature.take(8) + "…", style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = (if (tx.incoming) "+" else "-") + tx.amount,
            color = amountColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(name = "TransactionRow", device = "id:pixel_4", showBackground = true)
@Composable
private fun TransactionRowPreview() {
    UiTheme {
        TransactionRow(
            tx = SolanaTx("abcdef", "DestinationAddress", 1.23, incoming = true)
        )
    }
}
