package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapPhase
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import com.iloapps.nomaddashboard.core.model.SummaryTile
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTopBarAndPrimaryWeatherCard() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        settings = AppSettings(weatherForecastExpanded = true),
                        snapshot = DashboardSnapshot(
                            overallSummary = SummaryTile("Overall", "Ready", "Everything looks stable.", SignalLevel.GOOD),
                            networkSummary = SummaryTile("Network", "Stable", "Nomad Cabin Wi-Fi is online.", SignalLevel.GOOD),
                            powerSummary = SummaryTile("Power", "78%", "Battery drain is modest.", SignalLevel.GOOD),
                            travelContext = TravelContextSnapshot(
                                city = "Tarifa",
                                country = "Spain",
                            ),
                            power = PowerSnapshot(
                                batteryPercent = 78,
                                statusLabel = "On Battery",
                                batteryHealthSummary = "Good",
                                powerSourceLabel = "Battery",
                                temperatureCelsius = 31.2,
                            ),
                            weather = WeatherSnapshot(
                                currentTemperatureCelsius = 18.0,
                                apparentTemperatureCelsius = 17.0,
                                windSpeedKph = 22.0,
                                rainChancePercent = 10,
                                summary = "Sunny with Levante wind",
                                dailyForecast = listOf(
                                    WeatherDayForecast(LocalDate.of(2026, 4, 7), "Sunny", 14.0, 20.0, 10),
                                    WeatherDayForecast(LocalDate.of(2026, 4, 8), "Clear", 15.0, 21.0, 0),
                                ),
                            ),
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_summary_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_weather_card").assertIsDisplayed()
    }

    @Test
    fun showsRefreshProgressWhileRefreshing() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        snapshot = DashboardSnapshot(isRefreshing = true),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_refresh_progress").assertIsDisplayed()
    }

    @Test
    fun showsDeviceAndIpLocationsInDashboardHeader() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        snapshot = DashboardSnapshot(
                            travelContext = TravelContextSnapshot(
                                city = "Tarifa",
                                country = "Spain",
                                deviceCity = "Cadiz",
                                deviceCountry = "Spain",
                            ),
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_header_location_comparison").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_header_location_device").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_header_location_ip").assertIsDisplayed()
    }

    @Test
    fun showsOnlyDeviceLocationInDashboardHeaderWhenIpLocationMissing() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        snapshot = DashboardSnapshot(
                            travelContext = TravelContextSnapshot(
                                deviceCity = "Cadiz",
                                deviceCountry = "Spain",
                            ),
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_header_location_device").assertIsDisplayed()
        composeRule.onAllNodesWithTag("dashboard_header_location_ip").assertCountEquals(0)
    }

    @Test
    fun showsOnlyIpLocationInDashboardHeaderWhenDeviceLocationMissing() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        snapshot = DashboardSnapshot(
                            travelContext = TravelContextSnapshot(
                                city = "Tarifa",
                                country = "Spain",
                            ),
                        ),
                    ),
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_header_location_ip").assertIsDisplayed()
        composeRule.onAllNodesWithTag("dashboard_header_location_device").assertCountEquals(0)
    }

    @Test
    fun showsStartupLocationCheckingCopyInsteadOfUnavailable() {
        composeRule.setContent {
            NomadTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        settings = AppSettings(
                            publicIpGeolocationEnabled = true,
                            useCurrentLocationForWeather = true,
                        ),
                        snapshot = DashboardSnapshot(
                            isRefreshing = true,
                            startupLocation = StartupLocationBootstrapState(
                                phase = StartupLocationBootstrapPhase.CHECKING_DEVICE_LOCATION,
                                isChecking = true,
                            ),
                        ),
                    ),
                    hasLocationPermission = true,
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("Checking device location...").assertIsDisplayed()
        composeRule.onNodeWithText("Checking device location before loading location-based cards...").assertIsDisplayed()
        composeRule.onNodeWithText("Checking device location before loading location-based weather.").assertIsDisplayed()
    }
}
