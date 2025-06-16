package com.bswap.ui.seed

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bswap.app.models.ConfirmSeedViewModel
import com.bswap.data.seedStorage
import com.bswap.navigation.NavKey
import com.bswap.navigation.pop
import com.bswap.navigation.replaceAll
import com.bswap.seed.SeedPhraseValidator
import kotlinx.coroutines.launch

/**
 * Screen for confirming seed phrase order via chip selection.
 */
@Composable
fun ConfirmSeedScreen(
    mnemonic: List<String>,
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier,
    viewModel: ConfirmSeedViewModel = viewModel()
) {
    val available = remember { mutableStateListOf(*mnemonic.shuffled().toTypedArray()) }
    val selected = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val complete = selected.size == mnemonic.size

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Confirm your recovery phrase") },
                navigationIcon = {
                    IconButton(onClick = { backStack.pop() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp)
                .testTag(NavKey.ConfirmSeed::class.simpleName!!),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tap the words in order to confirm your recovery phrase.",
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .animateContentSize(spring())
            ) {
                selected.forEachIndexed { index, word ->
                    SeedWordChip(
                        text = "${index + 1}. $word",
                        onClick = {},
                        enabled = false
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .animateContentSize(spring())
                    .weight(1f)
            ) {
                available.forEach { word ->
                    SeedWordChip(
                        text = word,
                        onClick = {
                            available.remove(word)
                            selected.add(word)
                            viewModel.onWordPicked(word)
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        if (!SeedPhraseValidator.isValid(mnemonic, selected)) {
                            snackbarHostState.showSnackbar("Words do not match")
                        } else {
                            val keypair = seedStorage().createWallet(mnemonic)
                            seedStorage().savePublicKey(keypair.publicKey.toBase58())
                            backStack.replaceAll(NavKey.WalletHome(keypair.publicKey.toBase58()))
                        }
                    }
                },
                enabled = complete,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Confirm") }
        }
    }
}
