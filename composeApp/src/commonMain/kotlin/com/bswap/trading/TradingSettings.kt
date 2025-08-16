package com.bswap.trading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bswap.shared.trading.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingSettings(
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Trading Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Exchange") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Risk Management") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Notifications") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Advanced") }
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Tab Content
        when (selectedTab) {
            0 -> ExchangeSettings(apiClient = apiClient)
            1 -> RiskManagementSettings()
            2 -> NotificationSettings()
            3 -> AdvancedSettings()
        }
    }
}

@Composable
fun ExchangeSettings(
    apiClient: TradingApiClient,
    modifier: Modifier = Modifier
) {
    var currentExchange by remember { mutableStateOf("HYPERLIQUID") }
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var walletAddress by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var testnet by remember { mutableStateOf(true) }
    var showSecrets by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Load current exchange
    LaunchedEffect(Unit) {
        apiClient.getCurrentExchange().onSuccess { exchange ->
            currentExchange = exchange
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Exchange Selection
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Exchange Selection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentExchange == "SOLANA",
                        onClick = { 
                            currentExchange = "SOLANA"
                            scope.launch {
                                apiClient.switchExchange("SOLANA")
                            }
                        },
                        label = { Text("Solana DEX") },
                        leadingIcon = {
                            Icon(Icons.Default.AccountBalance, contentDescription = null)
                        }
                    )
                    FilterChip(
                        selected = currentExchange == "HYPERLIQUID",
                        onClick = { 
                            currentExchange = "HYPERLIQUID"
                            scope.launch {
                                apiClient.switchExchange("HYPERLIQUID")
                            }
                        },
                        label = { Text("Hyperliquid") },
                        leadingIcon = {
                            Icon(Icons.Default.TrendingUp, contentDescription = null)
                        }
                    )
                }
                
                Text(
                    text = "Current: $currentExchange",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Hyperliquid Configuration
        if (currentExchange == "HYPERLIQUID") {
            Card {
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
                            text = "Hyperliquid Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = testnet,
                                onCheckedChange = { testnet = it }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Testnet")
                        }
                    }
                    
                    // API Credentials
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("Enter your Hyperliquid API key") },
                        visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecrets = !showSecrets }) {
                                Icon(
                                    if (showSecrets) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showSecrets) "Hide" else "Show"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = apiSecret,
                        onValueChange = { apiSecret = it },
                        label = { Text("API Secret") },
                        placeholder = { Text("Enter your Hyperliquid API secret") },
                        visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = walletAddress,
                        onValueChange = { walletAddress = it },
                        label = { Text("Wallet Address") },
                        placeholder = { Text("0x...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text("Private Key") },
                        placeholder = { Text("Enter your Ethereum private key") },
                        visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Button(
                        onClick = {
                            // Save configuration logic would go here
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Configuration")
                    }
                }
            }
        }
        
        // Connection Status
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Connected to $currentExchange")
                }
                
                Button(
                    onClick = {
                        // Test connection logic
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Connection")
                }
            }
        }
    }
}

@Composable
fun RiskManagementSettings(
    modifier: Modifier = Modifier
) {
    var maxLeverage by remember { mutableStateOf("20.0") }
    var maxPositions by remember { mutableStateOf("10") }
    var positionSizePercent by remember { mutableStateOf("10.0") }
    var stopLossPercent by remember { mutableStateOf("2.0") }
    var takeProfitPercent by remember { mutableStateOf("5.0") }
    var trailingStopPercent by remember { mutableStateOf("1.5") }
    var autoCloseOnProfit by remember { mutableStateOf("5.0") }
    var autoCloseOnLoss by remember { mutableStateOf("2.0") }
    var enableStopLoss by remember { mutableStateOf(true) }
    var enableTakeProfit by remember { mutableStateOf(true) }
    var enableTrailingStop by remember { mutableStateOf(true) }
    var enableAutoClose by remember { mutableStateOf(true) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Position Limits
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Position Limits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = maxLeverage,
                    onValueChange = { maxLeverage = it },
                    label = { Text("Max Leverage") },
                    suffix = { Text("x") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = maxPositions,
                    onValueChange = { maxPositions = it },
                    label = { Text("Max Concurrent Positions") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = positionSizePercent,
                    onValueChange = { positionSizePercent = it },
                    label = { Text("Position Size") },
                    suffix = { Text("% of balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Stop Loss & Take Profit
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Stop Loss & Take Profit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Stop Loss")
                    Switch(
                        checked = enableStopLoss,
                        onCheckedChange = { enableStopLoss = it }
                    )
                }
                
                if (enableStopLoss) {
                    OutlinedTextField(
                        value = stopLossPercent,
                        onValueChange = { stopLossPercent = it },
                        label = { Text("Stop Loss") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Take Profit")
                    Switch(
                        checked = enableTakeProfit,
                        onCheckedChange = { enableTakeProfit = it }
                    )
                }
                
                if (enableTakeProfit) {
                    OutlinedTextField(
                        value = takeProfitPercent,
                        onValueChange = { takeProfitPercent = it },
                        label = { Text("Take Profit") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Trailing Stop")
                    Switch(
                        checked = enableTrailingStop,
                        onCheckedChange = { enableTrailingStop = it }
                    )
                }
                
                if (enableTrailingStop) {
                    OutlinedTextField(
                        value = trailingStopPercent,
                        onValueChange = { trailingStopPercent = it },
                        label = { Text("Trailing Stop") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Auto Close Settings
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Auto Close Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Auto Close")
                    Switch(
                        checked = enableAutoClose,
                        onCheckedChange = { enableAutoClose = it }
                    )
                }
                
                if (enableAutoClose) {
                    OutlinedTextField(
                        value = autoCloseOnProfit,
                        onValueChange = { autoCloseOnProfit = it },
                        label = { Text("Auto Close on Profit") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = autoCloseOnLoss,
                        onValueChange = { autoCloseOnLoss = it },
                        label = { Text("Auto Close on Loss") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Save Button
        Button(
            onClick = {
                // Save risk management settings
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Risk Settings")
        }
    }
}

@Composable
fun NotificationSettings(
    modifier: Modifier = Modifier
) {
    var enableTradeNotifications by remember { mutableStateOf(true) }
    var enablePositionAlerts by remember { mutableStateOf(true) }
    var enableBalanceAlerts by remember { mutableStateOf(false) }
    var enableSystemAlerts by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrateEnabled by remember { mutableStateOf(true) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Notification Types",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                NotificationSettingItem(
                    title = "Trade Notifications",
                    description = "Get notified when trades are executed",
                    checked = enableTradeNotifications,
                    onCheckedChange = { enableTradeNotifications = it }
                )
                
                NotificationSettingItem(
                    title = "Position Alerts",
                    description = "Alerts for position changes and PnL updates",
                    checked = enablePositionAlerts,
                    onCheckedChange = { enablePositionAlerts = it }
                )
                
                NotificationSettingItem(
                    title = "Balance Alerts",
                    description = "Notifications for significant balance changes",
                    checked = enableBalanceAlerts,
                    onCheckedChange = { enableBalanceAlerts = it }
                )
                
                NotificationSettingItem(
                    title = "System Alerts",
                    description = "Important system messages and errors",
                    checked = enableSystemAlerts,
                    onCheckedChange = { enableSystemAlerts = it }
                )
            }
        }
        
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Notification Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                NotificationSettingItem(
                    title = "Sound",
                    description = "Play sound for notifications",
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it }
                )
                
                NotificationSettingItem(
                    title = "Vibration",
                    description = "Vibrate device for notifications",
                    checked = vibrateEnabled,
                    onCheckedChange = { vibrateEnabled = it }
                )
            }
        }
        
        Button(
            onClick = {
                // Save notification settings
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Notification Settings")
        }
    }
}

@Composable
fun NotificationSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun AdvancedSettings(
    modifier: Modifier = Modifier
) {
    var enableLogging by remember { mutableStateOf(true) }
    var logLevel by remember { mutableStateOf("INFO") }
    var enableWebSocket by remember { mutableStateOf(true) }
    var updateInterval by remember { mutableStateOf("5") }
    var enableAutoTuning by remember { mutableStateOf(true) }
    var enableRateLimit by remember { mutableStateOf(true) }
    var maxRequestsPerSecond by remember { mutableStateOf("10") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Logging Settings
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Logging Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Logging")
                    Switch(
                        checked = enableLogging,
                        onCheckedChange = { enableLogging = it }
                    )
                }
                
                if (enableLogging) {
                    ExposedDropdownMenuBox(
                        expanded = false,
                        onExpandedChange = { }
                    ) {
                        OutlinedTextField(
                            value = logLevel,
                            onValueChange = { },
                            label = { Text("Log Level") },
                            readOnly = true,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        // Performance Settings
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Performance Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable WebSocket")
                    Switch(
                        checked = enableWebSocket,
                        onCheckedChange = { enableWebSocket = it }
                    )
                }
                
                OutlinedTextField(
                    value = updateInterval,
                    onValueChange = { updateInterval = it },
                    label = { Text("Update Interval") },
                    suffix = { Text("seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Auto-Tuning")
                    Switch(
                        checked = enableAutoTuning,
                        onCheckedChange = { enableAutoTuning = it }
                    )
                }
            }
        }
        
        // Rate Limiting
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Rate Limiting",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Rate Limiting")
                    Switch(
                        checked = enableRateLimit,
                        onCheckedChange = { enableRateLimit = it }
                    )
                }
                
                if (enableRateLimit) {
                    OutlinedTextField(
                        value = maxRequestsPerSecond,
                        onValueChange = { maxRequestsPerSecond = it },
                        label = { Text("Max Requests per Second") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    // Reset to defaults
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset to Defaults")
            }
            
            Button(
                onClick = {
                    // Save advanced settings
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Settings")
            }
        }
    }
}