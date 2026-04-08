package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import org.junit.Rule
import org.junit.Test

class LocalPriceLevelSectionCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun europeSnapshotShowsThreeRowsAndBand() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalPriceLevelSnapshot(
                            status = LocalPriceLevelStatus.READY,
                            summaryBand = LocalPriceSummaryBand.LOW,
                            countryCode = "ES",
                            countryName = "Spain",
                            rows = listOf(
                                row(LocalPriceIndicatorKind.MEAL_OUT, "Below Avg", "16% below EU average · Country fallback · 2024"),
                                row(LocalPriceIndicatorKind.GROCERIES, "Moderate", "4% below EU average · Country fallback · 2024"),
                                row(LocalPriceIndicatorKind.OVERALL, "Moderate", "1% above EU average · Country fallback · 2024"),
                            ),
                            sources = listOf("Eurostat"),
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag(LocalPriceLevelCardTag).assertIsDisplayed()
        composeRule.onNodeWithText("Low").assertIsDisplayed()
        composeRule.onNodeWithText("Meal Out").assertIsDisplayed()
        composeRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeRule.onNodeWithText("Overall").assertIsDisplayed()
        composeRule.onNodeWithText("Sources: Eurostat").assertIsDisplayed()
    }

    @Test
    fun usSnapshotShowsLimitedRentOnly() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalPriceLevelSnapshot(
                            status = LocalPriceLevelStatus.PARTIAL,
                            summaryBand = LocalPriceSummaryBand.LIMITED,
                            countryCode = "US",
                            countryName = "United States",
                            rows = listOf(
                                LocalPriceIndicatorRow(
                                    kind = LocalPriceIndicatorKind.RENT_ONE_BEDROOM,
                                    value = "${'$'}1,900/mo",
                                    detail = "Metro benchmark · Seattle metro · 2024",
                                    precision = LocalPricePrecision.METRO_BENCHMARK,
                                    source = "HUD USER",
                                ),
                            ),
                            sources = listOf("HUD USER", "US Census Geocoder"),
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Limited").assertIsDisplayed()
        composeRule.onNodeWithText("1BR Rent").assertIsDisplayed()
        composeRule.onNodeWithText("Sources: HUD USER · US Census Geocoder").assertIsDisplayed()
    }

    @Test
    fun setupRequiredStateShowsSettingsCta() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalPriceLevelSnapshot(
                            status = LocalPriceLevelStatus.CONFIGURATION_REQUIRED,
                            countryCode = "US",
                            countryName = "United States",
                            sources = listOf("HUD USER", "US Census Geocoder"),
                            detail = "Add a HUD USER API token in Settings to show the US 1-bedroom rent benchmark.",
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Setup").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    @Test
    fun locationRequiredStateShowsSettingsCta() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalPriceLevelSnapshot(
                            status = LocalPriceLevelStatus.LOCATION_REQUIRED,
                            sources = listOf("Eurostat", "HUD USER", "US Census Geocoder"),
                            detail = "Allow current location or external IP location to estimate the local price level.",
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Location Needed").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    @Test
    fun unsupportedStateShowsUnsupportedBadge() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = true,
                        snapshot = LocalPriceLevelSnapshot(
                            status = LocalPriceLevelStatus.UNSUPPORTED,
                            countryCode = "JP",
                            countryName = "Japan",
                            sources = listOf("Eurostat", "HUD USER", "US Census Geocoder"),
                            detail = "Local price level is only supported in Europe and the United States right now.",
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Unsupported").assertIsDisplayed()
        composeRule.onNodeWithText("Sources: Eurostat · HUD USER · US Census Geocoder").assertIsDisplayed()
    }

    @Test
    fun disabledStateShowsOffBodyAndSources() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = dashboardUiState(
                        enabled = false,
                        snapshot = LocalPriceLevelSnapshot(),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Enable local price level in Settings.").assertIsDisplayed()
        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Sources: Eurostat · HUD USER · US Census Geocoder").assertIsDisplayed()
    }

    private fun dashboardUiState(
        enabled: Boolean,
        snapshot: LocalPriceLevelSnapshot,
    ): DashboardUiState =
        DashboardUiState(
            settings = AppSettings(
                localPriceLevelEnabled = enabled,
                dashboardCardOrder = listOf(DashboardCardId.LOCAL_PRICE_LEVEL),
            ),
            snapshot = DashboardSnapshot(localPriceLevel = snapshot),
        )

    private fun row(
        kind: LocalPriceIndicatorKind,
        value: String,
        detail: String,
    ): LocalPriceIndicatorRow =
        LocalPriceIndicatorRow(
            kind = kind,
            value = value,
            detail = detail,
            precision = LocalPricePrecision.COUNTRY_FALLBACK,
            source = "Eurostat",
        )
}
