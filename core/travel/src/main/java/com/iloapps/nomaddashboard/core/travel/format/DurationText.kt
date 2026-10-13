package com.iloapps.nomaddashboard.core.travel.format

import java.time.Duration

/**
 * The one way a short duration is written, ported from the macOS `DurationText`, so every surface
 * (heads-ups, spoken answers and widgets) reads the same.
 */
object DurationText {
    /** "45 min", "1 h" or "3 h 11 min", rounded to the nearest minute (at least one minute). */
    fun hoursAndMinutes(duration: Duration): String {
        val minutes = maxOf(Math.round(duration.toMillis() / 60_000.0), 1L)
        val hours = minutes / 60
        val remainder = minutes % 60
        return when {
            hours == 0L -> "$remainder min"
            remainder == 0L -> "$hours h"
            else -> "$hours h $remainder min"
        }
    }
}
