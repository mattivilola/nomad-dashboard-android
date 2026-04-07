package com.iloapps.nomaddashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.iloapps.nomaddashboard.review.ScreenshotReviewActivity
import com.iloapps.nomaddashboard.review.ScreenshotReviewScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotReviewTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ScreenshotReviewActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun capture_dashboard_phone() {
        capture(ScreenshotReviewScreen.Dashboard)
    }

    @Test
    fun capture_settings_phone() {
        capture(ScreenshotReviewScreen.Settings)
    }

    @Test
    fun capture_visited_phone() {
        capture(ScreenshotReviewScreen.Visited)
    }

    @Test
    fun capture_timetracking_phone() {
        capture(ScreenshotReviewScreen.TimeTracking)
    }

    @Test
    fun capture_about_phone() {
        capture(ScreenshotReviewScreen.About)
    }

    private fun capture(screen: ScreenshotReviewScreen) {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.showScreen(screen)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(screen.rootTag).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val screenshotFile = composeTestRule.activity.screenshotOutputFile(screen.fileName)
        assertTrue(
            "Expected screenshot capture to succeed for ${screen.routeName}",
            device.takeScreenshot(screenshotFile),
        )
    }
}
