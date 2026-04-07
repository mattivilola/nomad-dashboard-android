package com.iloapps.nomaddashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        composeTestRule.onNodeWithTag("nav-dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-visited").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-timetracking").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-about").assertIsDisplayed()
    }

    @Test
    fun tracking_route_shows_disabled_guidance_by_default() {
        composeTestRule.onNodeWithTag("nav-timetracking").performClick()

        composeTestRule.onNodeWithText("Project time tracking is off").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable project time tracking in Settings to unlock local project capture, foreground tracking, and the persistent notification.").assertIsDisplayed()
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
