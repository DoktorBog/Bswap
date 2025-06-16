package com.bswap.ui.seed

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import com.bswap.app.MainActivity
import com.bswap.navigation.rememberBackStack
import org.junit.Rule
import org.junit.Test

class ConfirmSeedScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chipsSpacingAndButtonState() {
        val seed = listOf("one","two","three")
        composeTestRule.setContent {
            ConfirmSeedScreen(seed, rememberBackStack())
        }
        composeTestRule.onNodeWithText("Confirm").assertIsNotEnabled()
        composeTestRule.onAllNodesWithTag("SeedWordChip")[0].performClick()
        composeTestRule.onAllNodesWithTag("SeedWordChip")[1].performClick()
        composeTestRule.onAllNodesWithTag("SeedWordChip")[2].performClick()
        composeTestRule.onNodeWithText("Confirm").assertIsEnabled()
    }
}
