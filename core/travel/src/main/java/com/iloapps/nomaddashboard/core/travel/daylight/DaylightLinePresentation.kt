package com.iloapps.nomaddashboard.core.travel.daylight

import java.time.Instant

/**
 * The compact "Sunrise 07:45 · Sunset 18:12" line under the weather conditions, computed on the device
 * from the device or IP location and shown in the location's time zone.
 */
data class DaylightLinePresentation(val segments: List<Segment>) {
    enum class SegmentKind { SUNRISE, SUNSET, MIDNIGHT_SUN, POLAR_NIGHT }

    data class Segment(val kind: SegmentKind, val text: String)

    /** "Sunrise 07:45 · Sunset 18:12", for accessibility. */
    val summary: String
        get() = segments.joinToString(Separator) { it.text }

    companion object {
        private const val Separator = " · "

        /** Null when there is nothing to show, so the line stays hidden. */
        fun of(
            reference: DaylightReference,
            now: Instant,
            formatter: ClockTimeFormatter = ClockTimeFormatter.Hour24,
        ): DaylightLinePresentation? {
            val day = reference.solarDay(now)
            fun clock(time: Instant) = formatter.format(time.atZone(reference.zone))

            val segments = when {
                day.isPolarDay -> listOf(Segment(SegmentKind.MIDNIGHT_SUN, "Midnight sun"))
                day.isPolarNight -> listOf(Segment(SegmentKind.POLAR_NIGHT, "Polar night"))
                else -> listOfNotNull(
                    day.sunrise?.let { Segment(SegmentKind.SUNRISE, "Sunrise ${clock(it)}") },
                    day.sunset?.let { Segment(SegmentKind.SUNSET, "Sunset ${clock(it)}") },
                )
            }
            return segments.takeIf { it.isNotEmpty() }?.let(::DaylightLinePresentation)
        }
    }
}
