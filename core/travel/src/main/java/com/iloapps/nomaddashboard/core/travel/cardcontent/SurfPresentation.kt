package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.model.MarineForecastSlot
import com.iloapps.nomaddashboard.core.model.MarineSnapshot
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** One surf stat: a label over a primary value and, when there is one, a secondary line ("1.2 m" over "8 s"). */
data class SurfStat(
    val label: String,
    val primary: String,
    val secondary: String?,
)

/** One of the equal forecast columns under the surf stats. */
data class SurfForecastColumn(
    /** "+3h". */
    val label: String,
    val wave: String,
    val windSpeed: String,
    val windDirection: String?,
)

/**
 * The surf band of the weather card: wave, swell, wind and sea temperature in one row of equal
 * columns, then the forecast hours as equal columns. Ported from the macOS `SurfSectionPresentation`.
 */
data class SurfPresentation(
    val current: List<SurfStat>,
    val forecast: List<SurfForecastColumn>,
) {
    companion object {
        /** The forecast shows this many hours, one column each. */
        const val FORECAST_COLUMNS = 4

        fun of(marine: MarineSnapshot, now: Instant): SurfPresentation {
            val reference = marine.fetchedAt ?: now
            return SurfPresentation(
                current = listOfNotNull(
                    SurfStat(
                        label = "Wave",
                        primary = meters(marine.waveHeightMeters),
                        secondary = marine.wavePeriodSeconds?.let { "${it.roundToInt()} s" },
                    ),
                    SurfStat(
                        label = "Swell",
                        primary = meters(marine.swellHeightMeters),
                        secondary = marine.swellDirectionDegrees?.let(TravelFormatters::compassDirection),
                    ),
                    SurfStat(
                        label = "Wind",
                        primary = TravelFormatters.kilometersPerHour(marine.windSpeedKph),
                        secondary = marine.windDirectionDegrees?.let(TravelFormatters::compassDirection),
                    ),
                    marine.seaSurfaceTemperatureCelsius?.let {
                        SurfStat(label = "Sea", primary = TravelFormatters.temperature(it), secondary = null)
                    },
                ),
                forecast = marine.forecastSlots.take(FORECAST_COLUMNS).map { column(it, reference) },
            )
        }

        private fun column(slot: MarineForecastSlot, reference: Instant): SurfForecastColumn {
            val hours = ((slot.at.epochSecond - reference.epochSecond) / 3_600.0).roundToInt().coerceAtLeast(0)
            return SurfForecastColumn(
                label = "+${hours}h",
                wave = meters(slot.waveHeightMeters),
                windSpeed = TravelFormatters.kilometersPerHour(slot.windSpeedKph),
                windDirection = slot.windDirectionDegrees?.let(TravelFormatters::compassDirection),
            )
        }

        private fun meters(value: Double?): String =
            value?.let { String.format(Locale.ROOT, "%.1f m", it) } ?: TravelFormatters.Unavailable
    }
}

/** Why the surf band has no conditions to show, and what the user can do about it. */
enum class SurfSpotState {
    /** No surf spot saved yet. */
    NOT_SET,

    /** A surf spot is saved but its coordinates are out of range. */
    INVALID,

    /** A valid surf spot without marine data right now. */
    UNAVAILABLE,

    READY,
    ;

    /** The user can fix this in Settings. */
    val isActionable: Boolean
        get() = this == NOT_SET || this == INVALID

    companion object {
        fun of(surfSpot: SurfSpotConfiguration, marine: MarineSnapshot?): SurfSpotState {
            val configured = surfSpot.latitude != null && surfSpot.longitude != null
            val valid = surfSpot.latitude?.let { it in -90.0..90.0 } == true &&
                surfSpot.longitude?.let { it in -180.0..180.0 } == true
            return when {
                marine != null -> READY
                !configured -> NOT_SET
                !valid -> INVALID
                else -> UNAVAILABLE
            }
        }
    }
}
