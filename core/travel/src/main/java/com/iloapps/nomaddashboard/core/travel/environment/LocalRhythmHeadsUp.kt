package com.iloapps.nomaddashboard.core.travel.environment

import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpDecision
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpText
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmDateConfidence
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmKind
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPeriod
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPlace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Local-rhythm heads-up: the one cultural or religious period that matters most in the current country.
 *
 * Ported from the macOS heads-up engine (`localRhythmInsight`); that wording was still being finalised
 * on the macOS working branch when this was ported.
 */
object LocalRhythmHeadsUp {
    const val LOOK_AHEAD_DAYS = 14

    /**
     * A full shutdown (Yom Kippur, Nyepi) that is on or starts tomorrow, else an active period, else the
     * soonest one within [LOOK_AHEAD_DAYS]. Null without a country or when nothing is near.
     *
     * @param places the device location, then the IP location (decides whether Nyepi applies).
     * @param formatDay formats a date as day and abbreviated month in the traveller's locale, e.g. "8 Feb".
     */
    fun evaluate(
        calendar: LocalRhythmCalendar,
        countryCode: String?,
        places: List<LocalRhythmPlace>,
        now: Instant,
        zone: ZoneId,
        formatDay: (LocalDate) -> String,
    ): HeadsUpDecision? {
        val country = countryCode ?: return null

        val today = now.atZone(zone).toLocalDate()

        // Earliest first; the calendar only returns periods that are active or start within the window.
        val candidates = calendar.periods(country, places, now, zone, LOOK_AHEAD_DAYS).map { period ->
            Candidate(period, ChronoUnit.DAYS.between(today, period.start.atZone(zone).toLocalDate()).toInt())
        }

        // The shutdown (a day with almost nothing open) outranks a month-long period that is still running.
        val (period, startsIn) = candidates.firstOrNull { it.period.kind.isFullShutdown() && it.startsIn <= 1 }
            ?: candidates.firstOrNull { it.startsIn <= 0 }
            ?: candidates.firstOrNull()
            ?: return null

        val name = period.shortName
        val isShutdownHeadsUp = period.kind.isFullShutdown() && startsIn <= 1

        val title = when {
            isShutdownHeadsUp ->
                "$name ${if (startsIn == 1) "tomorrow" else "today"}: ${shutdownSummary(period.kind)}"

            startsIn > 0 -> "$name starts ${if (startsIn == 1) "tomorrow" else "in $startsIn days"}"

            else -> {
                val firstDay = period.start.atZone(zone).toLocalDate()
                val lastDay = period.end.atZone(zone).toLocalDate()
                val length = ChronoUnit.DAYS.between(firstDay, lastDay).toInt() + 1
                when {
                    length == 1 -> "$name today"
                    length <= 3 -> "$name · until ${formatDay(lastDay)}"
                    else -> "$name · day ${1 - startsIn} of $length"
                }
            }
        }

        val detail = if (startsIn > 0) {
            val start = formatDay(period.start.atZone(zone).toLocalDate())
            if (period.confidence == LocalRhythmDateConfidence.EXPECTED) "Expected $start · may shift a day" else "From $start"
        } else {
            HeadsUpText.truncated(firstSentence(period.guidance))
        }

        return HeadsUpDecision(
            severity = if (isShutdownHeadsUp) HeadsUpSeverity.CAUTION else HeadsUpSeverity.INFO,
            title = title,
            detail = detail,
            statusText = statusText(period),
        )
    }

    private data class Candidate(val period: LocalRhythmPeriod, val startsIn: Int)

    /** Periods when a whole country (or island) stops for a day, which a traveller must plan for the day before. */
    private fun LocalRhythmKind.isFullShutdown(): Boolean =
        this == LocalRhythmKind.YOM_KIPPUR || this == LocalRhythmKind.NYEPI

    private fun shutdownSummary(kind: LocalRhythmKind): String =
        when (kind) {
            LocalRhythmKind.NYEPI -> "Bali shuts down for 24 h"
            else -> "country stops for 25 h"
        }

    /** The short name when it fits a status surface, else a compact stand-in. */
    private fun statusText(period: LocalRhythmPeriod): String {
        val name = period.shortName
        if (name.length <= HeadsUpText.STATUS_TEXT_CHARACTER_LIMIT) {
            return name
        }
        return when (period.kind) {
            LocalRhythmKind.LUNAR_NEW_YEAR -> "New Year"
            LocalRhythmKind.HOLY_WEEK -> "Holy Week"
            else -> HeadsUpText.truncated(name, HeadsUpText.STATUS_TEXT_CHARACTER_LIMIT)
        }
    }

    private fun firstSentence(text: String): String {
        val end = text.indexOf(". ")
        return if (end >= 0) text.substring(0, end) else text.trim('.', ' ')
    }
}
