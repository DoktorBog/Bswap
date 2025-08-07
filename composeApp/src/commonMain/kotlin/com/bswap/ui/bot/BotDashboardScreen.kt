package com.bswap.ui.bot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bswap.api.BotApi
import com.bswap.models.BotStatus
import com.bswap.models.TradingStatistics
import com.bswap.ui.ActionCard
import com.bswap.ui.DangerButton
import com.bswap.ui.GlowCard
import com.bswap.ui.ModernBackground
import com.bswap.ui.ModernCard
import com.bswap.ui.StatCard
import com.bswap.ui.SuccessButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    ModernBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Bot Dashboard",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
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
                    error?.let { errorMsg ->
                        item {
                            ModernCard(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = errorMsg,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // Bot Status Card
                        ModernStatusCard(
                            status = botStatus,
                            error = error,
                            onStartStop = { start: Boolean ->
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
                        ModernStatsGrid(botStatus?.statistics)
                    }

                    item {
                        // Navigation Cards
                        ModernNavigationGrid(
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToAnalytics = onNavigateToAnalytics,
                            onNavigateToWallet = onNavigateToWallet,
                            onNavigateToHistory = onNavigateToHistory,
                            onNavigateToAlerts = onNavigateToAlerts
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernStatusCard(
    status: BotStatus?,
    error: String?,
    onStartStop: (Boolean) -> Unit
) {
    val statusColor = when {
        error != null -> MaterialTheme.colorScheme.error
        status?.isRunning == true -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }

    GlowCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = statusColor
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
                    text = "Bot Status",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                ModernCard(
                    modifier = Modifier.padding(start = 12.dp),
                    containerColor = statusColor,
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            error != null -> Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )

                            status?.isRunning == true -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )

                            else -> Icon(
                                Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = when {
                                error != null -> "ERROR"
                                status?.isRunning == true -> "RUNNING"
                                else -> "STOPPED"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            status?.let { botStatus ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Uptime:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatUptime(botStatus.uptimeMillis),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Current Token:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = botStatus.currentToken ?: "None",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SuccessButton(
                    text = "Start",
                    onClick = { onStartStop(true) },
                    enabled = status?.isRunning != true,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PlayArrow
                    )

                DangerButton(
                    text = "Stop",
                    onClick = { onStartStop(false) },
                    enabled = status?.isRunning == true,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Stop
                )
            }
        }
    }
}

@Composable
fun ModernStatsGrid(statistics: TradingStatistics?) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Trading Statistics",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Total Trades",
                    value = statistics?.totalTrades?.toString() ?: "0",
                    subtitle = "Completed",
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                StatCard(
                    title = "Success Rate",
                    value = "${statistics?.successRate?.let { (it * 100).toInt() } ?: 0}%",
                    subtitle = "Win ratio",
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            StatCard(
                title = "Profit & Loss",
                value = "${statistics?.totalProfitLoss ?: 0.0} SOL",
                subtitle = "Net trading result",
                containerColor = if ((statistics?.totalProfitLoss ?: 0.0) >= 0) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if ((statistics?.totalProfitLoss ?: 0.0) >= 0) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
}

@Composable
fun StatItem(label: String, value: String) {
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
fun ModernNavigationGrid(
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAlerts: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bot Controls",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    title = "Settings",
                    subtitle = "",
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    title = "Analytics",
                    subtitle = "",
                    icon = {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    onClick = onNavigateToAnalytics,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    title = "Wallet",
                    subtitle = "",
                    icon = {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    onClick = onNavigateToWallet,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    title = "History",
                    subtitle = "",
                    icon = {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    onClick = onNavigateToHistory,
                    modifier = Modifier.weight(1f)
                )
            }

            ActionCard(
                title = "Alerts & Notifications",
                subtitle = "",
                icon = {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                onClick = onNavigateToAlerts
            )
        }
    }
}

@Composable
fun NavigationCard(
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
