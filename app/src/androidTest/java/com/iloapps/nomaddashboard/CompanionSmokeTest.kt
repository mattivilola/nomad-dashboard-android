package com.iloapps.nomaddashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

/**
 * Entry smoke only: the Companion route shares real app storage, so this avoids persisting test
 * essentials or planner values into the connected test device.
 */
class CompanionSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboard_companion_entry_opens_local_first_tools() {
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Open Companion"))
        composeTestRule.onNodeWithText("Open Companion").performClick()

        composeTestRule.onNodeWithText("Companion").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Save essential"))
        composeTestRule.onNodeWithText("Save essential").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Home IANA zone"))
        composeTestRule.onNodeWithText("Home IANA zone").assertIsDisplayed()
    }
}
