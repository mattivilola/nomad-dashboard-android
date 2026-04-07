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
    fun top_level_navigation_opens_all_routes() {
        assertDashboardVisible()

        openTopLevelDestination("Settings")
        assertSettingsVisible()

        openTopLevelDestination("Visited")
        assertVisitedVisible()

        openTopLevelDestination("Tracking")
        assertTimeTrackingVisible()

        openTopLevelDestination("About")
        assertAboutVisible()

        openTopLevelDestination("Dashboard")
        assertDashboardVisible()
    }

    @Test
    fun dashboard_renders_stable_shell_content() {
        composeTestRule.onNodeWithText("Nomad Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Power").assertIsDisplayed()
        composeTestRule.onNodeWithText("Weather").assertIsDisplayed()
    }

    private fun openTopLevelDestination(label: String) {
        composeTestRule
            .onNode(hasText(label) and hasClickAction(), useUnmergedTree = true)
            .performClick()
    }

    private fun assertDashboardVisible() {
        composeTestRule.onNodeWithText("Nomad Dashboard").assertIsDisplayed()
    }

    private fun assertSettingsVisible() {
        composeTestRule.onNodeWithText("Manage Android-first behavior while preserving macOS parity goals.").assertIsDisplayed()
    }

    private fun assertVisitedVisible() {
        composeTestRule.onNodeWithText("Visited Map").assertIsDisplayed()
    }

    private fun assertTimeTrackingVisible() {
        composeTestRule.onNodeWithText("Time Tracking").assertIsDisplayed()
    }

    private fun assertAboutVisible() {
        composeTestRule.onNodeWithText("Nomad Dashboard for Android").assertIsDisplayed()
    }
}
