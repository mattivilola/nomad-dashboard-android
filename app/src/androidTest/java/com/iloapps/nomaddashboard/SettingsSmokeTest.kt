package com.iloapps.nomaddashboard

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class SettingsSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun expand_weather_forecast_toggle_persists_across_recreate_and_is_restored() {
        openSettings()

        val initialState = toggleState()
        try {
            toggleExpandWeatherForecast()
            waitForToggleState(initialState.opposite())
            assertToggleState(initialState.opposite())

            composeTestRule.activityRule.scenario.recreate()
            openSettings()
            waitForToggleState(initialState.opposite())
            assertToggleState(initialState.opposite())

            toggleExpandWeatherForecast()
            waitForToggleState(initialState)
            assertToggleState(initialState)
        } finally {
            openSettings()
            if (runCatching { toggleState() }.getOrNull() != initialState) {
                toggleExpandWeatherForecast()
                waitForToggleState(initialState)
            }
        }
    }

    private fun openSettings() {
        composeTestRule
            .onNode(hasText("Settings") and hasClickAction(), useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithTag(ExpandWeatherForecastSwitchTag).assertIsDisplayed()
    }

    private fun toggleExpandWeatherForecast() {
        composeTestRule.onNodeWithTag(ExpandWeatherForecastSwitchTag).performClick()
    }

    private fun waitForToggleState(expected: ToggleableState) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                toggleState() == expected
            }.getOrDefault(false)
        }
    }

    private fun assertToggleState(expected: ToggleableState) {
        when (expected) {
            ToggleableState.On -> composeTestRule.onNodeWithTag(ExpandWeatherForecastSwitchTag).assertIsOn()
            ToggleableState.Off -> composeTestRule.onNodeWithTag(ExpandWeatherForecastSwitchTag).assertIsOff()
            ToggleableState.Indeterminate -> error("Unexpected indeterminate switch state.")
        }
    }

    private fun toggleState(): ToggleableState =
        composeTestRule
            .onNodeWithTag(ExpandWeatherForecastSwitchTag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ToggleableState]

    private fun ToggleableState.opposite(): ToggleableState = when (this) {
        ToggleableState.On -> ToggleableState.Off
        ToggleableState.Off -> ToggleableState.On
        ToggleableState.Indeterminate -> error("Unexpected indeterminate switch state.")
    }

    private companion object {
        const val ExpandWeatherForecastSwitchTag = "settings_expand_weather_forecast_switch"
    }
}
