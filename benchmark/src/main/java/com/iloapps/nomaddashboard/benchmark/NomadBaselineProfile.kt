package com.iloapps.nomaddashboard.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class NomadBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndPrimaryNavigation() = baselineProfileRule.collect(
        packageName = "com.iloapps.nomaddashboard",
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Dashboard")), 5_000)
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 4, 20)
        for (label in listOf("Settings", "Visited", "Tracking", "About", "Dashboard")) {
            val destination = device.wait(Until.findObject(By.text(label)), 5_000)
                ?: error("$label navigation was not rendered.")
            destination.click()
            device.waitForIdle()
        }
    }
}
