package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
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
import com.iloapps.nomaddashboard.core.travel.cardcontent.LocalInfoRows
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocalInfoSectionCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readyStateShowsEmergencyNumberHolidayAndPriceRows() {
        val labourDay = LocalDate.now().plusDays(12)
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
                                    startDate = labourDay,
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
        composeRule.onNodeWithText("Tarifa · Andalusia, Spain · Europe/Madrid").assertIsDisplayed()
        composeRule.onNodeWithTag(localInfoRowTag(LocalInfoRows.EMERGENCY_ID)).assertIsDisplayed()
        composeRule.onNodeWithText("112").assertIsDisplayed()
        composeRule.onNodeWithText("Guardia Civil 062").assertIsDisplayed()
        composeRule.onNodeWithText("Labour Day").assertIsDisplayed()
        val day = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(labourDay)
        composeRule.onNodeWithText("$day · in 12 days").assertIsDisplayed()
        composeRule.onNodeWithTag(LocalInfoSchoolBreakWarningChipTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Prices vs EU").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Meals \u221216% · Groceries \u22124%").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Meal Out").assertCountEquals(0)
        composeRule.onNodeWithText("Sources: Nager.Date · OpenHolidays · Eurostat").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theEmergencyNumberRowComesFirst() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.READY,
                            countryCode = "JP",
                            countryName = "Japan",
                            publicHoliday = LocalHolidayStatus(
                                phase = LocalHolidayPhase.NEXT,
                                period = HolidayPeriod("Culture Day", LocalDate.now().plusDays(20)),
                            ),
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        val emergencyTop = composeRule.onNodeWithTag(localInfoRowTag(LocalInfoRows.EMERGENCY_ID))
            .fetchSemanticsNode().positionInRoot.y
        val holidayTop = composeRule.onNodeWithTag(localInfoRowTag(LocalInfoRows.PUBLIC_HOLIDAY_ID))
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("The emergency number must lead the rows.", emergencyTop < holidayTop)
        composeRule.onNodeWithText("110").assertIsDisplayed()
        composeRule.onNodeWithText("Ambulance/Fire 119").assertIsDisplayed()
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

        composeRule.onNodeWithText("tomorrow", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Police 17 · Ambulance 15 · Fire 18").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("School holidays appear only when the app can match your region confidently.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun locationOnlyStateShowsThePlaceInTheSubtitleWithoutEmptyRows() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalInfoSnapshot(
                            status = LocalInfoStatus.READY,
                            locality = "Neckargemuend",
                            region = "Baden-Wuerttemberg",
                            countryCode = "DE",
                            countryName = "Germany",
                            timezone = "Europe/Berlin",
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Neckargemuend · Baden-Wuerttemberg, Germany · Europe/Berlin").assertIsDisplayed()
        composeRule.onNodeWithTag(localInfoRowTag(LocalInfoRows.EMERGENCY_ID)).assertIsDisplayed()
        composeRule.onNodeWithText("Police 110").assertIsDisplayed()
        composeRule.onAllNodesWithText("Public Holiday").assertCountEquals(0)
        composeRule.onAllNodesWithText("School Break").assertCountEquals(0)
        composeRule.onAllNodesWithText("Prices vs EU").assertCountEquals(0)
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
        composeRule.onNodeWithText("tomorrow", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Summer Break").performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithTag(LocalInfoSchoolBreakWarningChipTag).performScrollTo().assertIsDisplayed()
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

        // A turned-off card is not on the dashboard; Customize Layout lists every card, including this one.
        composeRule.onAllNodesWithTag(LocalInfoCardTag).assertCountEquals(0)
        composeRule.onNodeWithTag(DashboardOverflowTag).performClick()
        composeRule.onNodeWithTag(DashboardCustomizeToggleTag).performClick()

        composeRule.onNodeWithText("Local Info is disabled. Enable it in Settings.").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("Open Settings") and hasAnyAncestor(hasTestTag(LocalInfoCardTag))).assertIsDisplayed()
        composeRule.onNodeWithText("Sources: Nager.Date · OpenHolidays · Eurostat · HUD USER · US Census Geocoder")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun localRhythmRowsShowThePeriodDatesAndVisitorGuidance() {
        // Ramadan 2027 starts on 8 Feb (Umm al-Qura), so on 3 Feb it is five days away.
        setLocalInfoCard(
            now = Instant.parse("2027-02-03T12:00:00Z"),
            snapshot = DashboardSnapshot(
                travelContext = TravelContextSnapshot(deviceCountryCode = "MA"),
                localInfo = LocalInfoSnapshot(status = LocalInfoStatus.READY, countryCode = "MA", countryName = "Morocco"),
            ),
        )

        composeRule.onNodeWithText("Local rhythm").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ramadan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("In 5 days").assertIsDisplayed()
        composeRule.onNodeWithText("(expected; may shift a day)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Shops and offices keep shorter hours", substring = true).assertIsDisplayed()
    }

    @Test
    fun noLocalRhythmSectionOutsideThePeriods() {
        setLocalInfoCard(
            now = Instant.parse("2026-10-12T12:00:00Z"),
            snapshot = DashboardSnapshot(
                travelContext = TravelContextSnapshot(deviceCountryCode = "ES"),
                localInfo = LocalInfoSnapshot(status = LocalInfoStatus.READY, countryCode = "ES", countryName = "Spain"),
            ),
        )

        composeRule.onAllNodesWithText("Local rhythm").assertCountEquals(0)
    }

    @Test
    fun theEmergencyNumberFollowsTheDeviceCountryBeforeTheLocalInfoCountry() {
        setLocalInfoCard(
            now = Instant.parse("2026-10-12T12:00:00Z"),
            snapshot = DashboardSnapshot(
                travelContext = TravelContextSnapshot(deviceCountryCode = "JP"),
                localInfo = LocalInfoSnapshot(status = LocalInfoStatus.READY, countryCode = "ES", countryName = "Spain"),
            ),
        )

        composeRule.onNodeWithText("110").assertIsDisplayed()
        composeRule.onAllNodesWithText("Guardia Civil 062").assertCountEquals(0)
    }

    private fun setLocalInfoCard(now: Instant, snapshot: DashboardSnapshot) {
        composeRule.setContent {
            NomadTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    LocalInfoSectionCard(
                        enabled = true,
                        snapshot = snapshot,
                        onOpenSettings = {},
                        now = now,
                    )
                }
            }
        }
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
