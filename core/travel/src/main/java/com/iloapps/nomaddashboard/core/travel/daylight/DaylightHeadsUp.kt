package com.iloapps.nomaddashboard.core.travel.daylight

import com.iloapps.nomaddashboard.core.travel.format.DurationText
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpDecision
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import java.time.Duration
import java.time.Instant

/**
 * The daylight heads-up for the Road trip & RV profile.
 *
 * The [decision] title is "Sunset 18:12 · 45 min of daylight" or "Dark until 07:45", the status text
 * "Sunset 18:12" or "Dark", and the detail "Dusk (civil twilight) ends 18:40" or "Sunrise tomorrow
 * 07:45" (null when dusk is unknown). [kind] says which of the two it is.
 */
data class DaylightHeadsUp(
    val kind: Kind,
    val decision: HeadsUpDecision,
) {
    enum class Kind { SUNSET_APPROACHING, DARK }

    companion object {
        val CautionInterval: Duration = Duration.ofMinutes(60)
        val InfoInterval: Duration = Duration.ofMinutes(120)

        /** The dark heads-up waits this long after civil dusk so it does not flicker while the light fades. */
        val DarkGrace: Duration = Duration.ofMinutes(30)

        /**
         * Road trippers want to finish driving before dark: warns in the last two hours of daylight and
         * says when it is dark. Computed from the coordinates, so it also works offline. Null when there
         * is nothing to say, including in midnight sun and polar night.
         */
        fun evaluate(
            reference: DaylightReference,
            now: Instant,
            formatter: ClockTimeFormatter = ClockTimeFormatter.Hour24,
        ): DaylightHeadsUp? {
            val today = reference.solarDay(now)
            val sunrise = today.sunrise
            val sunset = today.sunset
            if (today.isPolarDay || today.isPolarNight || sunrise == null || sunset == null) {
                return null
            }

            val todayDate = reference.localDate(now)
            fun clock(time: Instant) = formatter.format(time.atZone(reference.zone))
            fun solarDay(offsetDays: Long) = reference.solarDay(todayDate.plusDays(offsetDays))

            if (now < sunrise) {
                // Before dawn: dark once the grace period after yesterday's civil dusk has passed.
                val dusk = solarDay(-1).civilDusk
                if (dusk == null || now < dusk.plus(DarkGrace)) return null
                return dark(sunrise, todayDate == reference.localDate(sunrise), ::clock)
            }

            if (now < sunset) {
                val remaining = Duration.between(now, sunset)
                if (remaining > InfoInterval) return null
                val sunsetText = clock(sunset)
                return DaylightHeadsUp(
                    kind = Kind.SUNSET_APPROACHING,
                    decision = HeadsUpDecision(
                        severity = if (remaining <= CautionInterval) HeadsUpSeverity.CAUTION else HeadsUpSeverity.INFO,
                        title = "Sunset $sunsetText · ${DurationText.hoursAndMinutes(remaining)} of daylight",
                        detail = today.civilDusk?.let { "Dusk (civil twilight) ends ${clock(it)}" },
                        statusText = "Sunset $sunsetText",
                    ),
                )
            }

            // After sunset: dark once civil twilight is over (plus the grace period), until tomorrow's sunrise.
            val dusk = today.civilDusk
            val nextSunrise = solarDay(1).sunrise
            if (dusk == null || now < dusk.plus(DarkGrace) || nextSunrise == null) return null
            return dark(nextSunrise, todayDate == reference.localDate(nextSunrise), ::clock)
        }

        private fun dark(sunrise: Instant, sunriseIsToday: Boolean, clock: (Instant) -> String): DaylightHeadsUp {
            val sunriseText = clock(sunrise)
            val day = if (sunriseIsToday) "today" else "tomorrow"
            return DaylightHeadsUp(
                kind = Kind.DARK,
                decision = HeadsUpDecision(
                    severity = HeadsUpSeverity.INFO,
                    title = "Dark until $sunriseText",
                    detail = "Sunrise $day $sunriseText",
                    statusText = "Dark",
                ),
            )
        }
    }
}
