package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.HolidaySourceAttribution
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class LocalInfoSectionCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readyStateShowsLocationHolidayAndPriceRows() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.READY,
                            locality = "Tarifa",
                            region = "Andalusia",
                            countryCode = "ES",
                            countryName = "Spain",
                            timezone = "Europe/Madrid",
                            publicHoliday = LocalHolidayStatus(
                                phase = LocalHolidayPhase.NEXT,
                                period = HolidayPeriod(
                                    name = "Labour Day",
                                    startDate = LocalDate.parse("2026-05-01"),
                                ),
                            ),
                            schoolHoliday = LocalHolidayStatus(
                                phase = LocalHolidayPhase.ON_BREAK,
                                period = HolidayPeriod(
                                    name = "Spring Holidays",
                                    startDate = LocalDate.parse("2026-04-01"),
                                    endDate = LocalDate.parse("2026-04-10"),
                                ),
                            ),
                            localPriceLevel = priceSnapshot(),
                            sources = listOf(
                                HolidaySourceAttribution("Nager.Date"),
                                HolidaySourceAttribution("OpenHolidays"),
                                HolidaySourceAttribution("Eurostat"),
                            ),
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag(LocalInfoCardTag).assertIsDisplayed()
        composeRule.onNodeWithText("Tarifa / Andalusia / Spain").assertIsDisplayed()
        composeRule.onNodeWithText("Next: Labour Day").assertIsDisplayed()
        composeRule.onNodeWithText("On break").assertIsDisplayed()
        composeRule.onNodeWithText("Meal Out").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sources: Nager.Date · OpenHolidays · Eurostat").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun partialStateShowsNoSchoolHolidayRow() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.PARTIAL,
                            locality = "Paris",
                            region = "Ile-de-France",
                            countryCode = "FR",
                            countryName = "France",
                            timezone = "Europe/Paris",
                            publicHoliday = LocalHolidayStatus(
                                phase = LocalHolidayPhase.TOMORROW,
                                period = HolidayPeriod(
                                    name = "Ascension Day",
                                    startDate = LocalDate.parse("2026-05-14"),
                                ),
                            ),
                            note = "School holidays appear only when the app can match your region confidently.",
                            sources = listOf(HolidaySourceAttribution("Nager.Date")),
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Tomorrow").assertIsDisplayed()
        composeRule.onNodeWithText("School holidays appear only when the app can match your region confidently.").assertIsDisplayed()
    }

    @Test
    fun publicHolidayActiveTodayRendersBusyWarningChip() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.READY,
                            locality = "Seville",
                            region = "Andalusia",
                            countryCode = "ES",
                            countryName = "Spain",
                            publicHoliday = LocalHolidayStatus(
                                phase = LocalHolidayPhase.TODAY,
                                period = HolidayPeriod(
                                    name = "National Day",
                                    startDate = LocalDate.parse("2026-10-12"),
                                ),
                            ),
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag(LocalInfoPublicHolidayWarningChipTag).assertIsDisplayed()
        composeRule.onNodeWithText("Busy Today").assertIsDisplayed()
        composeRule.onAllNodesWithText("Today").assertCountEquals(0)
    }

    @Test
    fun schoolBreakActiveNowRendersBusyWarningChip() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.READY,
                            locality = "Lyon",
                            region = "Auvergne-Rhone-Alpes",
                            countryCode = "FR",
                            countryName = "France",
                            schoolHoliday = LocalHolidayStatus(
                                phase = LocalHolidayPhase.ON_BREAK,
                                period = HolidayPeriod(
                                    name = "Winter Break",
                                    startDate = LocalDate.parse("2026-02-14"),
                                    endDate = LocalDate.parse("2026-02-28"),
                                ),
                            ),
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag(LocalInfoSchoolBreakWarningChipTag).assertIsDisplayed()
        composeRule.onNodeWithText("Busy Period").assertIsDisplayed()
        composeRule.onAllNodesWithText("On break").assertCountEquals(0)
    }

    @Test
    fun upcomingOrTomorrowHolidaysDoNotRenderWarningChips() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.READY,
                            locality = "Paris",
                            region = "Ile-de-France",
                            countryCode = "FR",
                            countryName = "France",
                            publicHoliday = LocalHolidayStatus(
                                phase = LocalHolidayPhase.TOMORROW,
                                period = HolidayPeriod(
                                    name = "Ascension Day",
                                    startDate = LocalDate.parse("2026-05-14"),
                                ),
                            ),
                            schoolHoliday = LocalHolidayStatus(
                                phase = LocalHolidayPhase.NEXT,
                                period = HolidayPeriod(
                                    name = "Summer Break",
                                    startDate = LocalDate.parse("2026-07-04"),
                                    endDate = LocalDate.parse("2026-08-30"),
                                ),
                            ),
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(LocalInfoPublicHolidayWarningChipTag).assertCountEquals(0)
        composeRule.onAllNodesWithTag(LocalInfoSchoolBreakWarningChipTag).assertCountEquals(0)
        composeRule.onNodeWithText("Tomorrow").assertIsDisplayed()
        composeRule.onNodeWithText("Next break: Summer Break").assertIsDisplayed()
    }

    @Test
    fun bothActiveWarningsRenderAndFitNarrowWidth() {
        composeRule.setContent {
            NomadTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    DashboardScreen(
                        state = dashboardUiState(
                            enabled = true,
                            snapshot = LocalInfoSnapshot(
                                status = LocalInfoStatus.READY,
                                locality = "Barcelona",
                                region = "Catalonia",
                                countryCode = "ES",
                                countryName = "Spain",
                                publicHoliday = LocalHolidayStatus(
                                    phase = LocalHolidayPhase.TODAY,
                                    period = HolidayPeriod(
                                        name = "National Day",
                                        startDate = LocalDate.parse("2026-09-11"),
                                    ),
                                ),
                                schoolHoliday = LocalHolidayStatus(
                                    phase = LocalHolidayPhase.ON_BREAK,
                                    period = HolidayPeriod(
                                        name = "Spring Break",
                                        startDate = LocalDate.parse("2026-04-01"),
                                        endDate = LocalDate.parse("2026-04-10"),
                                    ),
                                ),
                            ),
                        ),
                        hasLocationPermission = true,
                        onRefresh = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LocalInfoCardTag).assertIsDisplayed()
        composeRule.onNodeWithTag(LocalInfoPublicHolidayWarningChipTag).assertIsDisplayed()
        composeRule.onNodeWithTag(LocalInfoSchoolBreakWarningChipTag).assertIsDisplayed()
        composeRule.onNodeWithText("Busy Today").assertIsDisplayed()
        composeRule.onNodeWithText("Busy Period").assertIsDisplayed()
    }

    @Test
    fun locationRequiredStateShowsSettingsCta() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.LOCATION_REQUIRED,
                            detail = "Allow location or enable IP-based location to show Local Info.",
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Location Needed").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    @Test
    fun unsupportedAndUnavailableStatesRenderMessages() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.UNSUPPORTED,
                            locality = "Tokyo",
                            region = "Tokyo",
                            countryCode = "JP",
                            countryName = "Japan",
                            detail = "Local context is available, but some Local Info sources do not cover this location yet.",
                            sources = listOf(HolidaySourceAttribution("Nager.Date")),
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Unsupported").assertIsDisplayed()
        composeRule.onNodeWithText("Local context is available, but some Local Info sources do not cover this location yet.").assertIsDisplayed()
    }

    @Test
    fun disabledStateShowsOffBodyAndSources() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = false,
                        snapshot = LocalInfoSnapshot(),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Local Info is disabled. Enable it in Settings.").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Sources: Nager.Date · OpenHolidays · Eurostat · HUD USER · US Census Geocoder").assertIsDisplayed()
    }

    private fun dashboardUiState(
        enabled: Boolean,
        snapshot: LocalInfoSnapshot,
    ): DashboardUiState =
        DashboardUiState(
            settings = AppSettings(
                localInfoEnabled = enabled,
                dashboardCardOrder = listOf(DashboardCardId.LOCAL_INFO),
            ),
            snapshot = DashboardSnapshot(localInfo = snapshot),
        )

    private fun priceSnapshot(): LocalPriceLevelSnapshot =
        LocalPriceLevelSnapshot(
            countryCode = "ES",
            countryName = "Spain",
            rows = listOf(
                LocalPriceIndicatorRow(
                    kind = LocalPriceIndicatorKind.MEAL_OUT,
                    value = "Below Avg",
                    detail = "16% below EU average · Country fallback · 2024",
                    precision = LocalPricePrecision.COUNTRY_FALLBACK,
                    source = "Eurostat",
                ),
                LocalPriceIndicatorRow(
                    kind = LocalPriceIndicatorKind.GROCERIES,
                    value = "Moderate",
                    detail = "4% below EU average · Country fallback · 2024",
                    precision = LocalPricePrecision.COUNTRY_FALLBACK,
                    source = "Eurostat",
                ),
            ),
            sources = listOf("Eurostat"),
        )
}
