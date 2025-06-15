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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiTheme

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
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Crossfade(targetState = isLoading, label = "balance") { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = solBalance, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = tokensValue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun BalanceCardPreview() {
    UiTheme {
        BalanceCard(solBalance = "0 SOL", tokensValue = "$0", isLoading = false)
    }
}
