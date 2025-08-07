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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.bswap.ui.TrianglesBackground
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bswap.navigation.NavKey
import com.bswap.navigation.push
import com.bswap.ui.*

private data class Slide(val title: String, val subtitle: String)

private val slides = listOf(
    Slide("Automated Solana Trading", "Let our advanced bot handle your trading automatically with intelligent algorithms"),
    Slide("Advanced Bot Controls", "Full control over your trading strategy with customizable parameters and settings"),
    Slide("Real-time Analytics", "Monitor your performance with detailed analytics and real-time market insights")
)

@Composable
fun OnboardingWelcomeScreen(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    
    ModernBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag(NavKey.Welcome::class.simpleName!!)
        ) {
            // Modern Header
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "BSWAP",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Professional Trading Bot",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            
            // Content Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlowCard(
                        glowColor = MaterialTheme.colorScheme.primary
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = slide.title,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = slide.subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            // Bottom Actions
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                GradientButton(
                    text = "Start Bot Control",
                    onClick = { backStack.push(NavKey.BotDashboard) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.PlayArrow
                )
                Text(
                    text = "Start controlling your trading bot with professional-grade tools",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
