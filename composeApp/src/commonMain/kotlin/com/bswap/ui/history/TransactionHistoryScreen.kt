package com.bswap.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bswap.app.Strings
import com.bswap.app.models.WalletViewModel
import com.bswap.ui.ModernBackground
import com.bswap.ui.PrimaryButton
import com.bswap.ui.tx.TransactionRow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(publicKey: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: WalletViewModel = koinViewModel(parameters = { parametersOf(publicKey) })
    val history by vm.history.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    ModernBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = Strings.back)
                        }
                    },
                    title = { Text("Transaction History") },
                    actions = {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        ) { inner ->
            
            when {
                isLoading && history.isEmpty() -> {
                    // Initial loading state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading transactions...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                
                history.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "No Transactions",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Transactions will appear here once your wallet becomes active",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                else -> {
                    // Transaction list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(history) { tx ->
                            TransactionRow(tx = tx)
                        }
                        
                        // Load More button
                        item {
                            if (isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PrimaryButton(
                                        text = "Load More",
                                        onClick = { vm.loadMore() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
