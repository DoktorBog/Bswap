package com.bswap.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bswap.ui.TrianglesBackground
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bswap.navigation.NavKey
import com.bswap.navigation.push
import com.bswap.ui.UiButton

private data class Slide(val title: String)

private val slides = listOf(
    Slide("Trade SPL tokens easily"),
    Slide("Secure your funds"),
    Slide("Open source wallet")
)

@Composable
fun OnboardingWelcomeScreen(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(NavKey.Welcome::class.simpleName!!)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val slide = slides[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                TrianglesBackground(modifier = Modifier.matchParentSize())
                Text(slide.title, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            UiButton(text = "Create Wallet", onClick = { backStack.push(NavKey.GenerateSeed) }, modifier = Modifier.weight(1f))
            UiButton(text = "Import Wallet", onClick = { backStack.push(NavKey.ImportWallet) }, secondary = true, modifier = Modifier.weight(1f))
        }
    }
}
