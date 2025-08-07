package com.bswap.ui.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.bswap.ui.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.WalletTheme
import com.bswap.shared.model.SolanaTx

/**
 * Card displaying detailed information about a transaction.
 */
@Composable
fun TransactionRow(tx: SolanaTx, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("TransactionRow"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction icon with background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (tx.incoming) 
                            MaterialTheme.colorScheme.primaryContainer
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    tx.incoming -> Icons.Default.ArrowDownward
                    else -> Icons.Default.ArrowUpward
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (tx.incoming) 
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else 
                        MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Transaction details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (tx.incoming) "Получено" else "Отправлено",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "От: ${tx.address.take(8)}...${tx.address.takeLast(4)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "TXN: ${tx.signature.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (tx.incoming) "+" else "-"}${String.format("%.4f", tx.amount)} SOL",
                    color = if (tx.incoming) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "~$${String.format("%.2f", tx.amount * 200)}", // Mock SOL price
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview
@Composable
private fun TransactionRowPreview() {
    WalletTheme {
        TransactionRow(
            tx = SolanaTx("abcdef", "DestinationAddress", 1.23, incoming = true)
        )
    }
}
