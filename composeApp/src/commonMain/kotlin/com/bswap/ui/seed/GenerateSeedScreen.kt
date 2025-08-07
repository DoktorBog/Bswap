package com.bswap.ui.seed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.push
import com.bswap.ui.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.WalletTheme
import com.bswap.app.copyToClipboard
import com.bswap.seed.SeedUtils
import com.bswap.data.seedStorage
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Screen displaying generated seed phrase.
 *
 * @param backStack navigation back stack
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateSeedScreen(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val seedWords = remember { SeedUtils.generateSeed() }
    LaunchedEffect(Unit) { seedStorage().saveSeed(seedWords) }
    var useBiometrics by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag("GenerateSeed"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    seedWords.forEachIndexed { index, word ->
                        SeedWordChip(text = "${index + 1}. $word", onClick = {}, enabled = false)
                    }
                }
            }
            FloatingActionButton(
                onClick = { copyToClipboard(seedWords.joinToString(" ")) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable biometrics")
            Switch(checked = useBiometrics, onCheckedChange = { useBiometrics = it })
        }
        UiButton(text = "Next", onClick = { backStack.push(NavKey.BotDashboard) }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview
@Composable
private fun GenerateSeedScreenPreview() {
    WalletTheme {
        GenerateSeedScreen(rememberBackStack())
    }
}
