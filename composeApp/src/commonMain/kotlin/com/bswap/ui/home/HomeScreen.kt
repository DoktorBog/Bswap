package com.bswap.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.bswap.app.Strings
import org.koin.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.lifecycle.viewModelScope
import com.bswap.app.api.WalletApi
import com.bswap.ui.TrianglesBackground
import com.bswap.ui.account.AccountHeader
import com.bswap.ui.token.TokenChip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
        containerColor = Color(0xFF1A1A1A),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White)
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Default.History, contentDescription = Strings.history, tint = Color.White)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF262626)) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.List, contentDescription = Strings.activity) },
                    label = { Text(Strings.activity) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Color.White, unselectedIconColor = Color.White, unselectedTextColor = Color.White, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {},
                    icon = { Icon(Icons.Default.Wallet, contentDescription = Strings.wallet) },
                    label = { Text(Strings.wallet) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFADADAD), selectedTextColor = Color(0xFFADADAD), unselectedIconColor = Color(0xFFADADAD), unselectedTextColor = Color(0xFFADADAD), indicatorColor = Color.Transparent)
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = state.portfolio, color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Row(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Text(Strings.buy)
                }
                FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) {
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
private fun AssetRow(asset: Asset) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF363636), shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(asset.symbol, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        Text(asset.balance, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}
