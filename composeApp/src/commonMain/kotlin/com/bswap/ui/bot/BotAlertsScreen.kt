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

data class BotAlert(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long,
    val isRead: Boolean = false
)

enum class AlertType {
    TRADE_EXECUTED, PROFIT_TARGET, STOP_LOSS, ERROR, SYSTEM, RISK_WARNING
}

enum class AlertSeverity {
    INFO, WARNING, ERROR, CRITICAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotAlertsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mock alerts data
    val alerts = remember {
        listOf(
            BotAlert("1", AlertType.TRADE_EXECUTED, "Trade Completed", "Successfully bought 1.5 SOL worth of BONK", AlertSeverity.INFO, System.currentTimeMillis() - 300000),
            BotAlert("2", AlertType.PROFIT_TARGET, "Profit Target Hit", "PEPE position reached +25% target", AlertSeverity.INFO, System.currentTimeMillis() - 900000, true),
            BotAlert("3", AlertType.STOP_LOSS, "Stop Loss Triggered", "WIF position hit -8% stop loss", AlertSeverity.WARNING, System.currentTimeMillis() - 1800000),
            BotAlert("4", AlertType.ERROR, "Transaction Failed", "Failed to execute buy order for MYRO", AlertSeverity.ERROR, System.currentTimeMillis() - 3600000),
            BotAlert("5", AlertType.SYSTEM, "Bot Started", "Trading bot started successfully", AlertSeverity.INFO, System.currentTimeMillis() - 7200000, true),
            BotAlert("6", AlertType.RISK_WARNING, "Daily Loss Limit", "Approaching daily loss limit of 100 SOL", AlertSeverity.CRITICAL, System.currentTimeMillis() - 10800000),
        )
    }
    
    val unreadCount = alerts.count { !it.isRead }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Bot Alerts",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = { /* Mark all as read */ }) {
                            Text("Mark all read")
                        }
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
                // Alerts Summary
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Alerts",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$unreadCount unread notifications",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (unreadCount > 0) {
                            Badge(
                                containerColor = Color(0xFFF44336)
                            ) {
                                Text(
                                    text = unreadCount.toString(),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Text(
                    text = "Recent Alerts",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(alerts) { alert ->
                AlertCard(alert = alert)
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: BotAlert,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (!alert.isRead) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Alert icon
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = getAlertColor(alert.severity).copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = getAlertIcon(alert.type),
                    contentDescription = null,
                    tint = getAlertColor(alert.severity),
                    modifier = Modifier.padding(8.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (!alert.isRead) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                    AlertSeverityChip(severity = alert.severity)
                }
                
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = formatTimestamp(alert.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!alert.isRead) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {}
            }
        }
    }
}

@Composable
private fun AlertSeverityChip(
    severity: AlertSeverity,
    modifier: Modifier = Modifier
) {
    val color = getAlertColor(severity)
    
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = severity.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun getAlertIcon(type: AlertType) = when (type) {
    AlertType.TRADE_EXECUTED -> Icons.Default.TrendingUp
    AlertType.PROFIT_TARGET -> Icons.Default.AttachMoney
    AlertType.STOP_LOSS -> Icons.Default.TrendingDown
    AlertType.ERROR -> Icons.Default.Error
    AlertType.SYSTEM -> Icons.Default.Settings
    AlertType.RISK_WARNING -> Icons.Default.Warning
}

private fun getAlertColor(severity: AlertSeverity) = when (severity) {
    AlertSeverity.INFO -> Color(0xFF2196F3)
    AlertSeverity.WARNING -> Color(0xFFFF9800)
    AlertSeverity.ERROR -> Color(0xFFF44336)
    AlertSeverity.CRITICAL -> Color(0xFF9C27B0)
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}