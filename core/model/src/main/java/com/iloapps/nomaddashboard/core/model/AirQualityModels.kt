package com.iloapps.nomaddashboard.core.model

import java.time.Instant

/**
 * The pollutants that make up the European AQI. The declaration order is the tie-break order when
 * two pollutants have the same sub-index (the earlier one wins).
 */
enum class AirQualityPollutant(
    val displayName: String,
) {
    PM2_5("PM2.5"),
    PM10("PM10"),
    OZONE("ozone"),
    NITROGEN_DIOXIDE("NO₂"),
    SULPHUR_DIOXIDE("SO₂"),
}

data class AirQualityForecastSlot(
    val time: Instant,
    val europeanAqi: Int?,
)

/**
 * Current air quality and a 12 hour European AQI outlook for the weather location.
 *
 * The coordinates are the ~11 km grid point that was requested, never the exact device location.
 */
data class AirQualitySnapshot(
    val latitude: Double,
    val longitude: Double,
    val sourceName: String,
    val europeanAqi: Int,
    val mainPollutant: AirQualityPollutant?,
    /** Concentrations in µg/m³. */
    val pm2_5: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val nitrogenDioxide: Double?,
    /** Hourly European AQI after [fetchedAt], earliest first (up to 12). */
    val forecastSlots: List<AirQualityForecastSlot>,
    val fetchedAt: Instant,
)
