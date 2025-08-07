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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random

data class TokenBalance(
    val symbol: String,
    val balance: Double,
    val usdValue: Double,
    val change24h: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotWalletScreen(
    onBack: () -> Unit,
    onNavigateToTransactionHistory: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Mock data
    val solBalance = Random.nextDouble(5.0, 50.0)
    val totalUSDValue = Random.nextDouble(1000.0, 10000.0)
    val tokenBalances = listOf(
        TokenBalance("USDC", Random.nextDouble(100.0, 1000.0), Random.nextDouble(100.0, 1000.0), Random.nextDouble(-5.0, 15.0)),
        TokenBalance("BONK", Random.nextDouble(1000000.0, 10000000.0), Random.nextDouble(50.0, 500.0), Random.nextDouble(-10.0, 25.0)),
        TokenBalance("RAY", Random.nextDouble(10.0, 100.0), Random.nextDouble(200.0, 800.0), Random.nextDouble(-8.0, 20.0)),
        TokenBalance("ORCA", Random.nextDouble(5.0, 50.0), Random.nextDouble(100.0, 400.0), Random.nextDouble(-12.0, 18.0))
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Bot Wallet",
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                // Wallet Overview
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Total Balance",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        
                        Text(
                            text = "$${"%.2f".format(totalUSDValue)}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "SOL Balance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${"%.4f".format(solBalance)} SOL",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "Active Trading",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Connected",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                // Quick Actions
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Quick Actions",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickActionButton(
                                icon = Icons.Default.Add,
                                label = "Fund",
                                onClick = { /* TODO */ }
                            )
                            QuickActionButton(
                                icon = Icons.Default.Remove,
                                label = "Withdraw",
                                onClick = { /* TODO */ }
                            )
                            QuickActionButton(
                                icon = Icons.Default.History,
                                label = "History",
                                onClick = { 
                                    // Mock wallet address for demo
                                    onNavigateToTransactionHistory?.invoke("7BgBvyjrZX8YKXAzN2wVbD8YkMXZJ4k9Fz8NyZ7VkT32") 
                                }
                            )
                        }
                    }
                }
            }
            
            item {
                Text(
                    text = "Token Holdings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(tokenBalances) { token ->
                TokenBalanceItem(token = token)
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TokenBalanceItem(
    token: TokenBalance,
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
                // Token icon placeholder
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = token.symbol.take(1),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Column {
                    Text(
                        text = token.symbol,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = if (token.symbol == "BONK") {
                            "${token.balance.toLong().toString().replace(Regex("(\\d)(?=(\\d{3})+\$)"), "$1,")} ${token.symbol}"
                        } else {
                            "${"%.4f".format(token.balance)} ${token.symbol}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$${"%.2f".format(token.usdValue)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "${if (token.change24h >= 0) "+" else ""}${"%.1f".format(token.change24h)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (token.change24h >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
    }
}