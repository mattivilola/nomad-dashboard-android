package com.iloapps.nomaddashboard

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test

class SettingsSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settings_lists_the_eight_pages_in_order() {
        showPageList()

        listOf(
            "GENERAL", "WEATHER_AND_LOCAL", "SAFETY", "STAYS_AND_VISAS", "CONNECTION_AND_DATA", "WORK", "PRIVACY_AND_LOCATION",
            "ADVANCED",
        ).forEach { page ->
            composeTestRule.onNodeWithTag("settings_page_list").performScrollToNode(hasTestTag("settings_page_$page"))
            composeTestRule.onNodeWithTag("settings_page_$page").assertIsDisplayed()
        }
    }

    @Test
    fun expand_weather_forecast_toggle_persists_across_recreate_and_is_restored() {
        openWeatherAndLocal()

        val initialState = toggleState()
        try {
            toggleExpandWeatherForecast()
            waitForToggleState(initialState.opposite())
            assertToggleState(initialState.opposite())

            composeTestRule.activityRule.scenario.recreate()
            openWeatherAndLocal()
            waitForToggleState(initialState.opposite())
            assertToggleState(initialState.opposite())

            toggleExpandWeatherForecast()
            waitForToggleState(initialState)
            assertToggleState(initialState)
        } finally {
            openWeatherAndLocal()
            if (runCatching { toggleState() }.getOrNull() != initialState) {
                toggleExpandWeatherForecast()
                waitForToggleState(initialState)
            }
        }
    }

    @Test
    fun tankerkoenig_api_key_persists_across_recreate_and_can_be_cleared() {
        openWeatherAndLocal()

        scrollToTag(TankerkoenigApiKeyFieldTag).performTextClearance()
        scrollToTag(TankerkoenigApiKeyFieldTag).performTextInput("test-key-123")
        scrollToTag(TankerkoenigApiKeySaveButtonTag).performClick()

        composeTestRule.activityRule.scenario.recreate()
        openWeatherAndLocal()
        waitForCredentials { it.tankerkoenigApiKey == "test-key-123" }

        pageDetail().performScrollToNode(hasText("Clear key"))
        composeTestRule.onNodeWithText("Clear key").performClick()
        waitForCredentials { it.tankerkoenigApiKey.isBlank() }
    }

    @Test
    fun reliefweb_app_name_persists_across_recreate_and_can_be_cleared() {
        openSafety()

        scrollToTag(ReliefWebAppNameFieldTag).performTextClearance()
        scrollToTag(ReliefWebAppNameFieldTag).performTextInput("nomad-approved-app")
        scrollToTag(ReliefWebAppNameSaveButtonTag).performClick()

        composeTestRule.activityRule.scenario.recreate()
        openSafety()
        waitForCredentials { it.reliefWebAppName == "nomad-approved-app" }

        pageDetail().performScrollToNode(hasText("Clear app name"))
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

    private fun openWeatherAndLocal() {
        openSettingsPage(WeatherAndLocalPageTag)
        scrollToTag(ExpandWeatherForecastSwitchTag).assertIsDisplayed()
    }

    private fun openSafety() {
        openSettingsPage(SafetyPageTag)
        scrollToTag(ReliefWebAppNameFieldTag).assertIsDisplayed()
    }

    /**
     * Opens one settings page from the Settings tab. A phone restores the page it was last on, so it
     * goes back to the page list first; side by side layouts always show the list and have no back button.
     */
    private fun openSettingsPage(pageTag: String) {
        showPageList()
        // The list is lazy: a row below the first screenful has to be scrolled to before it exists to click.
        composeTestRule.onNodeWithTag("settings_page_list").performScrollToNode(hasTestTag(pageTag))
        composeTestRule.onNodeWithTag(pageTag).performClick()
        pageDetail().assertIsDisplayed()
    }

    /**
     * Shows the page list from the Settings tab. A phone restores the page it was last on once the tab has
     * composed, so wait for either the list or that page, and go back from the page; side by side layouts
     * always show the list and have no back button.
     */
    private fun showPageList() {
        composeTestRule.onNodeWithTag("nav-settings").performClick()
        // After a recreation the screen first shows the page list for a frame, then the restored page.
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            hasNode("settings_page_list") || hasNode(SettingsPageDetailTag)
        }
        composeTestRule.waitForIdle()
        if (hasNode(SettingsPageDetailTag)) {
            // The back button is the first item of the page, and a restored page keeps its scroll position.
            pageDetail().performScrollToIndex(0)
            composeTestRule.waitUntil(timeoutMillis = 5_000) { hasNode(SettingsBackButtonTag) || hasNode("settings_page_list") }
        }
        if (hasNode(SettingsBackButtonTag)) {
            composeTestRule.onNodeWithTag(SettingsBackButtonTag).performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) { hasNode("settings_page_list") }
    }

    private fun hasNode(tag: String) = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun pageDetail() = composeTestRule.onNodeWithTag(SettingsPageDetailTag)

    private fun scrollToTag(tag: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        pageDetail().performScrollToNode(hasTestTag(tag))
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
        const val SettingsPageDetailTag = "settings_page_detail"
        const val SettingsBackButtonTag = "settings_back"
        const val WeatherAndLocalPageTag = "settings_page_WEATHER_AND_LOCAL"
        const val SafetyPageTag = "settings_page_SAFETY"
        const val ExpandWeatherForecastSwitchTag = "settings_expand_weather_forecast_switch"
        const val ReliefWebAppNameFieldTag = "settings_reliefweb_app_name_field"
        const val ReliefWebAppNameSaveButtonTag = "settings_reliefweb_app_name_save_button"
        const val TankerkoenigApiKeyFieldTag = "settings_tankerkoenig_api_key_field"
        const val TankerkoenigApiKeySaveButtonTag = "settings_tankerkoenig_api_key_save_button"
    }
}
