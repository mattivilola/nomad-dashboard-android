package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.hourly
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.now
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.time
import java.time.LocalDate
import org.junit.Test

class WeatherForecastPresentationTest {
    private val today = LocalDate.of(2026, 10, 12)

    private fun day(offset: Long, min: Double? = 11.6, max: Double? = 21.4, rain: Int? = 40) = WeatherDayForecast(
        date = today.plusDays(offset),
        summary = "Partly cloudy",
        minCelsius = min,
        maxCelsius = max,
        rainChancePercent = rain,
        weatherCode = 2,
        windSpeedKph = 14.0,
        windDirectionDegrees = 315.0,
    )

    private fun weather(
        hours: List<Double> = emptyList(),
        days: List<WeatherDayForecast> = emptyList(),
    ) = WeatherSnapshot(
        currentTemperatureCelsius = 19.0,
        hourlyForecast = hours.map { hourly(it, temperature = 18.6, rain = 30, windKph = 20.0, windDegrees = 270.0) },
        dailyForecast = days,
        fetchedAt = now,
    )

    private fun forecast(weather: WeatherSnapshot, expanded: Boolean = true) =
        WeatherForecastPresentation.of(weather, time, now, expanded)

    // Wind

    @Test
    fun `wind shows the speed with its direction and metres per second beneath`() {
        val metric = WindMetric.of(WeatherSnapshot(windSpeedKph = 14.4, windDirectionDegrees = 315.0))

        assertThat(metric.primary).isEqualTo("14 km/h NW")
        assertThat(metric.secondary).isEqualTo("4.0 m/s")
    }

    @Test
    fun `wind falls back to whatever is known`() {
        assertThat(WindMetric.summary(14.0, null)).isEqualTo("14 km/h")
        assertThat(WindMetric.summary(null, 90.0)).isEqualTo("E")
        assertThat(WindMetric.summary(null, null)).isEqualTo("n/a")
        assertThat(WindMetric.of(WeatherSnapshot()).secondary).isNull()
    }

    // Hourly strip

    @Test
    fun `the strip starts with the hour under way and runs through the next day`() {
        val chips = forecast(weather(hours = (-1..24).map { it - 0.0 })).hourly

        // The slot at -1 h ended exactly now, so it is gone; the slot at 0 h is the hour under way.
        assertThat(chips.first().label).isEqualTo("Now")
        assertThat(chips[1].label).isEqualTo("13:00")
        assertThat(chips).hasSize(WeatherForecastPresentation.HOURLY_LIMIT)
    }

    @Test
    fun `a chip carries the temperature rain and wind`() {
        val chip = forecast(weather(hours = listOf(3.0))).hourly.single()

        assertThat(chip.label).isEqualTo("15:00")
        assertThat(chip.temperature).isEqualTo("19°")
        assertThat(chip.rainChance).isEqualTo("30%")
        assertThat(chip.wind).isEqualTo("20 km/h W")
        assertThat(chip.accessibilityLabel).isEqualTo("15:00, Clear, 19°, rain 30%, 20 km/h W")
    }

    @Test
    fun `a chip leaves out what the slot does not know`() {
        val slot = hourly(2.0, temperature = null)
        val chip = forecast(WeatherSnapshot(hourlyForecast = listOf(slot))).hourly.single()

        assertThat(chip.temperature).isEqualTo("n/a")
        assertThat(chip.rainChance).isNull()
        assertThat(chip.wind).isNull()
    }

    @Test
    fun `no weather hours means no strip`() {
        assertThat(forecast(weather()).hourly).isEmpty()
    }

    // Tomorrow and the outlook

    @Test
    fun `tomorrow is the day after today`() {
        val result = forecast(weather(days = (0L..6L).map(::day)), expanded = false)

        assertThat(result.tomorrow?.label).isEqualTo("Tomorrow")
        assertThat(result.tomorrow?.date).isEqualTo(today.plusDays(1))
        assertThat(result.tomorrow?.range).isEqualTo("12° / 21°")
        assertThat(result.tomorrow?.rainChance).isEqualTo("Rain 40%")
        assertThat(result.tomorrow?.wind).isEqualTo("14 km/h NW")
        assertThat(result.showsTomorrow).isTrue()
    }

    @Test
    fun `a stale forecast without tomorrow shows no tomorrow row`() {
        val result = forecast(weather(days = listOf(day(-3), day(-2))), expanded = false)

        assertThat(result.tomorrow).isNull()
        assertThat(result.showsTomorrow).isFalse()
    }

    @Test
    fun `the outlook lists seven days labelled today tomorrow then weekdays`() {
        val result = forecast(weather(days = (0L..9L).map(::day)))

        assertThat(result.days.map { it.label })
            .containsExactly("Today", "Tomorrow", "Wed", "Thu", "Fri", "Sat", "Sun").inOrder()
    }

    @Test
    fun `an expanded outlook stands in for the tomorrow row`() {
        val days = (0L..6L).map(::day)

        val expanded = forecast(weather(days = days), expanded = true)
        assertThat(expanded.showsOutlook).isTrue()
        assertThat(expanded.isOutlookExpanded).isTrue()
        assertThat(expanded.showsTomorrow).isFalse()

        val folded = forecast(weather(days = days), expanded = false)
        assertThat(folded.showsOutlook).isTrue()
        assertThat(folded.isOutlookExpanded).isFalse()
        assertThat(folded.showsTomorrow).isTrue()
    }

    @Test
    fun `without daily data there is no outlook to fold`() {
        val result = forecast(weather(), expanded = true)

        assertThat(result.showsOutlook).isFalse()
        assertThat(result.isOutlookExpanded).isFalse()
        assertThat(result.tomorrow).isNull()
    }

    @Test
    fun `a day without temperatures says n a`() {
        val result = forecast(weather(days = listOf(day(1, min = null, max = null))), expanded = false)

        assertThat(result.tomorrow?.range).isEqualTo("n/a / n/a")
    }
}
