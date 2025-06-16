package com.bswap.ui.seed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import com.bswap.app.interactor.walletInteractor
import com.bswap.data.seedStorage
import com.bswap.navigation.NavKey
import com.bswap.navigation.replaceAll
import kotlinx.coroutines.launch

/**
 * Screen for confirming seed phrase order via chip selection.
 */
@Composable
fun ConfirmSeedScreen(
    words: List<String>,
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val available = remember { mutableStateListOf(*words.shuffled().toTypedArray()) }
    val selected = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val interactor = remember { walletInteractor() }

    val enabled = selected.size == words.size && selected == words

    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(NavKey.ConfirmSeed::class.simpleName!!),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            selected.forEach { word ->
                SeedWordChip(
                    word = word,
                    selected = true,
                    onClick = {},
                    enabled = false
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            available.forEach { word ->
                SeedWordChip(
                    word = word,
                    selected = false,
                    onClick = {
                        available.remove(word)
                        selected.add(word)
                    },
                    modifier = Modifier,
                )
            }
        }
        FilledTonalButton(
            onClick = {
                scope.launch {
                    val keypair = interactor.createWallet(selected)
                    seedStorage().savePublicKey(keypair.publicKey.toBase58())
                    backStack.replaceAll(NavKey.WalletHome(keypair.publicKey.toBase58()))
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm")
        }
    }
}
