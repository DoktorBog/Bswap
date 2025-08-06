package com.bswap.ui.bot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bswap.api.BotApi
import com.bswap.models.BotStatus
import com.bswap.models.TradingStatistics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotDashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    var botStatus by remember { mutableStateOf<BotStatus?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Auto-refresh every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            scope.launch {
                try {
                    println("BotDashboard: Fetching bot status...")
                    val response = BotApi.getBotStatus()
                    println("BotDashboard: Response received - success: ${response.success}")
                    if (response.success) {
                        botStatus = response.data
                        error = null
                        println("BotDashboard: Status updated successfully")
                    } else {
                        error = response.message
                        println("BotDashboard: API error: ${response.message}")
                    }
                } catch (e: Exception) {
                    error = "Connection error: ${e.message}"
                    println("BotDashboard: Exception: ${e.message}")
                    e.printStackTrace()
                }
                isLoading = false
            }
            delay(5000)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Bot Dashboard",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    // Bot Status Card
                    StatusCard(
                        status = botStatus,
                        error = error,
                        onStartStop = { start ->
                            scope.launch {
                                try {
                                    val response = if (start) BotApi.startBot() else BotApi.stopBot()
                                    if (!response.success) {
                                        error = response.message
                                    }
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        }
                    )
                }

                item {
                    // Quick Stats
                    QuickStatsCard(botStatus?.statistics)
                }

                item {
                    // Navigation Cards
                    NavigationGrid(
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToAnalytics = onNavigateToAnalytics,
                        onNavigateToWallet = onNavigateToWallet,
                        onNavigateToHistory = onNavigateToHistory,
                        onNavigateToAlerts = onNavigateToAlerts
                    )
                }

                error?.let { errorMsg ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "Error: $errorMsg",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: BotStatus?,
    error: String?,
    onStartStop: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                error != null -> MaterialTheme.colorScheme.errorContainer
                status?.isRunning == true -> Color(0xFF1B5E20).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
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
                    text = "Bot Status",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            error != null -> MaterialTheme.colorScheme.error
                            status?.isRunning == true -> Color(0xFF4CAF50)
                            else -> Color(0xFFFF9800)
                        }
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = when {
                            error != null -> "ERROR"
                            status?.isRunning == true -> "RUNNING"
                            else -> "STOPPED"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            status?.let { botStatus ->
                Text(
                    text = "Uptime: ${formatUptime(botStatus.uptimeMillis)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Current Token: ${botStatus.currentToken ?: "None"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onStartStop(true) },
                    enabled = status?.isRunning != true,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start")
                }

                OutlinedButton(
                    onClick = { onStartStop(false) },
                    enabled = status?.isRunning == true,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun QuickStatsCard(statistics: TradingStatistics?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Statistics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(
                    label = "Total Trades",
                    value = statistics?.totalTrades?.toString() ?: "0"
                )
                StatItem(
                    label = "Success Rate",
                    value = "${statistics?.successRate?.let { (it * 100).toInt() } ?: 0}%"
                )
                StatItem(
                    label = "P&L",
                    value = "${statistics?.totalProfitLoss ?: 0.0} SOL"
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NavigationGrid(
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAlerts: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Bot Controls",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavigationCard(
                title = "Settings",
                icon = Icons.Default.Settings,
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f)
            )
            NavigationCard(
                title = "Analytics",
                icon = Icons.Default.Analytics,
                onClick = onNavigateToAnalytics,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavigationCard(
                title = "Wallet",
                icon = Icons.Default.AccountBalanceWallet,
                onClick = onNavigateToWallet,
                modifier = Modifier.weight(1f)
            )
            NavigationCard(
                title = "History",
                icon = Icons.Default.History,
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f)
            )
        }
        
        NavigationCard(
            title = "Alerts & Notifications",
            icon = Icons.Default.Notifications,
            onClick = onNavigateToAlerts,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NavigationCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatUptime(uptimeMillis: Long): String {
    val seconds = uptimeMillis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}