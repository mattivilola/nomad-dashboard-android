package com.iloapps.nomaddashboard

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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

    @Test
    fun tankerkoenig_api_key_persists_across_recreate_and_can_be_cleared() {
        openSettings()

        composeTestRule.onNodeWithTag(TankerkoenigApiKeyFieldTag).performTextClearance()
        composeTestRule.onNodeWithTag(TankerkoenigApiKeyFieldTag).performTextInput("test-key-123")
        composeTestRule.onNodeWithTag(TankerkoenigApiKeySaveButtonTag).performClick()

        composeTestRule.activityRule.scenario.recreate()
        openSettings()
        composeTestRule.onNodeWithTag(TankerkoenigApiKeyFieldTag).assertTextContains("test-key-123")

        composeTestRule.onNodeWithText("Clear key").performClick()
        composeTestRule.onNodeWithTag(TankerkoenigApiKeyFieldTag).assertTextContains("")
    }

    @Test
    fun reliefweb_app_name_persists_across_recreate_and_can_be_cleared() {
        openSettings()

        composeTestRule.onNodeWithTag(ReliefWebAppNameFieldTag).performTextClearance()
        composeTestRule.onNodeWithTag(ReliefWebAppNameFieldTag).performTextInput("nomad-approved-app")
        composeTestRule.onNodeWithTag(ReliefWebAppNameSaveButtonTag).performClick()

        composeTestRule.activityRule.scenario.recreate()
        openSettings()
        composeTestRule.onNodeWithTag(ReliefWebAppNameFieldTag).assertTextContains("nomad-approved-app")

        composeTestRule.onNodeWithText("Clear app name").performClick()
        composeTestRule.onNodeWithTag(ReliefWebAppNameFieldTag).assertTextContains("")
    }

    private fun openSettings() {
        composeTestRule
            .onNodeWithTag("nav-settings")
            .performClick()
        composeTestRule.onNodeWithTag(ExpandWeatherForecastSwitchTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TankerkoenigApiKeyFieldTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReliefWebAppNameFieldTag).assertIsDisplayed()
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
        const val ReliefWebAppNameFieldTag = "settings_reliefweb_app_name_field"
        const val ReliefWebAppNameSaveButtonTag = "settings_reliefweb_app_name_save_button"
        const val TankerkoenigApiKeyFieldTag = "settings_tankerkoenig_api_key_field"
        const val TankerkoenigApiKeySaveButtonTag = "settings_tankerkoenig_api_key_save_button"
    }
}
