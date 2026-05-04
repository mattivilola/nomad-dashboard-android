package com.iloapps.nomaddashboard.feature.visited

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisitedScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun worldFootprintRenders_and_yearSelectionUpdatesSummary_withoutMapKey() {
        var clearHistoryClicks = 0

        composeTestRule.setContent {
            NomadTheme {
                VisitedScreen(
                    state = VisitedUiState(
                        settings = AppSettings(),
                        places = listOf(
                            VisitedPlace(
                                city = "Berlin",
                                region = "Berlin",
                                country = "Germany",
                                countryCode = "DE",
                                latitude = 52.52,
                                longitude = 13.405,
                                sources = listOf(VisitedPlaceSource.PUBLIC_IP_GEOLOCATION),
                                firstVisitedAt = Instant.parse("2025-06-01T10:15:30Z"),
                                lastVisitedAt = Instant.parse("2025-06-01T10:15:30Z"),
                            ),
                        ),
                        countryDays = listOf(
                            countryDay(country = "Germany", countryCode = "DE", year = 2025),
                            countryDay(country = "France", countryCode = "FR", year = 2024),
                            countryDay(country = "France", countryCode = "FR", year = 2024, day = 8),
                        ),
                    ),
                    hasLocationPermission = true,
                    hasMapsApiKey = false,
                    onRefresh = {},
                    onClearHistory = { clearHistoryClicks += 1 },
                    onRequestLocationPermission = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("visited_overview_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear history").performClick()
        assertEquals(1, clearHistoryClicks)
        composeTestRule.onNodeWithTag("visited_list").performScrollToNode(hasText("World Footprint"))

        composeTestRule.onNodeWithTag("visited_world_footprint").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Maps key needed").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("In 2025 you tracked 1 day.").assertCountEquals(1)

        composeTestRule.onNodeWithTag("visited_list").performScrollToNode(hasTestTag("visited-year-2024"))
        composeTestRule.onNodeWithTag("visited-year-2024").performClick()
        composeTestRule.onNodeWithTag("visited-year-2024").assertIsSelected()

        composeTestRule.onNodeWithTag("visited_list").performScrollToNode(hasText("In 2024 you tracked 2 days."))
        composeTestRule.onAllNodesWithText("In 2024 you tracked 2 days.").assertCountEquals(1)
    }

    private fun countryDay(
        country: String,
        countryCode: String,
        year: Int,
        day: Int = 7,
    ) = VisitedCountryDay(
        date = LocalDate.of(year, 4, day),
        country = country,
        countryCode = countryCode,
        source = VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
        isInferred = false,
    )
}
