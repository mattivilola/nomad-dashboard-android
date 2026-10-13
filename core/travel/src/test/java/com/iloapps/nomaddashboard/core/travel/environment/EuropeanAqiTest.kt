package com.iloapps.nomaddashboard.core.travel.environment

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import org.junit.Test

class EuropeanAqiTest {
    @Test
    fun `bands start at their lower bound and very poor ends at one hundred`() {
        val expectations = listOf(
            0 to EuropeanAqiBand.GOOD, 19 to EuropeanAqiBand.GOOD, 20 to EuropeanAqiBand.FAIR,
            39 to EuropeanAqiBand.FAIR, 40 to EuropeanAqiBand.MODERATE, 59 to EuropeanAqiBand.MODERATE,
            60 to EuropeanAqiBand.POOR, 79 to EuropeanAqiBand.POOR, 80 to EuropeanAqiBand.VERY_POOR,
            100 to EuropeanAqiBand.VERY_POOR, 101 to EuropeanAqiBand.EXTREMELY_POOR, 250 to EuropeanAqiBand.EXTREMELY_POOR,
        )
        for ((index, band) in expectations) {
            assertWithMessage("AQI $index").that(EuropeanAqiBand.fromIndex(index)).isEqualTo(band)
        }
        assertThat(EuropeanAqiBand.GOOD).isLessThan(EuropeanAqiBand.EXTREMELY_POOR)
        assertThat(EuropeanAqiBand.entries.map { it.title })
            .containsExactly("Good", "Fair", "Moderate", "Poor", "Very poor", "Extremely poor").inOrder()
    }

    @Test
    fun `dominant pollutant is the highest sub index with the earlier pollutant winning ties`() {
        assertThat(
            dominantAirQualityPollutant(
                mapOf(AirQualityPollutant.PM2_5 to 40.0, AirQualityPollutant.PM10 to 55.0, AirQualityPollutant.OZONE to 20.0),
            ),
        ).isEqualTo(AirQualityPollutant.PM10)
        assertThat(
            dominantAirQualityPollutant(mapOf(AirQualityPollutant.OZONE to 70.0, AirQualityPollutant.NITROGEN_DIOXIDE to 30.0)),
        ).isEqualTo(AirQualityPollutant.OZONE)
        assertThat(
            dominantAirQualityPollutant(mapOf(AirQualityPollutant.PM10 to 50.0, AirQualityPollutant.PM2_5 to 50.0)),
        ).isEqualTo(AirQualityPollutant.PM2_5)
        assertThat(dominantAirQualityPollutant(mapOf(AirQualityPollutant.SULPHUR_DIOXIDE to 12.0)))
            .isEqualTo(AirQualityPollutant.SULPHUR_DIOXIDE)
        assertThat(dominantAirQualityPollutant(mapOf(AirQualityPollutant.PM2_5 to 0.0, AirQualityPollutant.PM10 to 0.0))).isNull()
        assertThat(dominantAirQualityPollutant(emptyMap())).isNull()
    }

    @Test
    fun `pollutant display names`() {
        assertThat(AirQualityPollutant.entries.map { it.displayName })
            .containsExactly("PM2.5", "PM10", "ozone", "NO₂", "SO₂").inOrder()
    }

    @Test
    fun `coordinates are rounded to one decimal before leaving the device`() {
        val helsinki = AirQualityGridPoint.privacyRounded(60.169857, 24.938379)!!
        assertThat(helsinki.latitudeText).isEqualTo("60.2")
        assertThat(helsinki.longitudeText).isEqualTo("24.9")
        assertThat(helsinki).isEqualTo(AirQualityGridPoint(60.2, 24.9))
    }

    @Test
    fun `negative zero and southern western coordinates round cleanly`() {
        val point = AirQualityGridPoint.privacyRounded(-0.04, -33.86)!!
        assertThat(point.latitudeText).isEqualTo("0.0")
        assertThat(point.longitudeText).isEqualTo("-33.9")
        // Half away from zero, as macOS rounds.
        assertThat(AirQualityGridPoint.privacyRounded(-0.25, 0.25)).isEqualTo(AirQualityGridPoint(-0.3, 0.3))
    }

    @Test
    fun `nearby coordinates share a grid cell`() {
        assertThat(AirQualityGridPoint.privacyRounded(60.161, 24.941))
            .isEqualTo(AirQualityGridPoint.privacyRounded(60.174, 24.935))
        assertThat(AirQualityGridPoint.privacyRounded(60.4, 24.9))
            .isNotEqualTo(AirQualityGridPoint.privacyRounded(60.161, 24.941))
    }

    @Test
    fun `invalid coordinates have no grid point`() {
        assertThat(AirQualityGridPoint.privacyRounded(Double.NaN, 0.0)).isNull()
        assertThat(AirQualityGridPoint.privacyRounded(0.0, Double.POSITIVE_INFINITY)).isNull()
        assertThat(AirQualityGridPoint.privacyRounded(90.1, 0.0)).isNull()
        assertThat(AirQualityGridPoint.privacyRounded(0.0, -180.5)).isNull()
        assertThat(AirQualityGridPoint.privacyRounded(90.0, 180.0)).isNotNull()
    }
}
