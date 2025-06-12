package com.bswap.ui.token

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiTheme

/**
 * Compact token representation with icon, ticker and balance.
 */
@Composable
fun TokenChip(
    icon: ImageVector,
    ticker: String,
    balance: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = modifier.testTag("TokenChip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(ticker, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            Text(balance, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview(name = "TokenChip", device = "id:pixel_4", showBackground = true)
@Composable
private fun TokenChipPreview() {
    UiTheme {
        TokenChip(icon = Icons.Default.Star, ticker = "SOL", balance = "1.0", onClick = {})
    }
}
