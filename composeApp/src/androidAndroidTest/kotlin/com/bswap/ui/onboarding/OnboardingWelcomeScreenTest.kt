package com.bswap.ui.onboarding

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bswap.app.MainActivity
import com.bswap.navigation.BswapNavHost
import com.bswap.navigation.NavKey
import com.bswap.navigation.rememberBackStack
import org.junit.Rule
import org.junit.Test

class OnboardingWelcomeScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun buttonsNavigate() {
        rule.setContent {
            val backStack = rememberBackStack(NavKey.Welcome)
            BswapNavHost(backStack)
        }
        rule.onNodeWithText("Create Wallet").performClick()
        rule.waitUntil { rule.onNodeWithText("Next").fetchSemanticsNodes().isNotEmpty() }
    }
}
