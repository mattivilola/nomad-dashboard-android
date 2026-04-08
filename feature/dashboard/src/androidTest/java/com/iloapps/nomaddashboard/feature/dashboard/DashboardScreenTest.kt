package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.SignalLevel
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
}
