package com.bswap.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.bswap.app.Strings
import com.bswap.ui.TrianglesBackground
import com.bswap.ui.account.AccountHeader
import com.bswap.ui.balance.BalanceCard
import com.bswap.ui.token.TokenChip
import org.koin.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Simple fake asset model */
data class Asset(
    val symbol: String,
    val balance: String,
)

/** UI state for HomeScreen */
data class HomeUiState(
    val portfolio: String = "$0",
    val assets: List<Asset> = emptyList(),
)

class HomeViewModel(
    private val api: WalletApi,
    private val address: String,
) : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    fun refresh() = viewModelScope.launch {
        runCatching { api.walletInfo(address) }
            .onSuccess { info ->
                val assets = info.tokens.map { Asset(it.symbol ?: it.mint, it.amount ?: "0") }
                _uiState.value = HomeUiState(portfolio = "${'$'}${info.lamports / 1_000_000_000.0}", assets = assets)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(publicKey: String, onSettings: () -> Unit, onHistory: () -> Unit, modifier: Modifier = Modifier) {
    val vm: HomeViewModel = koinViewModel(parameters = { parametersOf(publicKey) })
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(Strings.home) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = Strings.settings)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.ArrowUpward, contentDescription = Strings.home) },
                    label = { Text(Strings.home) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onHistory,
                    icon = { Icon(Icons.Default.History, contentDescription = Strings.history) },
                    label = { Text(Strings.history) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettings,
                    icon = { Icon(Icons.Default.Settings, contentDescription = Strings.settings) },
                    label = { Text(Strings.settings) }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            TrianglesBackground(modifier = Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AccountHeader(publicKey = publicKey, onCopy = {})
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(text = state.portfolio, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.assets) { asset ->
                        TokenChip(
                            icon = Icons.Default.Star,
                            ticker = asset.symbol,
                            balance = asset.balance,
                            onClick = {}
                        )
                    }
                }
                ActionRow(onHistory = onHistory)
            }
        }
    }
}

@Composable
fun ActionRow(onHistory: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ShoppingCart, contentDescription = Strings.buy)
            Text(Strings.buy)
        }
        FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ArrowUpward, contentDescription = Strings.send)
            Text(Strings.send)
        }
        FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ArrowDownward, contentDescription = Strings.receive)
            Text(Strings.receive)
        }
        FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.SwapHoriz, contentDescription = Strings.swap)
            Text(Strings.swap)
        }
        FilledTonalButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.History, contentDescription = Strings.history)
            Text(Strings.history)
        }
    }
}
