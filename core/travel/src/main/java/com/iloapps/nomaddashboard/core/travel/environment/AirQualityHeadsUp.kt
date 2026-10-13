package com.iloapps.nomaddashboard.core.travel.environment

import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpDecision
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** European AQI heads-up. */
object AirQualityHeadsUp {
    /** A reading older than this is not shown. */
    val READING_MAX_AGE: Duration = Duration.ofHours(2)

    /** How far ahead a forecast that reaches "poor" is worth a warning. */
    val LOOK_AHEAD: Duration = Duration.ofHours(12)

    /**
     * European AQI of 60+ (poor) is worth a heads-up; moderate or better is not. Also warns when the
     * next hours are forecast to reach poor while the air is not poor now.
     *
     * @param clock formats an instant as a clock time in the place and locale shown to the traveller.
     */
    fun evaluate(
        airQuality: AirQualitySnapshot?,
        now: Instant,
        clock: (Instant) -> String,
    ): HeadsUpDecision? {
        if (airQuality == null || Duration.between(airQuality.fetchedAt, now) > READING_MAX_AGE) {
            return null
        }

        val aqi = airQuality.europeanAqi
        if (aqi >= EuropeanAqiBand.POOR_THRESHOLD) {
            val isVeryPoor = aqi >= EuropeanAqiBand.VERY_POOR_THRESHOLD
            val advice = if (isVeryPoor) "limit time outdoors" else "consider shorter outdoor activity"
            val detail = airQuality.mainPollutant?.let { "Mostly ${it.displayName} · $advice" }
                ?: advice.replaceFirstChar { it.titlecase(Locale.ROOT) }
            return HeadsUpDecision(
                severity = if (isVeryPoor) HeadsUpSeverity.ATTENTION else HeadsUpSeverity.CAUTION,
                title = "Air quality ${EuropeanAqiBand.fromIndex(aqi).title.lowercase(Locale.ROOT)} (AQI $aqi)",
                detail = detail,
                statusText = "AQI $aqi",
            )
        }

        val upcoming = airQuality.forecastSlots
            .filter { it.time > now && it.time <= now.plus(LOOK_AHEAD) }
            .sortedBy { it.time }
        val onset = upcoming.firstOrNull { (it.europeanAqi ?: 0) >= EuropeanAqiBand.POOR_THRESHOLD }
            ?: return null

        val peak = upcoming.mapNotNull { it.europeanAqi }.maxOrNull() ?: aqi
        return HeadsUpDecision(
            severity = HeadsUpSeverity.CAUTION,
            title = "Air quality worsening from ${clock(onset.time)}",
            detail = "Forecast AQI up to $peak · now $aqi",
            statusText = "AQI ↑$peak",
        )
    }
}
