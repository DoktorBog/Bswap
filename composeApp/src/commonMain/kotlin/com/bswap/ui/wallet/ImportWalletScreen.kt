package com.bswap.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavKey
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.replaceAll
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import com.bswap.ui.seed.SeedInputField
import com.bswap.data.seedStorage
import com.bswap.shared.wallet.toBase58
import com.bswap.ui.TrianglesBackground
import com.bswap.ui.UiRadioButton
import kotlinx.coroutines.launch
import wallet.core.jni.CoinType

@Composable
fun ImportWalletScreen(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val (text, setText) = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val coins = listOf(CoinType.SOLANA)
    val selected = remember { mutableStateOf(CoinType.SOLANA) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("ImportWallet")
    ) {
        TrianglesBackground(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Import Wallet", style = MaterialTheme.typography.headlineMedium)
            SeedInputField(value = text, onValueChange = setText, modifier = Modifier.fillMaxWidth())
            coins.forEach { coin ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UiRadioButton(selected = selected.value == coin, onClick = { selected.value = coin })
                    Text(coin.name, modifier = Modifier.padding(start = 8.dp))
                }
            }
            UiButton(
                text = "Import",
                onClick = {
                    scope.launch {
                        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        val keypair = seedStorage().createWallet(words, coin = selected.value)
                        seedStorage().saveSeed(words)
                        seedStorage().savePublicKey(keypair.publicKey.toBase58())
                        backStack.replaceAll(NavKey.BotDashboard)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
