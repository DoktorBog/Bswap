package com.bswap.ui.balance

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.ui.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.WalletTheme

/**
 * Displays overall SOL balance and total token value.
 *
 * @param solBalance formatted SOL amount
 * @param tokensValue formatted value in USDC
 * @param isLoading when true shows a loading state with progress indicator
 */
@Composable
fun BalanceCard(
    solBalance: String,
    tokensValue: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("BalanceCard")
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = solBalance,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tokensValue,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.6f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Preview
@Composable
private fun BalanceCardPreview() {
    WalletTheme {
        BalanceCard(solBalance = "0 SOL", tokensValue = "$0", isLoading = false)
    }
}
