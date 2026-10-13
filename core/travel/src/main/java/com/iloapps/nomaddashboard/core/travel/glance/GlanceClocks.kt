package com.iloapps.nomaddashboard.core.travel.glance

import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** "Here Valencia 11:30": a label, the city and the time there. */
data class GlanceClock(
    val label: String,
    val city: String,
    val time: String,
) {
    val text: String
        get() = "$label $city $time"
}

/**
 * The one-line clocks under the verdict: "Here <device city> 11:30 · Home <city> 12:30" plus how the
 * calendar day compares.
 *
 * @property home Null when no valid home time zone is set.
 * @property relativeDayText "Same day at home", "Tomorrow at home" or "Yesterday at home"; null without [home].
 */
data class GlanceClocks(
    val here: GlanceClock,
    val home: GlanceClock?,
    val relativeDayText: String?,
) {
    companion object {
        fun of(
            deviceCity: String?,
            homeTimeZoneId: String?,
            now: Instant,
            zone: ZoneId,
            clockTime: ClockTimeFormatter,
        ): GlanceClocks {
            val here = GlanceClock(
                label = "Here",
                city = hereCity(deviceCity, zone.id),
                time = clockTime.format(now.atZone(zone)),
            )
            val homeZone = homeTimeZoneId.trimmedNonEmpty()?.let(::zoneOrNull)
                ?: return GlanceClocks(here = here, home = null, relativeDayText = null)

            val home = GlanceClock(
                label = "Home",
                city = TimeZoneCityName.of(homeZone.id),
                time = clockTime.format(now.atZone(homeZone)),
            )
            val dayDifference = ChronoUnit.DAYS.between(
                now.atZone(homeZone).toLocalDate(),
                now.atZone(zone).toLocalDate(),
            )
            val relativeDay = when {
                dayDifference == 0L -> "Same day at home"
                dayDifference > 0 -> "Tomorrow at home"
                else -> "Yesterday at home"
            }
            return GlanceClocks(here = here, home = home, relativeDayText = relativeDay)
        }

        /**
         * "Here" prefers the device's city over the city of the time zone identifier, which can differ
         * from where the user actually is (a zone covers many towns).
         */
        fun hereCity(deviceCity: String?, timeZoneId: String): String =
            deviceCity.trimmedNonEmpty() ?: TimeZoneCityName.of(timeZoneId)

        private fun zoneOrNull(id: String): ZoneId? =
            try {
                ZoneId.of(id)
            } catch (_: DateTimeException) {
                null
            }
    }
}

/** The city part of a time zone identifier: "Los Angeles" for "America/Los_Angeles". */
object TimeZoneCityName {
    fun of(timeZoneId: String): String =
        timeZoneId.substringAfterLast('/').replace('_', ' ')
}
