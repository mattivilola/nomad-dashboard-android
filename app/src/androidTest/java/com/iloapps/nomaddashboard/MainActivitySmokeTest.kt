package com.iloapps.nomaddashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_shows_dashboard_title() {
        composeTestRule.onNodeWithText("Nomad Dashboard").assertIsDisplayed()
    }

    @Test
    fun top_level_navigation_exposes_all_routes() {
        assertDashboardVisible()
        composeTestRule.onNode(hasText("Dashboard") and hasClickAction(), useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNode(hasText("Settings") and hasClickAction(), useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNode(hasText("Visited") and hasClickAction(), useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNode(hasText("Tracking") and hasClickAction(), useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNode(hasText("About") and hasClickAction(), useUnmergedTree = true).assertIsDisplayed()
    }

    private fun assertDashboardVisible() {
        composeTestRule.onNodeWithText("Nomad Dashboard").assertIsDisplayed()
    }

    private fun assertSettingsVisible() {
        composeTestRule.onNodeWithText("Manage Android-first behavior while preserving macOS parity goals.").assertIsDisplayed()
    }

    private fun assertVisitedVisible() {
        composeTestRule.onNodeWithText("Visited Places").assertIsDisplayed()
    }

    private fun assertTimeTrackingVisible() {
        composeTestRule.onNodeWithText("Time Tracking").assertIsDisplayed()
    }

    private fun assertAboutVisible() {
        composeTestRule.onNodeWithText("Nomad Dashboard for Android").assertIsDisplayed()
    }
}
