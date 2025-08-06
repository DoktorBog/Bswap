package com.bswap.ui.bot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

data class TradeHistoryItem(
    val id: String,
    val type: TradeType,
    val token: String,
    val amount: Double,
    val price: Double,
    val profit: Double,
    val timestamp: Long,
    val status: TradeStatus
)

enum class TradeType { BUY, SELL }
enum class TradeStatus { SUCCESS, FAILED, PENDING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mock trade history data
    val trades = remember {
        (1..20).map { i ->
            val type = if (Random.nextBoolean()) TradeType.BUY else TradeType.SELL
            val profit = if (type == TradeType.SELL) Random.nextDouble(-50.0, 200.0) else 0.0
            TradeHistoryItem(
                id = "trade_$i",
                type = type,
                token = listOf("BONK", "PEPE", "WIF", "MYRO", "POPCAT").random(),
                amount = Random.nextDouble(0.1, 5.0),
                price = Random.nextDouble(0.001, 10.0),
                profit = profit,
                timestamp = System.currentTimeMillis() - (i * 3600000L),
                status = listOf(TradeStatus.SUCCESS, TradeStatus.FAILED, TradeStatus.PENDING).random()
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Trading History",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                // Summary Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Trading Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            SummaryItem(
                                title = "Total Trades",
                                value = trades.size.toString()
                            )
                            SummaryItem(
                                title = "Successful",
                                value = trades.count { it.status == TradeStatus.SUCCESS }.toString()
                            )
                            SummaryItem(
                                title = "Total P&L",
                                value = "${if (trades.sumOf { it.profit } >= 0) "+" else ""}${"%.2f".format(trades.sumOf { it.profit })} SOL"
                            )
                        }
                    }
                }
            }
            
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Trades",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = { /* Filter trades */ }) {
                        Text("Filter")
                        Icon(Icons.Default.FilterList, contentDescription = null)
                    }
                }
            }
            
            items(trades) { trade ->
                TradeHistoryCard(trade = trade)
            }
        }
    }
}

@Composable
private fun SummaryItem(
    title: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TradeHistoryCard(
    trade: TradeHistoryItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Trade type indicator
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = when (trade.type) {
                        TradeType.BUY -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        TradeType.SELL -> Color(0xFF2196F3).copy(alpha = 0.1f)
                    }
                ) {
                    Icon(
                        imageVector = when (trade.type) {
                            TradeType.BUY -> Icons.Default.TrendingUp
                            TradeType.SELL -> Icons.Default.TrendingDown
                        },
                        contentDescription = null,
                        tint = when (trade.type) {
                            TradeType.BUY -> Color(0xFF4CAF50)
                            TradeType.SELL -> Color(0xFF2196F3)
                        },
                        modifier = Modifier.padding(8.dp)
                    )
                }
                
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${trade.type.name} ${trade.token}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        StatusChip(status = trade.status)
                    }
                    Text(
                        text = "${"%.4f".format(trade.amount)} SOL @ $${"%.6f".format(trade.price)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTimestamp(trade.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (trade.type == TradeType.SELL) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${if (trade.profit >= 0) "+" else ""}${"%.4f".format(trade.profit)} SOL",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (trade.profit >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Text(
                        text = "${if (trade.profit >= 0) "+" else ""}${"%.1f".format((trade.profit / trade.amount) * 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: TradeStatus,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (status) {
        TradeStatus.SUCCESS -> Color(0xFF4CAF50) to "SUCCESS"
        TradeStatus.FAILED -> Color(0xFFF44336) to "FAILED"
        TradeStatus.PENDING -> Color(0xFFFF9800) to "PENDING"
    }
    
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}