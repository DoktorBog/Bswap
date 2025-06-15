package com.bswap.ui.seed

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiButton
import com.bswap.ui.UiTheme
import kotlinx.coroutines.delay

/**
 * Dialog showing the seed phrase after a hold gesture.
 * Content expands with animation when revealed.
 */
@Composable
fun SeedRevealDialog(
    seed: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var revealed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = modifier.testTag("SeedRevealDialog"),
        text = {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.animateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (revealed) {
                        Text(seed, fontWeight = FontWeight.Bold)
                    } else {
                        UiButton(text = "Hold to reveal", onClick = {})
                    }
                }
            }
        }
    )
    LaunchedEffect(Unit) {
        delay(1500)
        revealed = true
    }
}

@Preview(name = "SeedRevealDialog", device = "id:pixel_4", showBackground = true)
@Composable
private fun SeedRevealDialogPreview() {
    UiTheme {
        SeedRevealDialog(seed = "one two three four", onDismiss = {})
    }
}
