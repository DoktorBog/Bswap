package com.bswap.ui.bot

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bswap.app.api.BotApi
import com.bswap.app.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class BotControlUiState(
    val isLoading: Boolean = false,
    val status: BotStatus? = null,
    val config: BotConfig? = null,
    val activeTokens: List<TokenTradeInfo> = emptyList(),
    val statistics: TradingStatistics? = null,
    val error: String? = null,
    val manualTradeToken: String = "",
    val showConfigDialog: Boolean = false
)

class BotControlViewModel(private val botApi: BotApi) : ViewModel() {
    private val _uiState = MutableStateFlow(BotControlUiState())
    val uiState: StateFlow<BotControlUiState> = _uiState.asStateFlow()
    
    init {
        startPeriodicUpdates()
        refreshData()
    }
    
    private fun startPeriodicUpdates() {
        viewModelScope.launch {
            while (true) {
                refreshStatus()
                delay(5000) // Update every 5 seconds
            }
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val statusResponse = botApi.getBotStatus()
                val configResponse = botApi.getBotConfig()
                val tokensResponse = botApi.getActiveTokens()
                val statsResponse = botApi.getTradingStatistics()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    status = statusResponse.data,
                    config = configResponse.data,
                    activeTokens = tokensResponse.data ?: emptyList(),
                    statistics = statsResponse.data,
                    error = if (!statusResponse.success) statusResponse.message else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun refreshStatus() {
        try {
            val statusResponse = botApi.getBotStatus()
            val tokensResponse = botApi.getActiveTokens()
            
            _uiState.value = _uiState.value.copy(
                status = statusResponse.data,
                activeTokens = tokensResponse.data ?: emptyList()
            )
        } catch (e: Exception) {
            // Silently handle periodic update errors
        }
    }
    
    fun startBot() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = botApi.startBot()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    status = response.data,
                    error = if (!response.success) response.message else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to start bot: ${e.message}"
                )
            }
        }
    }
    
    fun stopBot() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = botApi.stopBot()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    status = response.data,
                    error = if (!response.success) response.message else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to stop bot: ${e.message}"
                )
            }
        }
    }
    
    fun executeManualTrade(tokenAddress: String, action: String) {
        viewModelScope.launch {
            try {
                val response = botApi.executeManualTrade(
                    ManualTradeRequest(tokenAddress, action)
                )
                if (!response.success) {
                    _uiState.value = _uiState.value.copy(error = response.message)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to execute trade: ${e.message}"
                )
            }
        }
    }
    
    fun updateManualTradeToken(token: String) {
        _uiState.value = _uiState.value.copy(manualTradeToken = token)
    }
    
    fun showConfigDialog() {
        _uiState.value = _uiState.value.copy(showConfigDialog = true)
    }
    
    fun hideConfigDialog() {
        _uiState.value = _uiState.value.copy(showConfigDialog = false)
    }
    
    fun updateConfig(config: BotConfig) {
        viewModelScope.launch {
            try {
                val response = botApi.updateBotConfig(config)
                _uiState.value = _uiState.value.copy(
                    config = response.data,
                    showConfigDialog = false,
                    error = if (!response.success) response.message else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update config: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotControlScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: BotControlViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        containerColor = Color(0xFF1A1A1A),
        topBar = {
            TopAppBar(
                title = { Text("Trading Bot Control", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.showConfigDialog() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF262626)
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            item {
                StatusCard(
                    status = uiState.status,
                    isLoading = uiState.isLoading,
                    onStartBot = { viewModel.startBot() },
                    onStopBot = { viewModel.stopBot() }
                )
            }
            
            // Statistics Card
            item {
                StatisticsCard(statistics = uiState.statistics)
            }
            
            // Manual Trade Card
            item {
                ManualTradeCard(
                    tokenAddress = uiState.manualTradeToken,
                    onTokenChange = { viewModel.updateManualTradeToken(it) },
                    onBuy = { viewModel.executeManualTrade(it, "buy") },
                    onSell = { viewModel.executeManualTrade(it, "sell") }
                )
            }
            
            // Active Tokens
            item {
                Text(
                    text = "Active Tokens (${uiState.activeTokens.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(uiState.activeTokens) { token ->
                TokenTradeCard(token = token)
            }
        }
    }
    
    // Error Snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error for 3 seconds then clear
            delay(3000)
            viewModel.clearError()
        }
    }
    
    // Configuration Dialog
    if (uiState.showConfigDialog) {
        uiState.config?.let { config ->
            BotConfigDialog(
                config = config,
                onDismiss = { viewModel.hideConfigDialog() },
                onSave = { viewModel.updateConfig(it) }
            )
        }
    }
}

@Composable
private fun StatusCard(
    status: BotStatus?,
    isLoading: Boolean,
    onStartBot: () -> Unit,
    onStopBot: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262626))
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
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (status != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusIndicator(
                        label = "Status",
                        value = if (status.isRunning) "Running" else "Stopped",
                        color = if (status.isRunning) Color.Green else Color.Red
                    )
                    StatusIndicator(
                        label = "Uptime",
                        value = formatUptime(status.uptime),
                        color = Color.White
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusIndicator(
                        label = "Active Tokens",
                        value = status.activeTokens.toString(),
                        color = Color.White
                    )
                    StatusIndicator(
                        label = "Total Trades",
                        value = status.totalTrades.toString(),
                        color = Color.White
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = if (status.isRunning) onStopBot else onStartBot,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (status.isRunning) Color.Red else Color.Green
                        )
                    ) {
                        Icon(
                            imageVector = if (status.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (status.isRunning) "Stop Bot" else "Start Bot")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFADADAD)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatisticsCard(statistics: TradingStatistics?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262626))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Trading Statistics",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            if (statistics != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusIndicator("Win Rate", "${(statistics.winRate * 100).toInt()}%", Color.White)
                    StatusIndicator("P&L", statistics.profitLoss, Color.White)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusIndicator("Volume", statistics.totalVolume, Color.White)
                    StatusIndicator("Avg Trade", statistics.averageTradeSize, Color.White)
                }
            }
        }
    }
}

@Composable
private fun ManualTradeCard(
    tokenAddress: String,
    onTokenChange: (String) -> Unit,
    onBuy: (String) -> Unit,
    onSell: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262626))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Manual Trading",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedTextField(
                value = tokenAddress,
                onValueChange = onTokenChange,
                label = { Text("Token Address") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color(0xFFADADAD)
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onBuy(tokenAddress) },
                    enabled = tokenAddress.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buy")
                }
                
                Button(
                    onClick = { onSell(tokenAddress) },
                    enabled = tokenAddress.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sell")
                }
            }
        }
    }
}

@Composable
private fun TokenTradeCard(token: TokenTradeInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262626))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = token.symbol ?: token.tokenAddress.take(8) + "...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = token.tokenAddress.take(16) + "...",
                    color = Color(0xFFADADAD),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .background(
                            color = when (token.state) {
                                "TradePending" -> Color.Yellow
                                "Swapped" -> Color.Green
                                "Selling" -> Color(0xFFFFA500)
                                "Sold" -> Color.Blue
                                else -> Color.Red
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = token.state,
                        color = Color.Black,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                token.balance?.let { balance ->
                    Text(
                        text = balance,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun BotConfigDialog(
    config: BotConfig,
    onDismiss: () -> Unit,
    onSave: (BotConfig) -> Unit
) {
    var editableConfig by remember { mutableStateOf(config) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bot Configuration") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = editableConfig.solAmountToTrade,
                        onValueChange = { editableConfig = editableConfig.copy(solAmountToTrade = it) },
                        label = { Text("SOL Amount to Trade") }
                    )
                }
                
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = editableConfig.autoSellAllSpl,
                            onCheckedChange = { editableConfig = editableConfig.copy(autoSellAllSpl = it) }
                        )
                        Text("Auto Sell All SPL Tokens")
                    }
                }
                
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = editableConfig.useJito,
                            onCheckedChange = { editableConfig = editableConfig.copy(useJito = it) }
                        )
                        Text("Use Jito Bundles")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(editableConfig) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatUptime(uptimeMs: Long): String {
    val seconds = (uptimeMs / 1000) % 60
    val minutes = (uptimeMs / (1000 * 60)) % 60
    val hours = (uptimeMs / (1000 * 60 * 60)) % 24
    val days = uptimeMs / (1000 * 60 * 60 * 24)
    
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}