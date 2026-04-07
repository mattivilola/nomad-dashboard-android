package com.iloapps.nomaddashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.iloapps.nomaddashboard.review.ScreenshotReviewActivity
import com.iloapps.nomaddashboard.review.ScreenshotReviewScreen
import com.iloapps.nomaddashboard.review.ScreenshotReviewTheme
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
        ScreenshotReviewTheme.entries.forEach { theme ->
            composeTestRule.runOnUiThread {
                composeTestRule.activity.showTheme(theme)
                composeTestRule.activity.showScreen(screen)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(screen.rootTag).assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val screenshotPath = "${ScreenshotReviewScreen.SharedExportDirectory}/${screen.fileName(theme)}"
            device.executeShellCommand("mkdir -p ${ScreenshotReviewScreen.SharedExportDirectory}")
            device.executeShellCommand("rm -f $screenshotPath")
            android.util.Log.i("ScreenshotReview", "Capturing ${screen.routeName}-${theme.routeName} -> $screenshotPath")
            assertTrue(
                "Expected screenshot capture to succeed for ${screen.routeName}-${theme.routeName}",
                device.executeShellCommand("screencap -p $screenshotPath").isBlank(),
            )
            val statOutput = device.executeShellCommand("stat -c %s $screenshotPath").trim()
            assertTrue(
                "Expected screenshot file to exist for ${screen.routeName}-${theme.routeName}",
                statOutput.toLongOrNull()?.let { it > 0L } == true,
            )
            android.util.Log.i("ScreenshotReview", "Saved ${screen.fileName(theme)}")
        }
    }
}
