package com.bswap.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bswap.app.Strings
import com.bswap.app.api.WalletApi
import org.koin.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


data class Asset(
    val symbol: String,
    val balance: String,
)

data class HomeUiState(
    val portfolio: String = "\$0",
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
                _uiState.value = HomeUiState(portfolio = "\$${info.lamports / 1_000_000_000.0}", assets = assets)
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
            SmallTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Menu, contentDescription = Strings.settings)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = onHistory,
                    icon = { Icon(Icons.Default.List, contentDescription = Strings.activity) },
                    label = { Text(Strings.activity) }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = Strings.wallet) },
                    label = { Text(Strings.wallet) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.portfolio,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(Strings.buy)
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(Strings.receive)
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.assets) { asset ->
                    AssetRow(asset)
                }
            }
        }
    }
}

@Composable
private fun AssetRow(asset: Asset, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            Text(asset.symbol, style = MaterialTheme.typography.bodyLarge)
        }
        Text(asset.balance, style = MaterialTheme.typography.bodyLarge)
    }
}

