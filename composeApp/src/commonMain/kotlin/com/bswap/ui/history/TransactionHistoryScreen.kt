package com.bswap.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.bswap.app.Strings
import com.bswap.app.models.WalletViewModel
import com.bswap.ui.TrianglesBackground
import com.bswap.ui.tx.TransactionRow
import org.koin.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(publicKey: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: WalletViewModel = koinViewModel(parameters = { parametersOf(publicKey) })
    val history by vm.history.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = Strings.back)
                    }
                },
                title = { Text(Strings.history) }
            )
        }
    ) { inner ->
        Box(modifier = modifier.fillMaxSize()) {
            TrianglesBackground(modifier = Modifier.matchParentSize())
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(inner)) {
                items(history) { tx ->
                    TransactionRow(tx = tx)
                }
            }
        }
    }
}
