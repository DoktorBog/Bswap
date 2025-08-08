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
import com.bswap.app.models.BotWalletViewModel
import org.koin.compose.viewmodel.koinViewModel
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
    val viewModel: BotWalletViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    
    // Real data from API
    val solBalance = state.balance?.solBalance ?: 0.0
    val totalUSDValue = state.balance?.totalValueUSD ?: 0.0
    
    // Convert tokens and balance data to UI format
    val tokenBalances = state.tokens.map { token ->
        val balance = token.amount?.toDoubleOrNull() ?: 0.0
        val adjustedBalance = if (token.decimals != null && token.decimals!! > 0) {
            balance / Math.pow(10.0, token.decimals!!.toDouble())
        } else balance
        
        val usdValue = state.balance?.tokenBalances?.get(token.symbol ?: token.mint.take(8)) ?: 0.0
        
        TokenBalance(
            symbol = token.symbol ?: token.mint.take(8) + "...",
            balance = adjustedBalance,
            usdValue = usdValue,
            change24h = Random.nextDouble(-10.0, 15.0) // TODO: Get real 24h change data
        )
    }

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
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refresh() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            // Loading state
            if (state.isLoading && state.balance == null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            
            // Error state
            if (state.error != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Error loading wallet data",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = state.error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = { 
                                    viewModel.clearError()
                                    viewModel.refresh() 
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
            
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
                                    // Real bot wallet public key
                                    onNavigateToTransactionHistory?.invoke("F277zfVkW6VBfkfWPNVXKoBEgCCeVcFYdiZDUX9yCPDW") 
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