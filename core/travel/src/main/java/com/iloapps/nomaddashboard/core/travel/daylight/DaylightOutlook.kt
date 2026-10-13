package com.iloapps.nomaddashboard.core.travel.daylight

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Today's light at a place, kept as plain instants so it can be stored, shown in a widget and replayed
 * later without the coordinates it was computed from.
 *
 * It holds the sunrise and sunset of the day it was made for and the sunrise after that, which is enough
 * to answer "how long until sunset" or "when does it get light" at any moment of the following day.
 */
data class DaylightOutlook(
    val sunrise: Instant?,
    val sunset: Instant?,
    /** The sunrise of the day after the one this outlook was made for. */
    val nextSunrise: Instant?,
    val isPolarDay: Boolean,
    val isPolarNight: Boolean,
    /** Time zone of the place, so clock times read the way they do there. */
    val zone: ZoneId,
) {
    sealed interface Phase {
        /** The sun is up and sets at [sunset]. */
        data class Daylight(val sunset: Instant) : Phase

        /** The sun is below the horizon. [sunrise] is the next sunrise when it is known. */
        data class Dark(val sunrise: Instant?) : Phase

        /** The sun stays up all day. */
        data object MidnightSun : Phase

        /** The sun stays down all day. */
        data object PolarNight : Phase
    }

    /** The moments at which [phaseAt] changes, in order. */
    val transitions: List<Instant>
        get() = listOfNotNull(sunrise, sunset, nextSunrise).sorted()

    /** What the light is doing at [instant], null once [instant] is past the sunrise this outlook knows about. */
    fun phaseAt(instant: Instant): Phase? {
        if (isPolarDay) return Phase.MidnightSun
        if (isPolarNight) return Phase.PolarNight
        if (sunrise == null || sunset == null) return null

        return when {
            instant < sunrise -> Phase.Dark(sunrise)
            instant < sunset -> Phase.Daylight(sunset)
            nextSunrise == null -> Phase.Dark(null)
            instant < nextSunrise -> Phase.Dark(nextSunrise)
            else -> null
        }
    }

    /** Daylight left until sunset at [instant]; null outside daylight. */
    fun daylightLeft(instant: Instant): Duration? =
        (phaseAt(instant) as? Phase.Daylight)?.let { Duration.between(instant, it.sunset).coerceAtLeast(Duration.ZERO) }

    /** A short clock time (for example "18:12" or "6:12 PM") in the place's time zone. */
    fun clockTime(instant: Instant, formatter: ClockTimeFormatter = ClockTimeFormatter.Hour24): String =
        formatter.format(instant.atZone(zone))

    /** Whether [instant] falls on the same calendar day as [reference] in the place's time zone. */
    fun isSameDay(instant: Instant, reference: Instant): Boolean =
        instant.atZone(zone).toLocalDate() == reference.atZone(zone).toLocalDate()

    companion object {
        /** Computed on the device from the reference location: no network request. */
        fun of(reference: DaylightReference, now: Instant): DaylightOutlook {
            val today = reference.solarDay(now)
            val tomorrow = reference.solarDay(reference.localDate(now).plusDays(1))
            return DaylightOutlook(
                sunrise = today.sunrise,
                sunset = today.sunset,
                nextSunrise = tomorrow.sunrise,
                isPolarDay = today.isPolarDay,
                isPolarNight = today.isPolarNight,
                zone = reference.zone,
            )
        }
    }
}
