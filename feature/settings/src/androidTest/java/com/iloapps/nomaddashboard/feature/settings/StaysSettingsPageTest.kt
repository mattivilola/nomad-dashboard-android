package com.iloapps.nomaddashboard.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StaysSettingsPageTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        page: SettingsPage?,
        settings: AppSettings = AppSettings(),
        actions: SettingsActions = SettingsActions(),
    ) {
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(
                    settings = settings,
                    navigation = SettingsNavigationState(selectedPage = page),
                ),
                actions = actions,
                isWideLayout = false,
            )
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(SettingsPageDetailTag).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun the_page_list_has_eight_pages_with_stays_and_visas_after_safety() {
        show(page = null)

        val tags = SettingsPage.entries.map(::settingsPageTag)
        assertEquals(8, tags.size)
        assertEquals(
            listOf("GENERAL", "WEATHER_AND_LOCAL", "SAFETY", "STAYS_AND_VISAS", "CONNECTION_AND_DATA", "WORK", "PRIVACY_AND_LOCATION", "ADVANCED"),
            SettingsPage.entries.map { it.name },
        )
        composeRule.onNodeWithTag(settingsPageTag(SettingsPage.STAYS_AND_VISAS)).assertIsDisplayed()
        composeRule.onNodeWithText("Stays & Visas").assertIsDisplayed()
    }

    @Test
    fun the_stays_page_holds_the_schengen_tax_and_allowance_groups() {
        show(page = SettingsPage.STAYS_AND_VISAS)

        composeRule.onNodeWithTag(settingsGroupTag(SettingsGroup.SCHENGEN_COUNTER)).assertIsDisplayed()
        composeRule.onNodeWithTag(SchengenCounterSwitchTag).assertIsOn()
        composeRule.onNodeWithText("Schengen 90/180 counter").assertIsDisplayed()

        scrollTo(settingsGroupTag(SettingsGroup.STAY_ALLOWANCES))
        composeRule.onNodeWithTag(StayAllowanceCountryButtonTag).assertIsDisplayed()

        scrollTo(settingsGroupTag(SettingsGroup.TAX_RESIDENCE_DAYS))
        composeRule.onNodeWithTag(TaxResidenceSwitchTag).assertIsOn()
        composeRule.onNodeWithTag(TaxResidenceThresholdFieldTag).assertIsDisplayed()
        composeRule.onNodeWithText("Count days for tax residence").assertIsDisplayed()
    }

    @Test
    fun the_stays_controls_are_no_longer_on_privacy_and_location() {
        show(page = SettingsPage.PRIVACY_AND_LOCATION)

        scrollTo(settingsGroupTag(SettingsGroup.VISITED_PLACES))
        composeRule.onNodeWithTag(settingsGroupTag(SettingsGroup.VISITED_PLACES)).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(SchengenCounterSwitchTag).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag(StayAllowanceCountryButtonTag).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun the_switches_follow_the_settings_and_edit_them() {
        var edited: AppSettings? = null
        show(
            page = SettingsPage.STAYS_AND_VISAS,
            settings = AppSettings(schengenCounterEnabled = false),
            actions = SettingsActions(onUpdate = { transform -> edited = transform(AppSettings(schengenCounterEnabled = false)) }),
        )

        composeRule.onNodeWithTag(SchengenCounterSwitchTag).assertIsOff()
        composeRule.onNodeWithTag(SchengenCounterSwitchTag).performClick()

        assertEquals(true, edited?.schengenCounterEnabled)
    }

    @Test
    fun the_data_group_warns_when_visited_places_are_off_and_points_to_the_privacy_page() {
        var opened: SettingsDestination? = null
        show(
            page = SettingsPage.STAYS_AND_VISAS,
            settings = AppSettings(visitedPlacesEnabled = false),
            actions = SettingsActions(onOpenDestination = { opened = it }),
        )

        scrollTo(StaysVisitedPlacesPointerTag)
        composeRule.onNodeWithTag(StaysVisitedPlacesOffNoticeTag).assertIsDisplayed()
        composeRule.onNodeWithTag(StaysVisitedPlacesPointerTag).performClick()

        assertEquals(SettingsDestination.VISITED_PLACES, opened)
    }
}
