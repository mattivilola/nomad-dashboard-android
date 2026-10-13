package com.iloapps.nomaddashboard.core.travel.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class TravelFormattersTest {
    @Test
    fun temperatureRoundsToWholeDegreesWithoutNegativeZero() {
        assertThat(TravelFormatters.temperature(19.4)).isEqualTo("19°")
        assertThat(TravelFormatters.temperature(-2.6)).isEqualTo("-3°")
        assertThat(TravelFormatters.temperature(-0.3)).isEqualTo("0°")
        assertThat(TravelFormatters.temperature(null)).isEqualTo("n/a")
    }

    @Test
    fun metricsKeepUnitsApart() {
        assertThat(TravelFormatters.megabitsPerSecond(96.04)).isEqualTo(FormattedMetric("96.0", "Mbps"))
        assertThat(TravelFormatters.latency(28.4).text).isEqualTo("28 ms")
        assertThat(TravelFormatters.watts(null).text).isEqualTo("n/a")
    }

    @Test
    fun kilometersSwitchToWholeNumbersFromTen() {
        assertThat(TravelFormatters.kilometers(4.84)).isEqualTo("4.8 km")
        assertThat(TravelFormatters.kilometers(12.4)).isEqualTo("12 km")
    }

    @Test
    fun kilometersMetricKeepsTheUnitApart() {
        assertThat(TravelFormatters.kilometersMetric(4.84)).isEqualTo(FormattedMetric("4.8", "km"))
        assertThat(TravelFormatters.kilometersMetric(12.4)).isEqualTo(FormattedMetric("12", "km"))
        assertThat(TravelFormatters.kilometersMetric(null)).isEqualTo(FormattedMetric("n/a"))
    }

    @Test
    fun minutesUseHoursAndMinutes() {
        assertThat(TravelFormatters.minutes(208)).isEqualTo("3h 28m")
        assertThat(TravelFormatters.minutes(13)).isEqualTo("13m")
        assertThat(TravelFormatters.minutes(-1)).isEqualTo("n/a")
    }

    @Test
    fun compassDirectionNormalizesBearings() {
        assertThat(TravelFormatters.compassDirection(270.0)).isEqualTo("W")
        assertThat(TravelFormatters.compassDirection(-45.0)).isEqualTo("NW")
        assertThat(TravelFormatters.compassDirection(359.0)).isEqualTo("N")
    }

    @Test
    fun fuelPriceUsesLocaleCurrencyWithThreeDecimals() {
        assertThat(TravelFormatters.fuelPrice(1.429, "EUR", Locale.forLanguageTag("es-ES"))).isEqualTo("1,429 €")
        assertThat(TravelFormatters.fuelPrice(1.429, "EUR", Locale.US)).isEqualTo("€1.429")
        assertThat(TravelFormatters.fuelPrice(1.5, "XYZ1", Locale.US)).isEqualTo("1.500 XYZ1")
    }
}
