package com.bswap.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiTheme
import com.bswap.ui.actions.PrimaryActionBar
import com.bswap.ui.balance.BalanceCard
import com.bswap.ui.tx.SolanaTx
import com.bswap.ui.tx.TransactionRow

/**
 * Main wallet home screen showing balance and recent transactions.
 */
@Composable
fun WalletHomeScreen(
    publicKey: String,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {}
) {
    val txs = remember {
        listOf(
            SolanaTx("sig1", "Address1", 1.23, incoming = true),
            SolanaTx("sig2", "Address2", 0.5, incoming = false)
        )
    }
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .testTag("WalletHome"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BalanceCard(solBalance = "0 SOL", tokensValue = "$0", isLoading = false)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(txs) { tx ->
                TransactionRow(tx)
            }
        }
        PrimaryActionBar(onSend = {}, onReceive = {}, onBuy = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "WalletHomeScreen", device = "id:pixel_4", showBackground = true)
@Composable
private fun WalletHomeScreenPreview() {
    UiTheme {
        WalletHomeScreen(publicKey = "ABCD")
    }
}
