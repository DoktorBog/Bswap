package com.bswap.trading.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.bswap.shared.trading.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingChart(
    symbol: String,
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var selectedTimeframe by remember { mutableStateOf("1h") }
    var chartData by remember { mutableStateOf<TradingViewData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    val timeframes = listOf("1m", "5m", "15m", "1h", "4h", "1d")
    
    // Load chart data
    LaunchedEffect(symbol, selectedTimeframe) {
        scope.launch {
            isLoading = true
            error = null
            try {
                val result = apiClient.getCandlestickData(symbol, selectedTimeframe, 100)
                result.fold(
                    onSuccess = { data ->
                        chartData = data
                        isLoading = false
                    },
                    onFailure = { e ->
                        error = e.message
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Chart Header with Timeframe Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            // Timeframe Pills
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                timeframes.forEach { timeframe ->
                    FilterChip(
                        selected = selectedTimeframe == timeframe,
                        onClick = { selectedTimeframe = timeframe },
                        label = { Text(timeframe) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }
        
        // Chart Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator()
                    }
                    error != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Error loading chart",
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    chartData != null -> {
                        PriceLineChart(
                            data = chartData!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Chart Controls and Indicators
        chartData?.let { data ->
            ChartIndicators(
                data = data,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun PriceLineChart(
    data: TradingViewData,
    modifier: Modifier = Modifier
) {
    if (data.candles.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("No data available")
        }
        return
    }
    
    val chartColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { /* Handle chart interactions */ }
            }
    ) {
        val candles = data.candles
        if (candles.isEmpty()) return@Canvas
        
        val padding = 40f
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2
        
        // Calculate price range
        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val priceRange = maxPrice - minPrice
        
        if (priceRange == 0.0) return@Canvas
        
        // Draw grid lines
        drawGrid(
            width = chartWidth,
            height = chartHeight,
            padding = padding,
            color = gridColor
        )
        
        // Create path for price line
        val path = Path()
        candles.forEachIndexed { index, candle ->
            val x = padding + (index.toFloat() / (candles.size - 1)) * chartWidth
            val y = padding + ((maxPrice - candle.close) / priceRange).toFloat() * chartHeight
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Draw the price line
        drawPath(
            path = path,
            color = chartColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
        
        // Draw price labels
        drawPriceLabels(
            minPrice = minPrice,
            maxPrice = maxPrice,
            chartHeight = chartHeight,
            padding = padding,
            textColor = textColor
        )
        
        // Draw time labels
        drawTimeLabels(
            candles = candles,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            padding = padding,
            textColor = textColor
        )
    }
}

private fun DrawScope.drawGrid(
    width: Float,
    height: Float,
    padding: Float,
    color: Color
) {
    // Horizontal grid lines
    for (i in 0..5) {
        val y = padding + (i / 5f) * height
        drawLine(
            color = color,
            start = Offset(padding, y),
            end = Offset(padding + width, y),
            strokeWidth = 1.dp.toPx()
        )
    }
    
    // Vertical grid lines
    for (i in 0..6) {
        val x = padding + (i / 6f) * width
        drawLine(
            color = color,
            start = Offset(x, padding),
            end = Offset(x, padding + height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawPriceLabels(
    minPrice: Double,
    maxPrice: Double,
    chartHeight: Float,
    padding: Float,
    textColor: Color
) {
    val priceRange = maxPrice - minPrice
    for (i in 0..5) {
        val price = maxPrice - (i / 5.0) * priceRange
        val y = padding + (i / 5f) * chartHeight
        
        // Note: In a real implementation, you'd use proper text drawing
        // For now, this is a placeholder for the grid structure
    }
}

private fun DrawScope.drawTimeLabels(
    candles: List<CandlestickData>,
    chartWidth: Float,
    chartHeight: Float,
    padding: Float,
    textColor: Color
) {
    // Draw time labels at bottom
    val labelCount = min(6, candles.size)
    for (i in 0 until labelCount) {
        val index = (i.toFloat() / (labelCount - 1) * (candles.size - 1)).toInt()
        val x = padding + (i.toFloat() / (labelCount - 1)) * chartWidth
        val y = padding + chartHeight + 20f
        
        // Note: In a real implementation, you'd use proper text drawing
        // This is simplified for demonstration
    }
}

@Composable
fun ChartIndicators(
    data: TradingViewData,
    modifier: Modifier = Modifier
) {
    if (data.candles.isEmpty()) return
    
    val latestCandle = data.candles.last()
    val previousCandle = if (data.candles.size > 1) data.candles[data.candles.size - 2] else latestCandle
    val priceChange = latestCandle.close - previousCandle.close
    val priceChangePercent = (priceChange / previousCandle.close) * 100
    
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
            Text(
                text = "Market Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MarketIndicator(
                    label = "Last Price",
                    value = "$${latestCandle.close.formatPrice()}",
                    change = priceChange,
                    changePercent = priceChangePercent
                )
                
                MarketIndicator(
                    label = "24h Volume",
                    value = "${(latestCandle.volume / 1000000).formatPrice()}M",
                    change = null,
                    changePercent = null
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MarketIndicator(
                    label = "24h High",
                    value = "$${data.candles.maxOf { it.high }.formatPrice()}",
                    change = null,
                    changePercent = null
                )
                
                MarketIndicator(
                    label = "24h Low",
                    value = "$${data.candles.minOf { it.low }.formatPrice()}",
                    change = null,
                    changePercent = null
                )
            }
            
            // Technical indicators
            data.indicators["sma20"]?.lastOrNull()?.let { sma20 ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MarketIndicator(
                        label = "SMA(20)",
                        value = "$${sma20.formatPrice()}",
                        change = null,
                        changePercent = null
                    )
                    
                    data.indicators["rsi"]?.lastOrNull()?.let { rsi ->
                        MarketIndicator(
                            label = "RSI(14)",
                            value = "${rsi.toInt()}",
                            change = null,
                            changePercent = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarketIndicator(
    label: String,
    value: String,
    change: Double?,
    changePercent: Double?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        if (change != null && changePercent != null) {
            val isPositive = change >= 0
            val color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = "${if (isPositive) "+" else ""}${change.formatPnL()} (${changePercent.formatPercent()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderBookWidget(
    symbol: String,
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var orderBook by remember { mutableStateOf<OrderBook?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(symbol) {
        scope.launch {
            try {
                val result = apiClient.getOrderBook(symbol, 10)
                result.fold(
                    onSuccess = { book ->
                        orderBook = book
                        isLoading = false
                    },
                    onFailure = {
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Order Book",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                orderBook?.let { book ->
                    OrderBookDisplay(book)
                }
            }
        }
    }
}

@Composable
fun OrderBookDisplay(
    orderBook: OrderBook,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Price",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Size",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Asks (sell orders) - displayed in reverse order (highest price first)
        orderBook.asks.take(5).forEach { ask ->
            OrderBookRow(
                price = ask.price,
                size = ask.size,
                isAsk = true
            )
        }
        
        // Spread indicator
        val spread = orderBook.asks.firstOrNull()?.price?.minus(orderBook.bids.firstOrNull()?.price ?: 0.0) ?: 0.0
        val spreadPercent = if (orderBook.bids.isNotEmpty()) (spread / orderBook.bids.first().price) * 100 else 0.0
        
        Divider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Spread: ${spread.formatPrice()} (${spreadPercent.formatPercent()})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Divider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline
        )
        
        // Bids (buy orders)
        orderBook.bids.take(5).forEach { bid ->
            OrderBookRow(
                price = bid.price,
                size = bid.size,
                isAsk = false
            )
        }
    }
}

@Composable
fun OrderBookRow(
    price: Double,
    size: Double,
    isAsk: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isAsk) {
        Color(0xFFF44336).copy(alpha = 0.1f)
    } else {
        Color(0xFF4CAF50).copy(alpha = 0.1f)
    }
    
    val textColor = if (isAsk) {
        Color(0xFFF44336)
    } else {
        Color(0xFF4CAF50)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = price.formatPrice(),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = size.formatPrice(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun RecentTradesWidget(
    symbol: String,
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var trades by remember { mutableStateOf<List<RecentTrade>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(symbol) {
        scope.launch {
            try {
                val result = apiClient.getRecentTrades(symbol, 20)
                result.fold(
                    onSuccess = { tradesList ->
                        trades = tradesList
                        isLoading = false
                    },
                    onFailure = {
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Recent Trades",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(trades) { trade ->
                        RecentTradeRow(trade)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTradeRow(
    trade: RecentTrade,
    modifier: Modifier = Modifier
) {
    val isBuy = trade.side == "BUY"
    val textColor = if (isBuy) Color(0xFF4CAF50) else Color(0xFFF44336)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = trade.price.formatPrice(),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = trade.size.formatPrice(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = trade.timestamp.formatTime(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}