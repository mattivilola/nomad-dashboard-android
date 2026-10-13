package com.iloapps.nomaddashboard.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.core.model.AirQualityForecastSlot
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.MarineForecastSlot
import com.iloapps.nomaddashboard.core.model.MarineSnapshot
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightReference
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WeatherSectionCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val today: LocalDate = now.atZone(ZoneId.systemDefault()).toLocalDate()

    private fun day(offset: Long) = WeatherDayForecast(
        date = today.plusDays(offset),
        summary = "Mostly clear",
        minCelsius = 12.0,
        maxCelsius = 21.0,
        rainChancePercent = 20,
        weatherCode = 1,
        windSpeedKph = 14.0,
        windDirectionDegrees = 90.0,
    )

    private fun weather(uv: Int? = 7) = WeatherSnapshot(
        currentTemperatureCelsius = 19.4,
        apparentTemperatureCelsius = 17.6,
        windSpeedKph = 22.0,
        windDirectionDegrees = 270.0,
        rainChancePercent = 10,
        summary = "Sunny with Levante wind",
        conditionDescription = "Sunny",
        weatherCode = 0,
        uvIndex = uv,
        hourlyForecast = (0..5).map { hours ->
            WeatherHourlyForecastSlot(
                at = now.plusSeconds(hours * 3_600L),
                summary = "Clear",
                temperatureCelsius = 19.0 + hours,
                rainChancePercent = 10,
                windSpeedKph = 20.0,
                windDirectionDegrees = 270.0,
                weatherCode = 0,
                uvIndex = if (hours == 2) 9 else 7,
            )
        },
        dailyForecast = (0L..6L).map(::day),
        sourceName = "Open-Meteo",
        fetchedAt = now,
    )

    private fun airQuality() = AirQualitySnapshot(
        latitude = 36.0,
        longitude = -5.6,
        sourceName = "Open-Meteo",
        europeanAqi = 38,
        mainPollutant = AirQualityPollutant.PM10,
        pm2_5 = 12.0,
        pm10 = 30.0,
        ozone = 80.0,
        nitrogenDioxide = 14.0,
        forecastSlots = (1..4).map { AirQualityForecastSlot(now.plusSeconds(it * 3_600L), 30 + it * 5) },
        fetchedAt = now,
    )

    private fun marine() = MarineSnapshot(
        spotName = "Tarifa",
        waveHeightMeters = 1.24,
        wavePeriodSeconds = 8.0,
        swellHeightMeters = 1.1,
        swellDirectionDegrees = 270.0,
        windSpeedKph = 14.0,
        windDirectionDegrees = 45.0,
        seaSurfaceTemperatureCelsius = 25.4,
        forecastSlots = (1..4).map {
            MarineForecastSlot(now.plusSeconds(it * 10_800L), 1.0, 1.0, 12.0, 90.0)
        },
        fetchedAt = now,
    )

    private fun setCard(
        snapshot: WeatherSnapshot = weather(),
        settings: AppSettings = AppSettings(weatherForecastExpanded = false),
        airQuality: AirQualitySnapshot? = null,
        daylight: DaylightReference? = null,
        marine: MarineSnapshot? = null,
        surfSpot: SurfSpotConfiguration = SurfSpotConfiguration(),
        startupLocation: StartupLocationBootstrapState = StartupLocationBootstrapState(),
        onOpenSurfSettings: () -> Unit = {},
        onForecastExpandedChange: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            NomadTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    WeatherSectionCard(
                        snapshot = snapshot,
                        startupLocation = startupLocation,
                        surfSpot = surfSpot,
                        marine = marine,
                        airQuality = airQuality,
                        settings = settings,
                        daylight = daylight,
                        onOpenSurfSettings = onOpenSurfSettings,
                        onForecastExpandedChange = onForecastExpandedChange,
                        now = now,
                    )
                }
            }
        }
    }

    @Test
    fun showsTheTemperatureAndTheConditionOnce() {
        setCard()

        composeRule.onNodeWithTag("weather_temperature").assertTextEquals("19°")
        composeRule.onAllNodesWithText("Sunny").assertCountEquals(1)
        composeRule.onAllNodesWithText("19 C").assertCountEquals(0)
    }

    @Test
    fun showsFeelsLikeWindWithMetersPerSecondAndRainChance() {
        setCard()

        composeRule.onNodeWithText("FEELS LIKE").assertIsDisplayed()
        composeRule.onNodeWithText("18°").assertIsDisplayed()
        composeRule.onNodeWithText("22 km/h W").assertIsDisplayed()
        composeRule.onNodeWithText("6.1 m/s").assertIsDisplayed()
        composeRule.onNodeWithText("RAIN CHANCE").assertIsDisplayed()
        // The hourly strip repeats the rain chance, so the stat is the first of several.
        composeRule.onAllNodesWithText("10%").onFirst().assertIsDisplayed()
    }

    @Test
    fun showsTheUvIndexWithTheRestOfTodaysPeak() {
        setCard()

        composeRule.onNodeWithText("7 (high)").assertIsDisplayed()
        composeRule.onNodeWithText("peak 9 at", substring = true).assertIsDisplayed()
    }

    @Test
    fun showsAirQualityOnlyWhileItIsOn() {
        setCard(
            settings = AppSettings(useCurrentLocationForWeather = true, airQualityEnabled = true),
            airQuality = airQuality(),
        )

        composeRule.onNodeWithTag(WeatherAirQualityTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AQI 38 · Fair · mostly PM10").assertIsDisplayed()
    }

    @Test
    fun hidesAirQualityWhenTheSettingIsOff() {
        setCard(
            settings = AppSettings(useCurrentLocationForWeather = true, airQualityEnabled = false),
            airQuality = airQuality(),
        )

        composeRule.onAllNodesWithTag(WeatherAirQualityTag).assertCountEquals(0)
    }

    @Test
    fun theHourlyStripIsAlwaysVisible() {
        setCard(settings = AppSettings(weatherForecastExpanded = false))
        composeRule.onNodeWithTag(WeatherHourlyStripTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Now").assertIsDisplayed()
    }

    @Test
    fun theHourlyStripStaysWhenTheOutlookIsOpen() {
        setCard(settings = AppSettings(weatherForecastExpanded = true))

        composeRule.onNodeWithTag(WeatherHourlyStripTag).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFoldedOutlookShowsTomorrowAndOpensOnTap() {
        val changes = mutableListOf<Boolean>()
        setCard(settings = AppSettings(weatherForecastExpanded = false), onForecastExpandedChange = { changes += it })

        composeRule.onNodeWithTag(WeatherTomorrowRowTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("7-day outlook").performScrollTo().performClick()

        composeRule.onAllNodesWithTag(WeatherTomorrowRowTag).assertCountEquals(0)
        composeRule.onNodeWithText("Today").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun anOpenOutlookFoldsOnTap() {
        val changes = mutableListOf<Boolean>()
        setCard(settings = AppSettings(weatherForecastExpanded = true), onForecastExpandedChange = { changes += it })

        composeRule.onAllNodesWithTag(WeatherTomorrowRowTag).assertCountEquals(0)
        composeRule.onNodeWithText("7-day outlook").performScrollTo().performClick()

        composeRule.onNodeWithTag(WeatherTomorrowRowTag).performScrollTo().assertIsDisplayed()
        assertEquals(listOf(false), changes)
    }

    @Test
    fun showsSunriseAndSunsetFromTheDeviceCoordinates() {
        setCard(daylight = DaylightReference(60.17, 24.94, ZoneId.of("Europe/Helsinki")))

        composeRule.onNodeWithTag(WeatherDaylightLineTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(WeatherDaylightLineTag).assertTextContains("Sunrise", substring = true)
        composeRule.onNodeWithTag(WeatherDaylightLineTag).assertTextContains("Sunset", substring = true)
    }

    @Test
    fun showsSunriseAndSunsetEvenWithoutWeatherData() {
        setCard(
            snapshot = WeatherSnapshot(),
            daylight = DaylightReference(60.17, 24.94, ZoneId.of("Europe/Helsinki")),
        )

        composeRule.onNodeWithText("Weather unavailable").assertIsDisplayed()
        composeRule.onNodeWithTag(WeatherDaylightLineTag).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hidesTheDaylightLineWithoutALocation() {
        setCard(daylight = null)

        composeRule.onAllNodesWithTag(WeatherDaylightLineTag).assertCountEquals(0)
    }

    @Test
    fun surfIsOneRowOfFourStatsAndFourForecastColumns() {
        setCard(marine = marine(), surfSpot = SurfSpotConfiguration("Tarifa", 36.0, -5.6))

        composeRule.onNodeWithTag(WeatherSurfBandTag).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("WAVE").assertIsDisplayed()
        composeRule.onNodeWithText("SWELL").assertIsDisplayed()
        composeRule.onNodeWithText("SEA").assertIsDisplayed()
        composeRule.onNodeWithText("25°").assertIsDisplayed()
        composeRule.onNodeWithText("+3h").assertIsDisplayed()
        composeRule.onNodeWithText("+12h").assertIsDisplayed()
    }

    @Test
    fun anUnsetSurfSpotOffersSettings() {
        var opened = 0
        setCard(onOpenSurfSettings = { opened++ })

        composeRule.onNodeWithTag(WeatherSurfSettingsTag).performScrollTo().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun waitingForTheLocationSaysSo() {
        setCard(
            snapshot = WeatherSnapshot(),
            startupLocation = StartupLocationBootstrapState(isChecking = true),
        )

        composeRule.onNodeWithText("Checking device location before loading location-based weather.").assertIsDisplayed()
    }
}
