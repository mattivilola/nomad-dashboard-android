package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.MarineForecastSlot
import com.iloapps.nomaddashboard.core.model.MarineSnapshot
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.at
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.now
import org.junit.Test

class SurfPresentationTest {
    private fun marine(slots: Int = 6, sea: Double? = 18.4) = MarineSnapshot(
        spotName = "Tarifa",
        waveHeightMeters = 1.24,
        wavePeriodSeconds = 8.4,
        swellHeightMeters = 1.1,
        swellDirectionDegrees = 270.0,
        windSpeedKph = 14.0,
        windDirectionDegrees = 45.0,
        seaSurfaceTemperatureCelsius = sea,
        forecastSlots = (1..slots).map {
            MarineForecastSlot(
                at = at(it * 3.0),
                waveHeightMeters = 0.9 + it / 10.0,
                swellHeightMeters = 1.0,
                windSpeedKph = 10.0 + it,
                windDirectionDegrees = 90.0,
            )
        },
        fetchedAt = now,
    )

    @Test
    fun `current conditions are four stats in one row`() {
        val result = SurfPresentation.of(marine(), now)

        assertThat(result.current.map { it.label }).containsExactly("Wave", "Swell", "Wind", "Sea").inOrder()
        assertThat(result.current[0]).isEqualTo(SurfStat("Wave", "1.2 m", "8 s"))
        assertThat(result.current[1]).isEqualTo(SurfStat("Swell", "1.1 m", "W"))
        assertThat(result.current[2]).isEqualTo(SurfStat("Wind", "14 km/h", "NE"))
        assertThat(result.current[3]).isEqualTo(SurfStat("Sea", "18°", null))
    }

    @Test
    fun `the sea stat is left out without a sea temperature`() {
        assertThat(SurfPresentation.of(marine(sea = null), now).current.map { it.label })
            .containsExactly("Wave", "Swell", "Wind").inOrder()
    }

    @Test
    fun `the forecast is four equal columns`() {
        val columns = SurfPresentation.of(marine(slots = 6), now).forecast

        assertThat(columns).hasSize(SurfPresentation.FORECAST_COLUMNS)
        assertThat(columns.first()).isEqualTo(SurfForecastColumn("+3h", "1.0 m", "11 km/h", "E"))
        assertThat(columns.map { it.label }).containsExactly("+3h", "+6h", "+9h", "+12h").inOrder()
    }

    @Test
    fun `missing readings say n a`() {
        val result = SurfPresentation.of(MarineSnapshot(spotName = "Tarifa"), now)

        assertThat(result.current[0]).isEqualTo(SurfStat("Wave", "n/a", null))
        assertThat(result.current[2]).isEqualTo(SurfStat("Wind", "n/a", null))
        assertThat(result.forecast).isEmpty()
    }

    @Test
    fun `the spot state says what to fix`() {
        val valid = SurfSpotConfiguration("Tarifa", 36.0, -5.6)

        assertThat(SurfSpotState.of(valid, marine())).isEqualTo(SurfSpotState.READY)
        assertThat(SurfSpotState.of(valid, null)).isEqualTo(SurfSpotState.UNAVAILABLE)
        assertThat(SurfSpotState.of(SurfSpotConfiguration(), null)).isEqualTo(SurfSpotState.NOT_SET)
        assertThat(SurfSpotState.of(SurfSpotConfiguration("Bad", 91.0, 0.0), null)).isEqualTo(SurfSpotState.INVALID)
        assertThat(SurfSpotState.NOT_SET.isActionable).isTrue()
        assertThat(SurfSpotState.INVALID.isActionable).isTrue()
        assertThat(SurfSpotState.UNAVAILABLE.isActionable).isFalse()
        assertThat(SurfSpotState.READY.isActionable).isFalse()
    }
}
