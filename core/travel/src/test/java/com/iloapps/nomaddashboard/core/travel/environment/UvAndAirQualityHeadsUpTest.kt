package com.iloapps.nomaddashboard.core.travel.environment

import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpDecision
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.iloapps.nomaddashboard.core.model.AirQualityForecastSlot
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Test

class UvAndAirQualityHeadsUpTest {
    // Fixtures

    private val now = Instant.ofEpochSecond(1_791_806_400) // 2026-10-12 12:00:00 UTC == 14:00 Paris
    private val paris = ZoneId.of("Europe/Paris")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(paris)

    /** Stand-in for the computed sunset: 4.5 hours after [now]. */
    private val sunset = now.plusSeconds(4 * 3_600 + 1_800)

    /** With a location the daylight ends at sunset. */
    private val untilSunset = RemainingDaylight(start = now, endExclusive = sunset)

    /** Without a location the rest of the calendar day counts (the end of the Paris day). */
    private val untilMidnight = RemainingDaylight(
        start = now,
        endExclusive = now.atZone(paris).toLocalDate().plusDays(1).atStartOfDay(paris).toInstant(),
    )

    private fun clock(hours: Double): String = clockFormat.format(at(hours))

    private fun at(hours: Double): Instant = now.plusMillis((hours * 3_600_000).toLong())

    private fun uv(
        current: Int?,
        slots: List<HourlyUvReading> = emptyList(),
        fetchedAt: Instant = now,
        at: Instant = now,
        daylight: RemainingDaylight = untilSunset,
    ): HeadsUpDecision? =
        UvHeadsUp.evaluate(current, fetchedAt, slots, at, daylight) { clockFormat.format(it) }

    private fun slot(hours: Double, uv: Int?) = HourlyUvReading(at(hours), uv)

    private fun airQuality(
        aqi: Int,
        main: AirQualityPollutant? = AirQualityPollutant.PM2_5,
        slots: List<AirQualityForecastSlot> = emptyList(),
        fetchedAt: Instant = now,
    ) = AirQualitySnapshot(
        latitude = 48.9,
        longitude = 2.4,
        sourceName = "Open-Meteo",
        europeanAqi = aqi,
        mainPollutant = main,
        pm2_5 = 20.0,
        pm10 = 30.0,
        ozone = 50.0,
        nitrogenDioxide = 15.0,
        forecastSlots = slots,
        fetchedAt = fetchedAt,
    )

    private fun aqiSlot(hours: Double, aqi: Int?) = AirQualityForecastSlot(at(hours), aqi)

    private fun aq(snapshot: AirQualitySnapshot?): HeadsUpDecision? =
        AirQualityHeadsUp.evaluate(snapshot, now) { clockFormat.format(it) }

    // UV index

    @Test
    fun `uv below six produces nothing`() {
        for (uv in listOf(0, 3, 5)) {
            assertThat(uv(current = uv, slots = listOf(slot(1.0, uv)))).isNull()
        }
        assertThat(uv(current = null, slots = listOf(slot(1.0, null)))).isNull()
    }

    @Test
    fun `uv category thresholds follow WHO bands`() {
        val expectations = listOf(
            6 to HeadsUpSeverity.INFO, 7 to HeadsUpSeverity.INFO, 8 to HeadsUpSeverity.CAUTION,
            10 to HeadsUpSeverity.CAUTION, 11 to HeadsUpSeverity.ATTENTION, 14 to HeadsUpSeverity.ATTENTION,
        )
        for ((uv, severity) in expectations) {
            val decision = uv(current = 0, slots = listOf(slot(1.0, uv)))!!
            assertWithMessage("UV $uv").that(decision.severity).isEqualTo(severity)
            assertThat(decision.statusText).isEqualTo("UV $uv")
        }
    }

    @Test
    fun `extreme uv is attention with the contiguous time range`() {
        val decision = uv(current = 5, slots = listOf(slot(1.0, 11), slot(2.0, 12), slot(3.0, 7)))!!

        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
        assertThat(decision.title).isEqualTo("UV extreme (12) ${clock(1.0)}–${clock(2.0)}")
        assertThat(decision.detail).isEqualTo("Avoid midday sun; cover up and use SPF 30+")
        assertThat(decision.statusText).isEqualTo("UV 12")
    }

    @Test
    fun `very high uv is caution and names only the start`() {
        val decision = uv(current = 4, slots = listOf(slot(1.0, 9), slot(2.0, 9), slot(3.0, 6)))!!

        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(decision.title).isEqualTo("UV very high (9) from ${clock(1.0)}")
        assertThat(decision.detail).isEqualTo("Seek shade at midday; SPF 30+")
        assertThat(decision.statusText).isEqualTo("UV 9")
    }

    @Test
    fun `high uv is info`() {
        val decision = uv(current = 3, slots = listOf(slot(1.0, 6), slot(2.0, 7), slot(3.0, 6)))!!

        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(decision.title).isEqualTo("UV high (7) ${clock(1.0)}–${clock(3.0)}")
        assertThat(decision.detail).isEqualTo("Hat and sunscreen around midday")
    }

    @Test
    fun `uv range stops at a gap below the category floor`() {
        // 8, then 3, then 9: the peak (9) is only the last reading, so the range does not bridge the dip.
        val decision = uv(current = 0, slots = listOf(slot(1.0, 8), slot(2.0, 3), slot(3.0, 9)))!!
        assertThat(decision.title).isEqualTo("UV very high (9) from ${clock(3.0)}")
    }

    @Test
    fun `uv peak ties keep the earliest run`() {
        val decision = uv(current = 0, slots = listOf(slot(1.0, 7), slot(2.0, 3), slot(3.0, 7)))!!
        assertThat(decision.title).isEqualTo("UV high (7) around ${clock(1.0)}")
    }

    @Test
    fun `slots are read in time order whatever the input order`() {
        val decision = uv(current = 3, slots = listOf(slot(3.0, 6), slot(1.0, 6), slot(2.0, 7)))!!
        assertThat(decision.title).isEqualTo("UV high (7) ${clock(1.0)}–${clock(3.0)}")
    }

    @Test
    fun `current uv counts as now when fresh and is ignored when stale`() {
        assertThat(uv(current = 9)!!.title).isEqualTo("UV very high (9) from now")
        assertThat(uv(current = 7, slots = listOf(slot(1.0, 7)))!!.title).isEqualTo("UV high (7) now–${clock(1.0)}")
        assertThat(uv(current = 7)!!.title).isEqualTo("UV high (7) now")
        assertThat(uv(current = 9, fetchedAt = now.minusSeconds(3 * 3_600))).isNull()
    }

    @Test
    fun `uv ignores past slots and everything after sunset`() {
        assertThat(uv(current = 0, slots = listOf(slot(-1.0, 11)))).isNull()

        // Slots after sunset are dropped even when they carry UV; the last daylight slot still counts.
        val beyondSunset = 4.5 + 0.5
        assertThat(uv(current = 0, slots = listOf(slot(beyondSunset, 9)))).isNull()
        assertThat(uv(current = 0, slots = listOf(slot(beyondSunset - 1, 9)))).isNotNull()

        // After sunset the current reading no longer counts either.
        val evening = sunset.plusSeconds(3_600)
        assertThat(uv(current = 9, fetchedAt = evening, at = evening, daylight = RemainingDaylight(evening, sunset))).isNull()
    }

    @Test
    fun `uv without a location falls back to the rest of the calendar day`() {
        assertThat(uv(current = 0, slots = listOf(slot(2.0, 8)), daylight = untilMidnight)).isNotNull()
        // 12 hours ahead is the next calendar day in Paris.
        assertThat(uv(current = 0, slots = listOf(slot(12.0, 8)), daylight = untilMidnight)).isNull()
    }

    // Air quality

    @Test
    fun `air quality thresholds follow european aqi bands`() {
        for (aqi in listOf(0, 20, 39, 40, 59)) {
            assertWithMessage("AQI $aqi").that(aq(airQuality(aqi))).isNull()
        }

        val expectations = listOf(
            60 to HeadsUpSeverity.CAUTION, 79 to HeadsUpSeverity.CAUTION, 80 to HeadsUpSeverity.ATTENTION,
            100 to HeadsUpSeverity.ATTENTION, 140 to HeadsUpSeverity.ATTENTION,
        )
        for ((aqi, severity) in expectations) {
            val decision = aq(airQuality(aqi))!!
            assertWithMessage("AQI $aqi").that(decision.severity).isEqualTo(severity)
            assertThat(decision.statusText).isEqualTo("AQI $aqi")
        }
    }

    @Test
    fun `very poor air quality is attention`() {
        val decision = aq(airQuality(86, AirQualityPollutant.PM2_5))!!

        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
        assertThat(decision.title).isEqualTo("Air quality very poor (AQI 86)")
        assertThat(decision.detail).isEqualTo("Mostly PM2.5 · limit time outdoors")
        assertThat(decision.statusText).isEqualTo("AQI 86")
    }

    @Test
    fun `poor air quality is caution`() {
        val decision = aq(airQuality(64, AirQualityPollutant.PM10))!!

        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(decision.title).isEqualTo("Air quality poor (AQI 64)")
        assertThat(decision.detail).isEqualTo("Mostly PM10 · consider shorter outdoor activity")
    }

    @Test
    fun `extremely poor air quality keeps attention and names the band`() {
        val decision = aq(airQuality(120, AirQualityPollutant.OZONE))!!
        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.ATTENTION)
        assertThat(decision.title).isEqualTo("Air quality extremely poor (AQI 120)")
        assertThat(decision.detail).isEqualTo("Mostly ozone · limit time outdoors")
    }

    @Test
    fun `air quality without a main pollutant omits the source`() {
        assertThat(aq(airQuality(70, main = null))!!.detail).isEqualTo("Consider shorter outdoor activity")
    }

    @Test
    fun `air quality forecast to reach poor warns when it is not poor now`() {
        val decision = aq(
            airQuality(30, slots = listOf(aqiSlot(1.0, 35), aqiSlot(4.0, 62), aqiSlot(5.0, 72))),
        )!!

        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(decision.title).isEqualTo("Air quality worsening from ${clock(4.0)}")
        assertThat(decision.detail).isEqualTo("Forecast AQI up to 72 · now 30")
        assertThat(decision.statusText).isEqualTo("AQI ↑72")
    }

    @Test
    fun `air quality forecast ignores past late and non poor slots`() {
        assertThat(aq(airQuality(30, slots = listOf(aqiSlot(2.0, 59), aqiSlot(6.0, 55))))).isNull()
        assertThat(aq(airQuality(30, slots = listOf(aqiSlot(-2.0, 90))))).isNull()
        assertThat(aq(airQuality(30, slots = listOf(aqiSlot(13.0, 90))))).isNull()
        assertThat(aq(airQuality(30, slots = listOf(aqiSlot(12.0, 90))))).isNotNull()
        assertThat(aq(airQuality(30, slots = listOf(aqiSlot(2.0, null))))).isNull()
    }

    @Test
    fun `current poor air quality wins over the forecast warning`() {
        val decision = aq(airQuality(65, AirQualityPollutant.PM10, slots = listOf(aqiSlot(3.0, 90))))!!
        assertThat(decision.title).isEqualTo("Air quality poor (AQI 65)")
    }

    @Test
    fun `stale or missing air quality produces nothing`() {
        assertThat(aq(airQuality(90, fetchedAt = now.minusSeconds(3 * 3_600)))).isNull()
        assertThat(aq(null)).isNull()
    }
}
