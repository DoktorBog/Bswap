package com.bswap.trading

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bswap.shared.trading.*
import com.bswap.trading.charts.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingDashboard(
    apiClient: TradingApiClient,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var tradingState by remember { mutableStateOf(TradingState()) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var showPositionDialog by remember { mutableStateOf(false) }
    var selectedSymbol by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    // Real-time data updates
    LaunchedEffect(Unit) {
        apiClient.tradingDataFlow().collect { state ->
            tradingState = state
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Status bar padding and toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                // Toolbar
                TopAppBar(
                    title = {
                        Text(
                            text = "Trading",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        onBackClick?.let {
                            IconButton(onClick = it) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            
            // Main content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
        // 1. Компактний баланс зверху
        item {
            BalanceHeader(
                stats = tradingState.stats,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 2. Список всіх торгових пар
        item {
            TradingPairsList(
                markets = tradingState.markets,
                selectedSymbol = selectedSymbol,
                onSelectSymbol = { symbol ->
                    selectedSymbol = symbol
                },
                onOpenLong = { symbol ->
                    scope.launch {
                        apiClient.openPosition(
                            OpenPositionRequest(
                                symbol = symbol,
                                side = "LONG",
                                size = 100.0,
                                leverage = 1.0
                            )
                        )
                    }
                },
                onOpenShort = { symbol ->
                    scope.launch {
                        apiClient.openPosition(
                            OpenPositionRequest(
                                symbol = symbol,
                                side = "SHORT", 
                                size = 100.0,
                                leverage = 1.0
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 3. Компактна лінія відкритих ордерів
        if (tradingState.orders.isNotEmpty()) {
            item {
                CompactOrdersLine(
                    orders = tradingState.orders,
                    onViewAll = { /* Navigate to orders screen */ }
                )
            }
        }
        
        // 4. Деталі вибраної пари (коли пара вибрана)
        if (selectedSymbol.isNotEmpty()) {
            item {
                SelectedPairDetails(
                    symbol = selectedSymbol,
                    markets = tradingState.markets,
                    apiClient = apiClient,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
            }
        }
    }
    
    // Dialogs
    if (showOrderDialog) {
        CreateOrderDialog(
            symbol = selectedSymbol,
            onDismiss = { showOrderDialog = false },
            onCreateOrder = { request ->
                scope.launch {
                    apiClient.createOrder(request)
                    showOrderDialog = false
                }
            }
        )
    }
    
    if (showPositionDialog) {
        OpenPositionDialog(
            symbol = selectedSymbol,
            onDismiss = { showPositionDialog = false },
            onOpenPosition = { request ->
                scope.launch {
                    // Call position opening endpoint
                    showPositionDialog = false
                }
            }
        )
    }
}

@Composable
fun MarketSelector(
    selectedSymbol: String,
    markets: List<MarketData>,
    onSymbolSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Market Selection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "${markets.size} Markets",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(markets) { market ->
                    MarketChip(
                        market = market,
                        isSelected = market.symbol == selectedSymbol,
                        onClick = { onSymbolSelected(market.symbol) }
                    )
                }
            }
        }
    }
}

@Composable
fun MarketChip(
    market: MarketData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPositive = market.change24h >= 0
    val changeColor = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
    
    Card(
        modifier = modifier
            .clickable { onClick() }
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = market.symbol,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "$${market.price.formatPrice()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurface
            )
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = changeColor.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = changeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    
                    Text(
                        text = "${market.change24h.formatPercent()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = changeColor
                    )
                }
            }
        }
    }
}

@Composable
fun TradingControls(
    selectedSymbol: String,
    onCreateOrder: () -> Unit,
    onOpenPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Quick Trading Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onCreateOrder,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Add, 
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Buy",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                OutlinedButton(
                    onClick = onCreateOrder,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    border = BorderStroke(2.dp, Color(0xFFF44336)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Remove, 
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sell",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Text(
                text = "Selected: $selectedSymbol",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Button(
                onClick = onOpenPosition,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Icon(
                    Icons.Default.TrendingUp, 
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Open Advanced Position",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TradingHeader(
    stats: TradingStats?,
    onStartTrading: () -> Unit,
    onStopTrading: () -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Trading Dashboard",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Real-time trading control center",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                
                // Enhanced status indicator
                stats?.let { s ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (s.isRunning) 
                                Color(0xFF4CAF50).copy(alpha = 0.2f) 
                            else 
                                Color(0xFFF44336).copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = if (s.isRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            
                            Text(
                                text = if (s.isRunning) "ACTIVE" else "STOPPED",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (s.isRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }
                }
            }
            
            // Enhanced trading controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartTrading,
                    enabled = stats?.isRunning != true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow, 
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Start",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                OutlinedButton(
                    onClick = onStopTrading,
                    enabled = stats?.isRunning == true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Stop, 
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Stop",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        Icons.Default.Warning, 
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Emergency",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Enhanced quick stats
            stats?.let { s ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QuickStat(
                            label = "Balance",
                            value = "$${s.totalBalanceUsd.formatPrice()}"
                        )
                        
                        QuickStat(
                            label = "Positions",
                            value = s.activePositions.toString()
                        )
                        
                        QuickStat(
                            label = "PnL",
                            value = s.unrealizedPnl.formatPnL(),
                            isPositive = s.unrealizedPnl >= 0
                        )
                        
                        QuickStat(
                            label = "Exchange",
                            value = s.exchange
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStat(
    label: String,
    value: String,
    isPositive: Boolean? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = when (isPositive) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFF44336)
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun TradingSummary(
    balance: Double,
    positions: List<Position>,
    orders: List<Order>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                label = "Total Balance",
                value = "$${balance.formatPrice()}",
                icon = Icons.Default.AccountBalanceWallet
            )
            
            SummaryItem(
                label = "Active Positions",
                value = positions.size.toString(),
                icon = Icons.Default.TrendingUp
            )
            
            SummaryItem(
                label = "Open Orders",
                value = orders.size.toString(),
                icon = Icons.Default.Receipt
            )
            
            val totalPnL = positions.sumOf { it.pnl }
            SummaryItem(
                label = "Unrealized PnL",
                value = totalPnL.formatPnL(),
                icon = if (totalPnL >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                isPositive = totalPnL >= 0
            )
        }
    }
}

@Composable
fun SummaryItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPositive: Boolean? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when (isPositive) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFF44336)
                null -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when (isPositive) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFF44336)
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PositionsSection(
    positions: List<Position>,
    onClosePosition: (String) -> Unit,
    onOpenPositionDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Active Positions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${positions.size} position${if (positions.size != 1) "s" else ""} open",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = onOpenPositionDialog,
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Icon(
                        Icons.Default.Add, 
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Open Position",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (positions.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No active positions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Open your first position to start trading",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    positions.forEach { position ->
                        PositionRow(
                            position = position,
                            onClose = { onClosePosition(position.symbol) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PositionRow(
    position: Position,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pnlColor = if (position.isProfit) Color(0xFF4CAF50) else Color(0xFFF44336)
    val sideColor = if (position.side == "LONG") Color(0xFF4CAF50) else Color(0xFFF44336)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = position.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = sideColor.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = position.side,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sideColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Text(
                    text = "Leverage: ${position.leverage}x • Size: ${position.size.formatPrice()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Entry: $${position.entryPrice.formatPrice()} • Mark: $${position.markPrice.formatPrice()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // PnL Info
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(0.8f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = pnlColor.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = position.pnl.formatPnL(),
                        style = MaterialTheme.typography.titleMedium,
                        color = pnlColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                
                Text(
                    text = "${(position.pnlPercent * 100).formatPrice()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = pnlColor,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(40.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF44336).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close position",
                        tint = Color(0xFFF44336),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OrdersSection(
    orders: List<Order>,
    onCancelOrder: (String) -> Unit,
    onCreateOrderDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Orders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = onCreateOrderDialog,
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Order")
                }
            }
            
            if (orders.isEmpty()) {
                Text(
                    text = "No open orders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                orders.forEach { order ->
                    OrderRow(
                        order = order,
                        onCancel = { onCancelOrder(order.orderId) }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderRow(
    order: Order,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sideColor = if (order.side == "BUY") Color(0xFF4CAF50) else Color(0xFFF44336)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = order.symbol,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${order.side} • ${order.type}",
                style = MaterialTheme.typography.bodySmall,
                color = sideColor
            )
        }
        
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "$${order.price.formatPrice()}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Size: ${order.size.formatPrice()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Cancel,
                contentDescription = "Cancel order",
                tint = Color(0xFFF44336)
            )
        }
    }
}

@Composable
fun CreateOrderDialog(
    symbol: String,
    onDismiss: () -> Unit,
    onCreateOrder: (CreateOrderRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    var side by remember { mutableStateOf("BUY") }
    var amount by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var orderType by remember { mutableStateOf("MARKET") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Order - $symbol") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Side selector
                Row {
                    FilterChip(
                        selected = side == "BUY",
                        onClick = { side = "BUY" },
                        label = { Text("Buy") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = side == "SELL",
                        onClick = { side = "SELL" },
                        label = { Text("Sell") }
                    )
                }
                
                // Order type
                Row {
                    FilterChip(
                        selected = orderType == "MARKET",
                        onClick = { orderType = "MARKET" },
                        label = { Text("Market") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = orderType == "LIMIT",
                        onClick = { orderType = "LIMIT" },
                        label = { Text("Limit") }
                    )
                }
                
                // Amount input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Price input (only for limit orders)
                if (orderType == "LIMIT") {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = CreateOrderRequest(
                        symbol = symbol,
                        side = side,
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        price = if (orderType == "LIMIT") price.toDoubleOrNull() else null,
                        type = orderType
                    )
                    onCreateOrder(request)
                }
            ) {
                Text("Create Order")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OpenPositionDialog(
    symbol: String,
    onDismiss: () -> Unit,
    onOpenPosition: (OpenPositionRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    var side by remember { mutableStateOf("LONG") }
    var size by remember { mutableStateOf("") }
    var leverage by remember { mutableStateOf("1") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open Position - $symbol") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Side selector
                Row {
                    FilterChip(
                        selected = side == "LONG",
                        onClick = { side = "LONG" },
                        label = { Text("Long") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = side == "SHORT",
                        onClick = { side = "SHORT" },
                        label = { Text("Short") }
                    )
                }
                
                // Size input
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Position Size") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Leverage input
                OutlinedTextField(
                    value = leverage,
                    onValueChange = { leverage = it },
                    label = { Text("Leverage (1-20x)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = OpenPositionRequest(
                        symbol = symbol,
                        side = side,
                        size = size.toDoubleOrNull() ?: 0.0,
                        leverage = leverage.toDoubleOrNull() ?: 1.0
                    )
                    onOpenPosition(request)
                }
            ) {
                Text("Open Position")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// =====================================
// COMPACT MOBILE COMPONENTS
// =====================================

@Composable
fun BalanceHeader(
    stats: TradingStats?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Portfolio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stats?.let { (it.unrealizedPnl + it.realizedPnl).formatPnL() } ?: "$0.00",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (stats?.let { it.unrealizedPnl + it.realizedPnl } ?: 0.0 >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${stats?.activePositions ?: 0}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CompactMarketSelector(
    markets: List<MarketData>,
    selectedSymbol: String?,
    onSelectSymbol: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(markets.take(10)) { market ->
            FilterChip(
                selected = selectedSymbol == market.symbol,
                onClick = { onSelectSymbol(market.symbol) },
                label = {
                    Column {
                        Text(
                            text = market.symbol,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = market.change24h.formatPercent(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (market.isPositiveChange) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                },
                modifier = Modifier.height(48.dp)
            )
        }
    }
}

@Composable
fun QuickTradeButtons(
    selectedSymbol: String?,
    onCreateOrder: (CreateOrderRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                selectedSymbol?.let { symbol ->
                    onCreateOrder(
                        CreateOrderRequest(
                            symbol = symbol,
                            type = "MARKET",
                            side = "BUY",
                            amount = 100.0
                        )
                    )
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            ),
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("BUY")
        }
        
        Button(
            onClick = {
                selectedSymbol?.let { symbol ->
                    onCreateOrder(
                        CreateOrderRequest(
                            symbol = symbol,
                            type = "MARKET",
                            side = "SELL",
                            amount = 100.0
                        )
                    )
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF44336)
            ),
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Icon(Icons.Default.TrendingDown, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("SELL")
        }
    }
}

@Composable
fun CompactPositions(
    positions: List<Position>,
    onClosePosition: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Positions (${positions.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            if (positions.isEmpty()) {
                Text(
                    text = "No active positions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                positions.take(3).forEach { position ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = position.symbol,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${position.side} • ${position.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Text(
                            text = position.pnl.formatPnL(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (position.pnl >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (position != positions.take(3).last()) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
                
                if (positions.size > 3) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "and ${positions.size - 3} more...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CompactOrderBook(
    selectedSymbol: String?,
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var orderBook by remember { mutableStateOf<OrderBook?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(selectedSymbol) {
        selectedSymbol?.let { symbol ->
            scope.launch {
                val result = apiClient.getOrderBook(symbol, 5)
                result.getOrNull()?.let { orderBook = it }
            }
        }
    }
    
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Order Book",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            orderBook?.let { book ->
                // Asks (top 3)
                book.asks.take(3).forEach { ask ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = ask.price.formatPrice(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF44336)
                        )
                        Text(
                            text = ask.size.formatPrice(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                
                // Bids (top 3)
                book.bids.take(3).forEach { bid ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bid.price.formatPrice(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = bid.size.formatPrice(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } ?: run {
                Text(
                    text = "Loading order book...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CompactRecentTrades(
    selectedSymbol: String?,
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var trades by remember { mutableStateOf<List<RecentTrade>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(selectedSymbol) {
        selectedSymbol?.let { symbol ->
            scope.launch {
                val result = apiClient.getRecentTrades(symbol, 5)
                result.getOrNull()?.let { trades = it }
            }
        }
    }
    
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Recent Trades",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            if (trades.isEmpty()) {
                Text(
                    text = "Loading trades...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                trades.forEach { trade ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = trade.price.formatPrice(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (trade.side == "BUY") Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Text(
                            text = trade.size.formatPrice(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = trade.timestamp.formatTime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactOrders(
    orders: List<Order>,
    onCancelOrder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Orders (${orders.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            if (orders.isEmpty()) {
                Text(
                    text = "No active orders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                orders.take(3).forEach { order ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${order.symbol} ${order.side}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${order.type} • ${order.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        IconButton(
                            onClick = { onCancelOrder(order.orderId) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Cancel",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                if (orders.size > 3) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "and ${orders.size - 3} more...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =====================================
// ENHANCED TRADING PAIRS LIST
// =====================================

@Composable
fun TradingPairsList(
    markets: List<MarketData>,
    selectedSymbol: String?,
    onSelectSymbol: (String) -> Unit,
    onOpenLong: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Trading Pairs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (markets.isEmpty()) {
                Text(
                    text = "Loading trading pairs...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(markets) { market ->
                        TradingPairItem(
                            market = market,
                            isSelected = selectedSymbol == market.symbol,
                            onSelectSymbol = onSelectSymbol,
                            onOpenLong = onOpenLong,
                            onOpenShort = onOpenShort
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TradingPairItem(
    market: MarketData,
    isSelected: Boolean,
    onSelectSymbol: (String) -> Unit,
    onOpenLong: (String) -> Unit,
    onOpenShort: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelectSymbol(market.symbol) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header with symbol and price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = market.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$${market.price.formatPrice()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = market.change24h.formatPercent(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (market.isPositiveChange) 
                            Color(0xFF4CAF50) 
                        else 
                            Color(0xFFF44336)
                    )
                    Text(
                        text = "Vol: ${(market.volume24h / 1000000).formatPrice()}M",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Market info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoChip(
                    label = "Bid",
                    value = "$${market.bid.formatPrice()}",
                    color = Color(0xFF4CAF50)
                )
                
                InfoChip(
                    label = "Ask", 
                    value = "$${market.ask.formatPrice()}",
                    color = Color(0xFFF44336)
                )
                
                InfoChip(
                    label = "Spread",
                    value = market.spreadPercent.formatPercent(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Trading buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpenLong(market.symbol) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("LONG", style = MaterialTheme.typography.labelMedium)
                }
                
                Button(
                    onClick = { onOpenShort(market.symbol) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("SHORT", style = MaterialTheme.typography.labelMedium)
                }
                
                OutlinedButton(
                    onClick = { onSelectSymbol(market.symbol) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("CHART", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun InfoChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// =====================================
// COMPACT ORDERS LINE & PAIR DETAILS
// =====================================

@Composable
fun CompactOrdersLine(
    orders: List<Order>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { onViewAll() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${orders.size} Active Orders",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SelectedPairDetails(
    symbol: String,
    markets: List<MarketData>,
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    val market = markets.find { it.symbol == symbol }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                market?.let {
                    Text(
                        text = it.change24h.formatPercent(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (it.isPositiveChange) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Chart
            TradingChart(
                symbol = symbol,
                apiClient = apiClient,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Trading buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Open long position */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("LONG")
                }
                
                Button(
                    onClick = { /* Open short position */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("SHORT")
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Order book and recent trades
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactOrderBook(
                    selectedSymbol = symbol,
                    apiClient = apiClient,
                    modifier = Modifier.weight(1f)
                )
                
                CompactRecentTrades(
                    selectedSymbol = symbol,
                    apiClient = apiClient,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}