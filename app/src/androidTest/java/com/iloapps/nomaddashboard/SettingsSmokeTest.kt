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
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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

        scrollToTag(TankerkoenigApiKeyFieldTag).performTextClearance()
        scrollToTag(TankerkoenigApiKeyFieldTag).performTextInput("test-key-123")
        scrollToTag(TankerkoenigApiKeySaveButtonTag).performClick()

        composeTestRule.activityRule.scenario.recreate()
        openSettings()
        waitForCredentials { it.tankerkoenigApiKey == "test-key-123" }

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Clear key"))
        composeTestRule.onNodeWithText("Clear key").performClick()
        waitForCredentials { it.tankerkoenigApiKey.isBlank() }
    }

    @Test
    fun reliefweb_app_name_persists_across_recreate_and_can_be_cleared() {
        openSettings()

        scrollToTag(ReliefWebAppNameFieldTag).performTextClearance()
        scrollToTag(ReliefWebAppNameFieldTag).performTextInput("nomad-approved-app")
        scrollToTag(ReliefWebAppNameSaveButtonTag).performClick()

        composeTestRule.activityRule.scenario.recreate()
        openSettings()
        waitForCredentials { it.reliefWebAppName == "nomad-approved-app" }

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Clear app name"))
        composeTestRule.onNodeWithText("Clear app name").performClick()
        waitForCredentials { it.reliefWebAppName.isBlank() }
    }

    private fun waitForCredentials(matches: (com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings) -> Boolean) {
        // Password semantics intentionally mask text. Verify the repository value
        // after recreation rather than expecting secrets in the rendered tree.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            matches(runBlocking { composeTestRule.activity.dashboardRepository.providerCredentials.first() })
        }
    }

    private fun openSettings() {
        composeTestRule
            .onNodeWithTag("nav-settings")
            .performClick()
        scrollToTag(ExpandWeatherForecastSwitchTag).assertIsDisplayed()

    }

    private fun scrollToTag(tag: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        return composeTestRule.onNodeWithTag(tag)
    }

    private fun toggleExpandWeatherForecast() {
        scrollToTag(ExpandWeatherForecastSwitchTag).performClick()
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
            ToggleableState.On -> scrollToTag(ExpandWeatherForecastSwitchTag).assertIsOn()
            ToggleableState.Off -> scrollToTag(ExpandWeatherForecastSwitchTag).assertIsOff()
            ToggleableState.Indeterminate -> error("Unexpected indeterminate switch state.")
        }
    }

    private fun toggleState(): ToggleableState =
        scrollToTag(ExpandWeatherForecastSwitchTag)
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
