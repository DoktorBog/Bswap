package com.bswap.trading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bswap.shared.trading.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrderDialog(
    symbol: String,
    onDismiss: () -> Unit,
    onCreateOrder: (CreateOrderRequest) -> Unit
) {
    var side by remember { mutableStateOf("BUY") }
    var type by remember { mutableStateOf("MARKET") }
    var amount by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var timeInForce by remember { mutableStateOf("GTC") }
    var postOnly by remember { mutableStateOf(false) }
    var reduceOnly by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Create Order",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Symbol
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { },
                    label = { Text("Symbol") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Side selection
                Text(
                    text = "Side",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = side == "BUY",
                        onClick = { side = "BUY" },
                        label = { Text("BUY") }
                    )
                    FilterChip(
                        selected = side == "SELL",
                        onClick = { side = "SELL" },
                        label = { Text("SELL") }
                    )
                }
                
                // Type selection
                Text(
                    text = "Order Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == "MARKET",
                        onClick = { type = "MARKET" },
                        label = { Text("MARKET") }
                    )
                    FilterChip(
                        selected = type == "LIMIT",
                        onClick = { type = "LIMIT" },
                        label = { Text("LIMIT") }
                    )
                    FilterChip(
                        selected = type == "STOP",
                        onClick = { type = "STOP" },
                        label = { Text("STOP") }
                    )
                }
                
                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Price (only for limit orders)
                if (type == "LIMIT" || type == "STOP") {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Time in Force
                Text(
                    text = "Time in Force",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = timeInForce == "GTC",
                        onClick = { timeInForce = "GTC" },
                        label = { Text("GTC") }
                    )
                    FilterChip(
                        selected = timeInForce == "IOC",
                        onClick = { timeInForce = "IOC" },
                        label = { Text("IOC") }
                    )
                    FilterChip(
                        selected = timeInForce == "FOK",
                        onClick = { timeInForce = "FOK" },
                        label = { Text("FOK") }
                    )
                }
                
                // Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = postOnly,
                            onCheckedChange = { postOnly = it }
                        )
                        Text("Post Only")
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = reduceOnly,
                            onCheckedChange = { reduceOnly = it }
                        )
                        Text("Reduce Only")
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val request = CreateOrderRequest(
                                symbol = symbol,
                                side = side,
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                price = if (type != "MARKET") price.toDoubleOrNull() else null,
                                type = type,
                                timeInForce = timeInForce,
                                postOnly = postOnly,
                                reduceOnly = reduceOnly
                            )
                            onCreateOrder(request)
                        },
                        enabled = amount.toDoubleOrNull() != null && (type == "MARKET" || price.toDoubleOrNull() != null),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create Order")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenPositionDialog(
    symbol: String,
    onDismiss: () -> Unit,
    onOpenPosition: (OpenPositionRequest) -> Unit
) {
    var side by remember { mutableStateOf("LONG") }
    var size by remember { mutableStateOf("") }
    var leverage by remember { mutableStateOf("1.0") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Open Position",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Symbol
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { },
                    label = { Text("Symbol") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Side selection
                Text(
                    text = "Position Side",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = side == "LONG",
                        onClick = { side = "LONG" },
                        label = { Text("LONG") }
                    )
                    FilterChip(
                        selected = side == "SHORT",
                        onClick = { side = "SHORT" },
                        label = { Text("SHORT") }
                    )
                }
                
                // Size
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Position Size (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Leverage
                OutlinedTextField(
                    value = leverage,
                    onValueChange = { leverage = it },
                    label = { Text("Leverage (1x - 20x)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Leverage presets
                Text(
                    text = "Quick Leverage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1", "2", "5", "10", "20").forEach { lev ->
                        FilterChip(
                            selected = leverage == "$lev.0",
                            onClick = { leverage = "$lev.0" },
                            label = { Text("${lev}x") }
                        )
                    }
                }
                
                // Risk warning
                if ((leverage.toDoubleOrNull() ?: 1.0) > 5.0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "⚠️ High leverage increases both potential profits and losses. Trade responsibly.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val request = OpenPositionRequest(
                                symbol = symbol,
                                side = side,
                                size = size.toDoubleOrNull() ?: 0.0,
                                leverage = leverage.toDoubleOrNull() ?: 1.0
                            )
                            onOpenPosition(request)
                        },
                        enabled = size.toDoubleOrNull() != null && 
                                 leverage.toDoubleOrNull() != null &&
                                 (leverage.toDoubleOrNull() ?: 0.0) in 1.0..20.0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Position")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}