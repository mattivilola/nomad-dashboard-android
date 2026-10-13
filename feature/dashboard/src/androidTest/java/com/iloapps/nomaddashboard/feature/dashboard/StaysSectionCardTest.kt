package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.travel.stayscard.StaysCardPresentation
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StaysSectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 12)
    private val opened = mutableListOf<SettingsDestination>()
    private var visitedOpened = 0

    /** [count] consecutive days in [code] ending today. */
    private fun daysIn(code: String, count: Int): List<VisitedCountryDay> =
        (count - 1 downTo 0).map { offset ->
            VisitedCountryDay(today.minusDays(offset.toLong()), code, code, VisitedPlaceSource.DEVICE_LOCATION, isInferred = false)
        }

    private fun show(
        days: List<VisitedCountryDay>,
        current: String? = "MA",
        settings: AppSettings = AppSettings(),
    ) {
        val presentation = StaysCardPresentation.of(settings, days, current, today, Locale.ENGLISH)
        composeRule.setContent {
            NomadTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StaysSectionCard(
                        presentation = presentation,
                        onOpenSettings = { opened += it },
                        onOpenVisited = { visitedOpened += 1 },
                    )
                }
            }
        }
    }

    @Test
    fun theCurrentStayNamesTheCountryDayAndStartDate() {
        show(daysIn("MA", 23))

        composeRule.onNodeWithTag(StaysCardTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Days recorded on this device").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Morocco · day 23").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("since 20 Sep").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tracking").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun schengenCountsTheDaysAndNamesTheLeaveByDateForANonEuCitizen() {
        show(daysIn("ES", 61), current = "ES", settings = AppSettings(citizenshipCountryCodes = listOf("US")))

        composeRule.onNodeWithTag(StaysSchengenTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("61 of 90 days used (rolling 180 days) · leave by", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag(StaysSetCitizenshipTag).assertCountEquals(0)
    }

    @Test
    fun schengenWithoutACitizenshipPromptsToSetIt() {
        show(daysIn("ES", 61), current = "ES")

        composeRule.onNodeWithText("61 of 90 days used (rolling 180 days)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Set your citizenship to check if this applies").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(StaysSetCitizenshipTag).performClick()

        assertEquals(listOf(SettingsDestination.CITIZENSHIP), opened)
    }

    @Test
    fun anEuCitizenIsExemptFromTheLimit() {
        show(daysIn("ES", 100), current = "ES", settings = AppSettings(citizenshipCountryCodes = listOf("FI")))

        composeRule.onNodeWithText("Schengen: no limit for EU/EEA/Swiss citizens").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag(StaysSetCitizenshipTag).assertCountEquals(0)
    }

    @Test
    fun anAllowanceNearItsEndWarns() {
        show(daysIn("MA", 80), settings = AppSettings(stayAllowances = mapOf("MA" to 90)))

        composeRule.onNodeWithTag(StaysAllowanceTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Morocco allowance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("80 of 90 days · leave by 22 Oct").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Limit Near").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anOverstayIsOverTheLimit() {
        show(daysIn("MA", 93), settings = AppSettings(stayAllowances = mapOf("MA" to 90)))

        composeRule.onNodeWithText("93 of 90 days · 3 days over · ended 9 Oct").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Over Limit").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun taxYearDaysComeWithTheThresholdAndAnAdviceDisclaimer() {
        show(daysIn("ES", 97), current = "ES", settings = AppSettings(citizenshipCountryCodes = listOf("FI")))

        composeRule.onNodeWithTag(StaysTaxYearTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Days in 2026").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Threshold 183 days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Spain").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("97 days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("not tax or legal advice", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutACurrentCountryTheCardSaysItIsNotKnownYet() {
        show(daysIn("MA", 5), current = null)

        composeRule.onNodeWithText("Current country not known yet").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theCardLinksToTheStaysSettingsPage() {
        show(daysIn("MA", 5))

        composeRule.onNodeWithTag(StaysSettingsLinkTag).performScrollTo().performClick()

        assertEquals(listOf(SettingsDestination.STAYS), opened)
    }

    @Test
    fun reviewDaysOpensTheVisitedTab() {
        show(daysIn("MA", 5))

        composeRule.onNodeWithTag(StaysReviewDaysTag).performScrollTo().performClick()

        assertEquals(1, visitedOpened)
    }

    @Test
    fun withVisitedPlacesOffTheCardIsTurnedOffWithAPromptToTurnThemOn() {
        show(daysIn("MA", 5), settings = AppSettings(visitedPlacesEnabled = false))

        composeRule.onNodeWithText("Off").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(StaysCardPresentation.OffMessage).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag(StaysCurrentStayTag).assertCountEquals(0)
        composeRule.onNodeWithTag(StaysOpenSettingsTag).performClick()

        assertEquals(listOf(SettingsDestination.VISITED_PLACES), opened)
    }
}
