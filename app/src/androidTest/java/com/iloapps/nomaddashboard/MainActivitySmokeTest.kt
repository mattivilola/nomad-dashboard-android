package com.iloapps.nomaddashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_shows_the_dashboard() {
        assertDashboardVisible()
    }

    @Test
    fun top_level_navigation_exposes_all_routes() {
        assertDashboardVisible()
        composeTestRule.onNodeWithTag("nav-dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-visited").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-timetracking").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav-about").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav-settings").performClick()
        assertSettingsVisible()
        composeTestRule.onNodeWithTag("nav-visited").performClick()
        assertVisitedVisible()
        composeTestRule.onNodeWithTag("nav-timetracking").performClick()
        assertTimeTrackingVisible()
        composeTestRule.onNodeWithTag("nav-about").performClick()
        assertAboutVisible()
        composeTestRule.onNodeWithTag("nav-dashboard").performClick()
        assertDashboardVisible()
    }

    @Test
    fun tracking_route_shows_disabled_guidance_by_default() {
        composeTestRule.onNodeWithTag("nav-timetracking").performClick()

        composeTestRule.onNodeWithText("Project time tracking is off").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Enable time tracking in Settings to start continuous local capture and dashboard allocation."))
        composeTestRule.onNodeWithText("Enable time tracking in Settings to start continuous local capture and dashboard allocation.").assertIsDisplayed()
    }

    /** The dashboard opens on its header and the verdict strip; the old app-name title is gone on purpose. */
    private fun assertDashboardVisible() {
        composeTestRule.onNodeWithTag("dashboard_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_glance_verdict").assertIsDisplayed()
    }

    private fun assertSettingsVisible() {
        // A phone restores the last settings page; either the page list or a page's detail shows Settings.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("settings_page_list").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithTag("settings_page_detail").fetchSemanticsNodes().isNotEmpty()
        }
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
