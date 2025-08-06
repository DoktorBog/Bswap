package com.bswap.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bswap.app.models.WalletViewModel
import com.bswap.navigation.NavKey
import com.bswap.navigation.replaceAll
import com.bswap.ui.UiButton
import com.bswap.ui.actions.PrimaryActionBar
import com.bswap.ui.balance.BalanceCard
import com.bswap.ui.token.TokenChip
import com.bswap.ui.tx.TransactionRow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Main wallet home screen showing balance and recent transactions.
 */
@Composable
fun WalletHomeScreen(
    publicKey: String,
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val viewModel: WalletViewModel = koinViewModel(parameters = { parametersOf(publicKey) })

    val walletInfo by viewModel.walletInfo.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val history by viewModel.history.collectAsState()

    val solBalanceText = walletInfo?.lamports?.let { "${it / 1_000_000_000.0} SOL" } ?: "0 SOL"
    val tokens = walletInfo?.tokens ?: emptyList()
    Box(modifier = modifier.testTag("WalletHome")) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BalanceCard(solBalance = solBalanceText, tokensValue = "$0", isLoading = loading)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tokens) { token ->
                    TokenChip(
                        icon = Icons.Default.Star,
                        ticker = token.symbol ?: token.mint.take(4),
                        balance = token.amount ?: "0",
                        onClick = {}
                    )
                }
                items(history) { tx ->
                    TransactionRow(tx = tx)
                }
            }
            PrimaryActionBar(onSend = {}, onReceive = {}, onBuy = {}, modifier = Modifier.fillMaxWidth())
            UiButton(
                text = "Logout",
                onClick = { backStack.replaceAll(NavKey.Welcome) },
                modifier = Modifier.fillMaxWidth(),
                secondary = true
            )
        }
        FloatingActionButton(
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
    }
}
