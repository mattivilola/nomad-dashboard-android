package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AirQualityForecastSlot
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.at
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.hourly
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.now
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.time
import org.junit.Test

class WeatherAirPresentationTest {
    // UV

    private fun weather(uv: Int?, slots: List<Pair<Double, Int?>> = emptyList()) = WeatherSnapshot(
        currentTemperatureCelsius = 24.0,
        uvIndex = uv,
        hourlyForecast = slots.map { (hours, slotUv) -> hourly(hours, uv = slotUv) },
        fetchedAt = now,
    )

    private fun uv(weather: WeatherSnapshot) = WeatherUvPresentation.of(weather, time, now)

    @Test
    fun `uv names the who band`() {
        assertThat(uv(weather(7))?.value).isEqualTo("7 (high)")
        assertThat(uv(weather(7))?.text).isEqualTo("UV 7 (high)")
        assertThat(uv(weather(2))?.value).isEqualTo("2 (low)")
        assertThat(uv(weather(4))?.value).isEqualTo("4 (moderate)")
        assertThat(uv(weather(9))?.value).isEqualTo("9 (very high)")
        assertThat(uv(weather(11))?.value).isEqualTo("11 (extreme)")
    }

    @Test
    fun `uv tints follow the who bands`() {
        assertThat(uv(weather(5))?.tone).isEqualTo(CardTone.NEUTRAL)
        assertThat(uv(weather(6))?.tone).isEqualTo(CardTone.CAUTION)
        assertThat(uv(weather(8))?.tone).isEqualTo(CardTone.CAUTION)
        assertThat(uv(weather(10))?.tone).isEqualTo(CardTone.CAUTION)
        assertThat(uv(weather(11))?.tone).isEqualTo(CardTone.ATTENTION)
    }

    @Test
    fun `uv needs a reading`() {
        assertThat(uv(weather(null))).isNull()
    }

    @Test
    fun `uv shows the peak of the rest of today when it is higher`() {
        // Slots at 13:00, 14:00 and tomorrow 14:00.
        val result = uv(weather(7, listOf(1.0 to 9, 2.0 to 8, 26.0 to 11)))

        assertThat(result?.peak).isEqualTo("peak 9 at 13:00")
        assertThat(result?.tone).isEqualTo(CardTone.CAUTION)
    }

    @Test
    fun `uv has no peak when nothing higher follows today`() {
        assertThat(uv(weather(9, listOf(1.0 to 8, 26.0 to 11)))?.peak).isNull()
        assertThat(uv(weather(9))?.peak).isNull()
    }

    @Test
    fun `uv ignores hours that are already over`() {
        assertThat(uv(weather(5, listOf(-2.0 to 9)))?.peak).isNull()
    }

    @Test
    fun `uv peak time reads in the place's own clock`() {
        val tokyo = CardTime(java.time.ZoneId.of("Asia/Tokyo"), time.formats)
        // 12:00 UTC is 21:00 in Tokyo, so +1 h is still the same Tokyo day and reads 22:00.
        val result = WeatherUvPresentation.of(weather(3, listOf(1.0 to 6)), tokyo, now)

        assertThat(result?.peak).isEqualTo("peak 6 at 22:00")
    }

    // Air quality

    private fun airQuality(aqi: Int, pollutant: AirQualityPollutant? = AirQualityPollutant.PM10, outlook: List<Int?> = emptyList()) =
        AirQualitySnapshot(
            latitude = 34.0,
            longitude = -6.8,
            sourceName = "Open-Meteo",
            europeanAqi = aqi,
            mainPollutant = pollutant,
            pm2_5 = 12.0,
            pm10 = 30.4,
            ozone = 80.0,
            nitrogenDioxide = 14.0,
            forecastSlots = outlook.mapIndexed { index, value -> AirQualityForecastSlot(at((index + 1).toDouble()), value) },
            fetchedAt = now,
        )

    private val locationWeather = AppSettings(useCurrentLocationForWeather = true)

    private fun aqi(snapshot: AirQualitySnapshot?, settings: AppSettings = locationWeather) =
        WeatherAirQualityPresentation.of(snapshot, settings, time, now)

    @Test
    fun `the headline has the index, the band and the main pollutant`() {
        assertThat(aqi(airQuality(38, AirQualityPollutant.PM10))?.headline).isEqualTo("AQI 38 · Fair · mostly PM10")
        assertThat(aqi(airQuality(64, AirQualityPollutant.OZONE))?.headline).isEqualTo("AQI 64 · Poor · mostly ozone")
        assertThat(aqi(airQuality(12, null))?.headline).isEqualTo("AQI 12 · Good")
    }

    @Test
    fun `band tints stay neutral until poor`() {
        assertThat(aqi(airQuality(10))?.tone).isEqualTo(CardTone.NEUTRAL)
        assertThat(aqi(airQuality(39))?.tone).isEqualTo(CardTone.NEUTRAL)
        assertThat(aqi(airQuality(59))?.tone).isEqualTo(CardTone.NEUTRAL)
        assertThat(aqi(airQuality(60))?.tone).isEqualTo(CardTone.CAUTION)
        assertThat(aqi(airQuality(79))?.tone).isEqualTo(CardTone.CAUTION)
        assertThat(aqi(airQuality(80))?.tone).isEqualTo(CardTone.ATTENTION)
        assertThat(aqi(airQuality(140))?.tone).isEqualTo(CardTone.ATTENTION)
    }

    @Test
    fun `the outlook is a bar per hour with its peak`() {
        val result = aqi(airQuality(38, outlook = listOf(40, 55, 64, 50)))!!

        assertThat(result.bars).hasSize(4)
        assertThat(result.bars.map { it.tone })
            .containsExactly(CardTone.NEUTRAL, CardTone.NEUTRAL, CardTone.CAUTION, CardTone.NEUTRAL).inOrder()
        assertThat(result.bars[2].height).isWithin(0.0001).of(0.64)
        assertThat(result.bars[2].help).isEqualTo("15:00 · AQI 64 · Poor")
        assertThat(result.outlookCaption).isEqualTo("Next 4 h · peak 64 at 15:00")
    }

    @Test
    fun `a clean hour still has a visible bar and a terrible one is capped`() {
        val result = aqi(airQuality(38, outlook = listOf(2, 250)))!!

        assertThat(result.bars[0].height).isWithin(0.0001).of(0.08)
        assertThat(result.bars[1].height).isEqualTo(1.0)
    }

    @Test
    fun `missing outlook hours are skipped`() {
        val result = aqi(airQuality(38, outlook = listOf(null, 50)))!!

        assertThat(result.bars).hasSize(1)
        assertThat(aqi(airQuality(38))?.outlookCaption).isNull()
        assertThat(aqi(airQuality(38))?.bars).isEmpty()
    }

    @Test
    fun `hours that ended more than an hour ago are skipped`() {
        val snapshot = airQuality(38).copy(
            forecastSlots = listOf(AirQualityForecastSlot(at(-2.0), 90), AirQualityForecastSlot(at(1.0), 30)),
        )

        assertThat(aqi(snapshot)?.bars).hasSize(1)
    }

    @Test
    fun `air quality needs the setting and the weather location`() {
        val snapshot = airQuality(38)

        assertThat(aqi(snapshot, locationWeather.copy(airQualityEnabled = false))).isNull()
        assertThat(aqi(snapshot, AppSettings(airQualityEnabled = true, useCurrentLocationForWeather = false))).isNull()
        assertThat(aqi(null)).isNull()
        assertThat(aqi(snapshot)).isNotNull()
    }

    @Test
    fun `the help lists the concentrations`() {
        val help = aqi(airQuality(38))?.help

        assertThat(help).contains("PM2.5 12 µg/m³")
        assertThat(help).contains("PM10 30 µg/m³")
        assertThat(help).contains("ozone 80 µg/m³")
        assertThat(help).contains("NO₂ 14 µg/m³")
    }
}
