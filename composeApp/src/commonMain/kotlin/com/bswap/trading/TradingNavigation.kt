package com.bswap.trading

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bswap.shared.trading.TradingApiClient
import com.bswap.shared.trading.formatPrice
import com.bswap.shared.trading.formatPercent
import io.ktor.client.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingApp(
    httpClient: HttpClient,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val apiClient = remember { TradingApiClient(httpClient) }
    
    // Використовуємо тільки dashboard без навігації
    TradingDashboard(
        apiClient = apiClient,
        onBackClick = onBackClick,
        modifier = modifier.fillMaxSize()
    )
}

enum class TradingDestination(
    val label: String,
    val icon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    POSITIONS("Positions", Icons.Default.TrendingUp),
    ORDERS("Orders", Icons.Default.Receipt),
    MARKETS("Markets", Icons.Default.ShowChart),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun PositionsScreen(
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var tradingState by remember { mutableStateOf(com.bswap.shared.trading.TradingState()) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        apiClient.tradingDataFlow().collect { state ->
            tradingState = state
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Positions Management",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Summary Card
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Portfolio Summary",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Active Positions")
                        Text(
                            text = tradingState.positions.size.toString(),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Column {
                        Text("Total PnL")
                        val totalPnL = tradingState.positions.sumOf { it.pnl }
                        Text(
                            text = totalPnL.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (totalPnL >= 0) androidx.compose.ui.graphics.Color(0xFF4CAF50) else androidx.compose.ui.graphics.Color(0xFFF44336)
                        )
                    }
                    Column {
                        Text("Total Margin")
                        Text(
                            text = tradingState.positions.sumOf { it.margin }.toString(),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }
        }
        
        // Positions List
        PositionsSection(
            positions = tradingState.positions,
            onClosePosition = { symbol ->
                scope.launch {
                    apiClient.closePosition(com.bswap.shared.trading.ClosePositionRequest(symbol))
                }
            },
            onOpenPositionDialog = { /* Handle open position dialog */ },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun OrdersScreen(
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var tradingState by remember { mutableStateOf(com.bswap.shared.trading.TradingState()) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        apiClient.tradingDataFlow().collect { state ->
            tradingState = state
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Orders Management",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Quick Actions
        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            apiClient.cancelAllOrders()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel All Orders")
                }
                
                OutlinedButton(
                    onClick = { /* Refresh orders */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
            }
        }
        
        // Orders List
        OrdersSection(
            orders = tradingState.orders,
            onCancelOrder = { orderId ->
                scope.launch {
                    apiClient.cancelOrder(orderId)
                }
            },
            onCreateOrderDialog = { /* Handle create order dialog */ },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun MarketsScreen(
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var tradingState by remember { mutableStateOf(com.bswap.shared.trading.TradingState()) }
    var selectedMarket by remember { mutableStateOf<com.bswap.shared.trading.MarketData?>(null) }
    
    LaunchedEffect(Unit) {
        apiClient.tradingDataFlow().collect { state ->
            tradingState = state
        }
    }
    
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Markets List
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Markets",
                style = MaterialTheme.typography.headlineMedium
            )
            
            MarketsSection(
                markets = tradingState.markets,
                onSelectSymbol = { symbol ->
                    selectedMarket = tradingState.markets.find { it.symbol == symbol }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Market Details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Market Details",
                style = MaterialTheme.typography.headlineMedium
            )
            
            selectedMarket?.let { market ->
                MarketDetailsCard(market = market)
            } ?: run {
                Card(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = "Select a market to view details",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarketDetailsCard(
    market: com.bswap.shared.trading.MarketData,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = market.symbol,
                style = MaterialTheme.typography.headlineSmall
            )
            
            Divider()
            
            MarketDetailRow("Price", "$${market.price.formatPrice()}")
            MarketDetailRow("24h Change", market.change24h.formatPercent())
            MarketDetailRow("24h Volume", market.volume24h.formatPrice())
            MarketDetailRow("Bid", "$${market.bid.formatPrice()}")
            MarketDetailRow("Ask", "$${market.ask.formatPrice()}")
            MarketDetailRow("Spread", "${market.spreadPercent.formatPercent()}")
            
            market.fundingRate?.let { funding ->
                Divider()
                Text(
                    text = "Perpetual Data",
                    style = MaterialTheme.typography.titleMedium
                )
                MarketDetailRow("Funding Rate", funding.formatPercent())
            }
            
            market.markPrice?.let { mark ->
                MarketDetailRow("Mark Price", "$${mark.formatPrice()}")
            }
            
            market.indexPrice?.let { index ->
                MarketDetailRow("Index Price", "$${index.formatPrice()}")
            }
            
            market.openInterest?.let { oi ->
                MarketDetailRow("Open Interest", oi.formatPrice())
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Open buy dialog */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Buy")
                }
                
                OutlinedButton(
                    onClick = { /* Open sell dialog */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sell")
                }
            }
        }
    }
}

@Composable
fun MarketDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun MarketsSection(
    markets: List<com.bswap.shared.trading.MarketData>,
    onSelectSymbol: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Available Markets",
                style = MaterialTheme.typography.titleMedium
            )
            
            if (markets.isEmpty()) {
                Text(
                    text = "No markets available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                markets.forEach { market ->
                    MarketRow(
                        market = market,
                        onClick = { onSelectSymbol(market.symbol) }
                    )
                }
            }
        }
    }
}

@Composable
fun MarketRow(
    market: com.bswap.shared.trading.MarketData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val changeColor = if (market.isPositiveChange) 
        androidx.compose.ui.graphics.Color(0xFF4CAF50) 
    else 
        androidx.compose.ui.graphics.Color(0xFFF44336)
    
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = market.symbol,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "$${market.price.formatPrice()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    text = market.change24h.formatPercent(),
                    style = MaterialTheme.typography.labelMedium,
                    color = changeColor
                )
                Text(
                    text = "Vol: ${(market.volume24h / 1000000).formatPrice()}M",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}