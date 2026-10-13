package com.iloapps.nomaddashboard.core.travel.environment

import com.iloapps.nomaddashboard.core.model.AirQualityPollutant
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * European Air Quality Index (EAQI) bands.
 *
 * Sources:
 * - EEA European Air Quality Index, https://airindex.eea.europa.eu/AQI/index.html: six classes (good, fair,
 *   moderate, poor, very poor, extremely poor); the overall class is the poorest class of PM2.5, PM10, O3, NO2 and SO2.
 * - Open-Meteo Air Quality API, https://open-meteo.com/en/docs/air-quality-api (2024 EEA revision): the numeric
 *   `european_aqi` scale used here, where 0-20 is good, 20-40 fair, 40-60 moderate, 60-80 poor, 80-100 very poor
 *   and above 100 extremely poor. `european_aqi` is the maximum of the per-pollutant `european_aqi_*` indices.
 *
 * A band starts at its lower bound (an index of exactly 60 is already "poor"); 100 is still "very poor".
 * Declaration order is severity order.
 */
enum class EuropeanAqiBand(
    val title: String,
) {
    GOOD("Good"),
    FAIR("Fair"),
    MODERATE("Moderate"),
    POOR("Poor"),
    VERY_POOR("Very poor"),
    EXTREMELY_POOR("Extremely poor"),
    ;

    companion object {
        const val FAIR_THRESHOLD = 20
        const val MODERATE_THRESHOLD = 40
        const val POOR_THRESHOLD = 60
        const val VERY_POOR_THRESHOLD = 80
        const val EXTREMELY_POOR_THRESHOLD = 100

        fun fromIndex(index: Int): EuropeanAqiBand =
            when {
                index < FAIR_THRESHOLD -> GOOD
                index < MODERATE_THRESHOLD -> FAIR
                index < POOR_THRESHOLD -> MODERATE
                index < VERY_POOR_THRESHOLD -> POOR
                index <= EXTREMELY_POOR_THRESHOLD -> VERY_POOR
                else -> EXTREMELY_POOR
            }
    }
}

/**
 * The pollutant with the highest sub-index, which is the one that sets the overall European AQI.
 * Ties go to the earlier pollutant in declaration order; null when no sub-index is positive.
 */
fun dominantAirQualityPollutant(subIndices: Map<AirQualityPollutant, Double>): AirQualityPollutant? {
    var best: Pair<AirQualityPollutant, Double>? = null
    for (pollutant in AirQualityPollutant.entries) {
        val value = subIndices[pollutant]?.takeIf { it > 0 } ?: continue
        if (best == null || value > best.second) {
            best = pollutant to value
        }
    }
    return best?.first
}

/** A coordinate rounded to one decimal (~11 km), the only form that leaves the device. */
data class AirQualityGridPoint(
    val latitude: Double,
    val longitude: Double,
) {
    /** One-decimal text for the request and the cache key, e.g. "60.2". */
    val latitudeText: String get() = coordinateText(latitude)
    val longitudeText: String get() = coordinateText(longitude)

    companion object {
        /**
         * The coordinate rounded to one decimal (half away from zero), or null when it is not a valid
         * latitude/longitude. The air-quality provider must never see a more exact location.
         */
        fun privacyRounded(latitude: Double, longitude: Double): AirQualityGridPoint? {
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) {
                return null
            }
            return AirQualityGridPoint(roundToOneDecimal(latitude), roundToOneDecimal(longitude))
        }

        private fun roundToOneDecimal(value: Double): Double {
            val rounded = BigDecimal(value * 10).setScale(0, RoundingMode.HALF_UP).toDouble() / 10
            return if (rounded == 0.0) 0.0 else rounded // normalises -0.0
        }

        private fun coordinateText(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
    }
}
