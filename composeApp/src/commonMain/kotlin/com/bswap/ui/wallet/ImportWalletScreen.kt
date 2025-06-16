package com.bswap.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.replaceAll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme
import com.bswap.ui.seed.SeedInputField
import com.bswap.app.interactor.walletInteractor
import com.bswap.data.seedStorage
import kotlinx.coroutines.launch

/**
 * Screen for manual wallet import via seed phrase.
 */
@Composable
fun ImportWalletScreen(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val (text, setText) = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val interactor = remember { walletInteractor() }
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(NavKey.ImportWallet::class.simpleName!!),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SeedInputField(value = text, onValueChange = setText, modifier = Modifier.fillMaxWidth())
        UiButton(
            text = "Import",
            onClick = {
                scope.launch {
                    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val keypair = interactor.createWallet(words)
                    seedStorage().saveSeed(words)
                    seedStorage().savePublicKey(keypair.publicKey.toBase58())
                    backStack.replaceAll(NavKey.WalletHome(keypair.publicKey.toBase58()))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun ImportWalletScreenPreview() {
    UiTheme {
        ImportWalletScreen(rememberBackStack())
    }
}
