package com.bswap.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bswap.navigation.push
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.PrimaryButton
import com.bswap.ui.UiTheme

/**
 * First onboarding screen with a welcome message and start button.
 *
 * @param backStack navigation back stack
 */
@Composable
fun OnboardingWelcomeScreen(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .testTag(NavKey.Welcome::class.simpleName!!),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Bswap", style = MaterialTheme.typography.headlineMedium)
        PrimaryButton(text = "\u041d\u0430\u0447\u0430\u0442\u044c", onClick = { backStack.push(NavKey.ChoosePath) }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "OnboardingWelcomeScreen", device = "id:pixel_4", showBackground = true)
@Composable
private fun OnboardingWelcomeScreenPreview() {
    UiTheme {
        OnboardingWelcomeScreen(rememberBackStack())
    }
}
