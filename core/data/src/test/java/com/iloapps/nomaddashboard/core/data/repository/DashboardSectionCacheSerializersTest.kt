package com.iloapps.nomaddashboard.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.database.entity.DashboardSectionCacheEntity
import com.iloapps.nomaddashboard.core.model.AirQualityForecastSlot
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.HospitalOwnership
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Test

class DashboardSectionCacheSerializersTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `travel context cache round trips with generated serializer`() {
        val snapshot = TravelContextSnapshot(
            publicIp = "198.51.100.12",
            city = "Colmar",
            region = "Grand Est",
            country = "France",
            countryCode = "FR",
            latitude = 48.079,
            longitude = 7.358,
            timeZoneId = "Europe/Paris",
            deviceCity = "Colmar",
            deviceCountry = "France",
            deviceLatitude = 48.0835,
            deviceLongitude = 7.3553,
        )

        val entity = snapshot.toCacheEntity(json = json, fetchedAt = Instant.ofEpochMilli(1234))
        val cached = loadCachedDashboardSections(entries = listOf(entity), json = json)

        assertThat(cached.travelContext).isEqualTo(snapshot)
        assertThat(cached.lastRefresh).isEqualTo(Instant.ofEpochMilli(1234))
    }

    @Test
    fun `weather cache keeps uv and hourly uv through a cold start`() {
        val fetchedAt = Instant.ofEpochMilli(5_000)
        val snapshot = WeatherSnapshot(
            currentTemperatureCelsius = 21.0,
            uvIndex = 7,
            hourlyForecast = listOf(
                WeatherHourlyForecastSlot(
                    at = Instant.ofEpochMilli(3_600_000),
                    summary = "Sunny",
                    temperatureCelsius = 22.0,
                    rainChancePercent = 5,
                    windSpeedKph = 12.0,
                    windDirectionDegrees = 90.0,
                    weatherCode = 0,
                    uvIndex = 9,
                ),
                WeatherHourlyForecastSlot(
                    at = Instant.ofEpochMilli(7_200_000),
                    summary = "Cloudy",
                    temperatureCelsius = 20.0,
                    rainChancePercent = null,
                    windSpeedKph = null,
                    windDirectionDegrees = null,
                ),
            ),
            fetchedAt = fetchedAt,
        )

        val entity = requireNotNull(snapshot.toCacheEntity(json))
        val cached = loadCachedDashboardSections(entries = listOf(entity), json = json)

        assertThat(cached.weather?.uvIndex).isEqualTo(7)
        assertThat(cached.weather?.hourlyForecast?.map { it.uvIndex }).containsExactly(9, null).inOrder()
        assertThat(cached.weather).isEqualTo(snapshot)
    }

    @Test
    fun `weather cache written before uv existed still decodes`() {
        val legacy = DashboardSectionCacheEntity(
            sectionId = DashboardSectionId.WEATHER,
            payloadJson = """
                {"currentTemperatureCelsius":18.5,"summary":"Today tops out at 20°C","conditionDescription":"Clear",
                 "hourlyForecast":[{"atEpochMillis":3600000,"summary":"Clear","temperatureCelsius":19.0}],
                 "sourceName":"Open-Meteo","fetchedAtEpochMillis":5000}
            """.trimIndent(),
            fetchedAtEpochMillis = 5_000,
        )

        val weather = loadCachedDashboardSections(entries = listOf(legacy), json = json).weather

        assertThat(weather?.currentTemperatureCelsius).isEqualTo(18.5)
        assertThat(weather?.uvIndex).isNull()
        assertThat(weather?.hourlyForecast?.single()?.uvIndex).isNull()
    }

    @Test
    fun `emergency care cache round trips several hospitals nearest first with ownership`() {
        val snapshot = EmergencyCareSnapshot(
            status = EmergencyCareStatus.READY,
            countryCode = "ES",
            countryName = "Spain",
            searchRadiusKilometers = 25.0,
            facilities = listOf(
                EmergencyCareFacility("a", "Clinica Privada", "Calle 1", 2.4, 36.1, -5.6, "hospital", HospitalOwnership.PRIVATE),
                EmergencyCareFacility("b", "Hospital Publico", null, 6.8, 36.2, -5.7, "hospital", HospitalOwnership.PUBLIC),
            ),
            fetchedAt = Instant.ofEpochMilli(9_000),
            detail = "Nearby emergency hospitals within 25 km.",
        )

        val entity = requireNotNull(snapshot.toCacheEntity(json))
        val cached = loadCachedDashboardSections(entries = listOf(entity), json = json).emergencyCare

        assertThat(cached).isEqualTo(snapshot)
        assertThat(entity.payloadJson).doesNotContain("\"facility\"")
    }

    @Test
    fun `emergency care cache with only the old single facility decodes into a one item list`() {
        val legacy = DashboardSectionCacheEntity(
            sectionId = DashboardSectionId.EMERGENCY_CARE,
            payloadJson = """
                {"status":"READY","sourceName":"Google Places","countryCode":"ES","countryName":"Spain",
                 "searchRadiusKilometers":10.0,"locationSource":"DEVICE",
                 "facility":{"placeId":"h1","name":"Hospital Punta Europa","address":"Carretera Getares",
                             "distanceKilometers":18.6,"latitude":36.1184,"longitude":-5.4534,"primaryType":"hospital"},
                 "fetchedAtEpochMillis":9000,"detail":"Nearest hospital found within 10 km.","note":"Using device location."}
            """.trimIndent(),
            fetchedAtEpochMillis = 9_000,
        )

        val cached = loadCachedDashboardSections(entries = listOf(legacy), json = json).emergencyCare

        assertThat(cached?.facilities?.map { it.name }).containsExactly("Hospital Punta Europa")
        assertThat(cached?.facilities?.single()?.ownership).isEqualTo(HospitalOwnership.UNKNOWN)
        assertThat(cached?.facilities?.single()?.distanceKilometers).isEqualTo(18.6)
    }

    @Test
    fun `emergency care cache without any hospital decodes to an empty list`() {
        val snapshot = EmergencyCareSnapshot(
            status = EmergencyCareStatus.UNAVAILABLE,
            fetchedAt = Instant.ofEpochMilli(9_000),
            detail = "No nearby hospitals found within 25 km.",
        )

        val entity = requireNotNull(snapshot.toCacheEntity(json))

        assertThat(loadCachedDashboardSections(listOf(entity), json).emergencyCare?.facilities).isEmpty()
    }

    @Test
    fun `air quality cache round trips with forecast and pollutant`() {
        val snapshot = AirQualitySnapshot(
            latitude = 60.2,
            longitude = 24.9,
            sourceName = "Open-Meteo",
            europeanAqi = 44,
            mainPollutant = AirQualityPollutant.PM2_5,
            pm2_5 = 14.2,
            pm10 = 31.5,
            ozone = null,
            nitrogenDioxide = 12.4,
            forecastSlots = listOf(
                AirQualityForecastSlot(Instant.ofEpochMilli(3_600_000), 48),
                AirQualityForecastSlot(Instant.ofEpochMilli(7_200_000), null),
            ),
            fetchedAt = Instant.ofEpochMilli(1_000),
        )

        val entity = snapshot.toCacheEntity(json)
        val cached = loadCachedDashboardSections(entries = listOf(entity), json = json)

        assertThat(cached.airQuality).isEqualTo(snapshot)
        assertThat(entity.fetchedAtEpochMillis).isEqualTo(1_000)
    }

    @Test
    fun `corrupt air quality payload is ignored`() {
        val corrupt = DashboardSectionCacheEntity(
            sectionId = DashboardSectionId.AIR_QUALITY,
            payloadJson = "{not json",
            fetchedAtEpochMillis = 1,
        )

        assertThat(loadCachedDashboardSections(listOf(corrupt), json).airQuality).isNull()
    }
}
