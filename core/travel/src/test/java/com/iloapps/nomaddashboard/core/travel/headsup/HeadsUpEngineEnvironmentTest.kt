package com.iloapps.nomaddashboard.core.travel.headsup

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import java.time.Duration
import java.time.Instant
import org.junit.Test

/** The UV index and air quality heads-ups through the engine (macOS UVAndAirQualityInsightTests). */
class HeadsUpEngineEnvironmentTest {
    private fun uvItems(
        weather: WeatherSnapshot,
        hasLocation: Boolean = true,
        at: Instant = Now,
    ): List<HeadsUpItem> =
        insights(snapshot(weather = weather, travelContext = if (hasLocation) ParisLocation else TravelContextSnapshot()), at = at)

    private fun uv(
        uv: Int?,
        windKph: Double? = null,
        rain: Int? = null,
        slots: List<WeatherHourlyForecastSlot> = emptyList(),
        fetchedAt: Instant? = Now,
    ) = weather(windKph = windKph, rainChancePercent = rain, slots = slots, fetchedAt = fetchedAt, uvIndex = uv)

    private fun aqiItems(airQuality: AirQualitySnapshot, settings: AppSettings = settings()) =
        insights(snapshot(airQuality = airQuality), settings)

    // region UV index

    @Test
    fun `UV below six produces nothing`() {
        for (uv in listOf(0, 3, 5)) {
            assertThat(uvItems(uv(uv, slots = listOf(slot(hours = 1.0, uv = uv))))).isEmpty()
        }
        assertThat(uvItems(uv(null, slots = listOf(slot(hours = 1.0, uv = null))))).isEmpty()
    }

    @Test
    fun `UV category thresholds follow the WHO bands`() {
        val expectations = listOf(
            6 to HeadsUpSeverity.INFO,
            7 to HeadsUpSeverity.INFO,
            8 to HeadsUpSeverity.CAUTION,
            10 to HeadsUpSeverity.CAUTION,
            11 to HeadsUpSeverity.ATTENTION,
            14 to HeadsUpSeverity.ATTENTION,
        )
        for ((uv, severity) in expectations) {
            val item = uvItems(uv(0, slots = listOf(slot(hours = 1.0, uv = uv)))).single()
            assertThat(item.kind).isEqualTo(HeadsUpKind.UV_INDEX)
            assertWithMessage("UV $uv").that(item.severity).isEqualTo(severity)
            assertThat(item.statusText).isEqualTo("UV $uv")
            assertThat(item.relatedCard).isEqualTo(DashboardCardId.WEATHER)
        }
    }

    @Test
    fun `extreme UV is an attention with the contiguous time range`() {
        val weather = uv(5, slots = listOf(slot(hours = 1.0, uv = 11), slot(hours = 2.0, uv = 12), slot(hours = 3.0, uv = 7)))
        val item = uvItems(weather).single()

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
        assertThat(item.title).isEqualTo("UV extreme (12) ${parisClock(1.0)}–${parisClock(2.0)}")
        assertThat(item.detail).isEqualTo("Avoid midday sun; cover up and use SPF 30+")
        assertThat(item.statusText).isEqualTo("UV 12")
    }

    @Test
    fun `very high UV is a caution and names only the start`() {
        val weather = uv(4, slots = listOf(slot(hours = 1.0, uv = 9), slot(hours = 2.0, uv = 9), slot(hours = 3.0, uv = 6)))
        val item = uvItems(weather).single()

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(item.title).isEqualTo("UV very high (9) from ${parisClock(1.0)}")
        assertThat(item.detail).isEqualTo("Seek shade at midday; SPF 30+")
        assertThat(item.statusText).isEqualTo("UV 9")
    }

    @Test
    fun `high UV is an info`() {
        val weather = uv(3, slots = listOf(slot(hours = 1.0, uv = 6), slot(hours = 2.0, uv = 7), slot(hours = 3.0, uv = 6)))
        val item = uvItems(weather).single()

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(item.title).isEqualTo("UV high (7) ${parisClock(1.0)}–${parisClock(3.0)}")
        assertThat(item.detail).isEqualTo("Hat and sunscreen around midday")
    }

    @Test
    fun `the UV range stops at a gap below the category floor`() {
        // 8, then 3, then 9: the peak (9) is only the last reading, so the range does not bridge the dip.
        val weather = uv(0, slots = listOf(slot(hours = 1.0, uv = 8), slot(hours = 2.0, uv = 3), slot(hours = 3.0, uv = 9)))

        assertThat(uvItems(weather).single().title).isEqualTo("UV very high (9) from ${parisClock(3.0)}")
    }

    @Test
    fun `UV peak ties keep the earliest run`() {
        val weather = uv(0, slots = listOf(slot(hours = 1.0, uv = 7), slot(hours = 2.0, uv = 3), slot(hours = 3.0, uv = 7)))

        assertThat(uvItems(weather).single().title).isEqualTo("UV high (7) around ${parisClock(1.0)}")
    }

    @Test
    fun `the current UV counts as now when fresh and is ignored when stale`() {
        assertThat(uvItems(uv(9, slots = emptyList())).single().title).isEqualTo("UV very high (9) from now")

        val withSlot = uvItems(uv(7, slots = listOf(slot(hours = 1.0, uv = 7)))).single()
        assertThat(withSlot.title).isEqualTo("UV high (7) now–${parisClock(1.0)}")

        assertThat(uvItems(uv(7, slots = emptyList())).single().title).isEqualTo("UV high (7) now")

        val stale = uv(9, slots = emptyList(), fetchedAt = Now.minus(Duration.ofHours(3)))
        assertThat(uvItems(stale)).isEmpty()

        // A weather snapshot that was never fetched has no trustworthy current reading either.
        assertThat(uvItems(uv(9, slots = emptyList(), fetchedAt = null))).isEmpty()
    }

    @Test
    fun `UV ignores past slots and everything after sunset`() {
        val sunset = ParisSun.sunset!!
        assertThat(uvItems(uv(0, slots = listOf(slot(hours = -1.0, uv = 11))))).isEmpty()

        // Slots after sunset are dropped even when they carry UV; the last daylight slot still counts.
        val beyondSunset = Duration.between(Now, sunset).toMillis() / 3_600_000.0 + 0.5
        assertThat(uvItems(uv(0, slots = listOf(slot(hours = beyondSunset, uv = 9))))).isEmpty()
        val beforeSunset = uvItems(uv(0, slots = listOf(slot(hours = beyondSunset - 1, uv = 9))))
        assertThat(beforeSunset.kinds()).containsExactly(HeadsUpKind.UV_INDEX)

        // After sunset the current reading no longer counts either.
        val evening = sunset.plus(Duration.ofHours(1))
        assertThat(uvItems(uv(9, slots = emptyList(), fetchedAt = evening), at = evening)).isEmpty()
    }

    @Test
    fun `UV without a location falls back to the rest of the calendar day`() {
        val sameDay = uv(0, slots = listOf(slot(hours = 2.0, uv = 8)))
        assertThat(uvItems(sameDay, hasLocation = false).kinds()).containsExactly(HeadsUpKind.UV_INDEX)

        val nextDay = uv(0, slots = listOf(slot(hours = 12.0, uv = 8)))
        assertThat(uvItems(nextDay, hasLocation = false)).isEmpty()
    }

    // endregion

    // region Air quality

    @Test
    fun `air quality thresholds follow the European AQI bands`() {
        for (aqi in listOf(0, 20, 39, 40, 59)) {
            assertWithMessage("AQI $aqi").that(aqiItems(airQuality(aqi))).isEmpty()
        }

        val expectations = listOf(
            60 to HeadsUpSeverity.CAUTION,
            79 to HeadsUpSeverity.CAUTION,
            80 to HeadsUpSeverity.ATTENTION,
            100 to HeadsUpSeverity.ATTENTION,
            140 to HeadsUpSeverity.ATTENTION,
        )
        for ((aqi, severity) in expectations) {
            val item = aqiItems(airQuality(aqi)).single()
            assertThat(item.kind).isEqualTo(HeadsUpKind.AIR_QUALITY)
            assertWithMessage("AQI $aqi").that(item.severity).isEqualTo(severity)
            assertThat(item.statusText).isEqualTo("AQI $aqi")
            assertThat(item.relatedCard).isEqualTo(DashboardCardId.WEATHER)
        }
    }

    @Test
    fun `very poor air quality is an attention`() {
        val item = aqiItems(airQuality(aqi = 86, main = AirQualityPollutant.PM2_5)).single()

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
        assertThat(item.title).isEqualTo("Air quality very poor (AQI 86)")
        assertThat(item.detail).isEqualTo("Mostly PM2.5 · limit time outdoors")
        assertThat(item.statusText).isEqualTo("AQI 86")
    }

    @Test
    fun `poor air quality is a caution`() {
        val item = aqiItems(airQuality(aqi = 64, main = AirQualityPollutant.PM10)).single()

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(item.title).isEqualTo("Air quality poor (AQI 64)")
        assertThat(item.detail).isEqualTo("Mostly PM10 · consider shorter outdoor activity")
    }

    @Test
    fun `extremely poor air quality keeps the attention and names the band`() {
        val item = aqiItems(airQuality(aqi = 120, main = AirQualityPollutant.OZONE)).single()

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
        assertThat(item.title).isEqualTo("Air quality extremely poor (AQI 120)")
        assertThat(item.detail).isEqualTo("Mostly ozone · limit time outdoors")
    }

    @Test
    fun `air quality without a main pollutant omits the source`() {
        assertThat(aqiItems(airQuality(aqi = 70, main = null)).single().detail)
            .isEqualTo("Consider shorter outdoor activity")
    }

    @Test
    fun `an air quality forecast that reaches poor warns when it is not poor now`() {
        val item = aqiItems(
            airQuality(aqi = 30, slots = listOf(aqiSlot(1.0, 35), aqiSlot(4.0, 62), aqiSlot(5.0, 72))),
        ).single()

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(item.title).isEqualTo("Air quality worsening from ${parisClock(4.0)}")
        assertThat(item.detail).isEqualTo("Forecast AQI up to 72 · now 30")
        assertThat(item.statusText).isEqualTo("AQI ↑72")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.WEATHER)
    }

    @Test
    fun `the air quality forecast ignores past late and non-poor slots`() {
        assertThat(aqiItems(airQuality(30, slots = listOf(aqiSlot(2.0, 59), aqiSlot(6.0, 55))))).isEmpty()
        assertThat(aqiItems(airQuality(30, slots = listOf(aqiSlot(-2.0, 90))))).isEmpty()
        assertThat(aqiItems(airQuality(30, slots = listOf(aqiSlot(13.0, 90))))).isEmpty()
        assertThat(aqiItems(airQuality(30, slots = listOf(aqiSlot(12.0, 90)))).kinds()).containsExactly(HeadsUpKind.AIR_QUALITY)
        assertThat(aqiItems(airQuality(30, slots = listOf(aqiSlot(2.0, null))))).isEmpty()
    }

    @Test
    fun `current poor air quality wins over the forecast warning`() {
        val items = aqiItems(airQuality(aqi = 65, main = AirQualityPollutant.PM10, slots = listOf(aqiSlot(3.0, 90))))

        assertThat(items).hasSize(1)
        assertThat(items.single().title).isEqualTo("Air quality poor (AQI 65)")
    }

    @Test
    fun `stale air quality produces nothing`() {
        assertThat(aqiItems(airQuality(aqi = 90, fetchedAt = Now.minus(Duration.ofHours(3))))).isEmpty()
    }

    @Test
    fun `air quality needs the setting and the device-location weather consent`() {
        val poor = airQuality(aqi = 90)

        assertThat(aqiItems(poor, settings(airQualityEnabled = false))).isEmpty()
        assertThat(aqiItems(poor, settings(useCurrentLocationForWeather = false))).isEmpty()
        assertThat(aqiItems(poor).kinds()).containsExactly(HeadsUpKind.AIR_QUALITY)
    }

    // endregion

    // region Ranking

    @Test
    fun `nomad ranks UV and air quality right after rain`() {
        val snapshot = snapshot(
            // wind caution, rain and UV info
            weather = uv(0, windKph = 45.0, rain = 80, slots = listOf(slot(hours = 1.0, uv = 7))),
            airQuality = airQuality(aqi = 90, main = AirQualityPollutant.PM2_5), // attention
            travelContext = ParisLocation,
        )
        assertThat(insights(snapshot).kinds()).containsExactly(
            HeadsUpKind.AIR_QUALITY,
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.RAIN_SOON,
            HeadsUpKind.UV_INDEX,
        ).inOrder()

        val info = snapshot(
            weather = uv(0, rain = 80, slots = listOf(slot(hours = 1.0, uv = 7))),
            travelContext = ParisLocation,
        )
        assertThat(insights(info).kinds()).containsExactly(HeadsUpKind.RAIN_SOON, HeadsUpKind.UV_INDEX).inOrder()
    }

    @Test
    fun `road trip ranks UV and air quality right after daylight`() {
        val evening = ParisSun.sunset!!.minus(Duration.ofMinutes(30)) // daylight caution
        val snapshot = snapshot(
            weather = uv(9, windKph = 45.0, fetchedAt = evening), // wind caution, UV caution
            airQuality = airQuality(aqi = 65, main = AirQualityPollutant.PM10, fetchedAt = evening), // AQI caution
            travelContext = ParisLocation,
        )

        val kinds = insights(snapshot, settings(profile = TravelProfile.ROAD_TRIP), evening).kinds()

        assertThat(kinds).containsExactly(
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.DAYLIGHT,
            HeadsUpKind.UV_INDEX,
            HeadsUpKind.AIR_QUALITY,
        ).inOrder()
    }

    // endregion
}
