package com.iloapps.nomaddashboard.core.travel.rhythm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One cultural or religious period of the current country as a row: Ramadan, Lunar New Year, Holy
 * Week. These change opening hours, traffic and prices, so the row names the dates and what to expect.
 *
 * Ported from the macOS `LocalRhythmRowModel`; the arrival briefing shows the same rows.
 */
data class LocalRhythmRow(
    val id: String,
    val name: String,
    /** "Now", "Tomorrow" or "In 5 days". */
    val timing: String,
    /** "8 Feb – 9 Mar (expected; may shift a day)". */
    val dates: String,
    val guidance: String,
) {
    companion object {
        const val SECTION_TITLE = "Local rhythm"
        const val SHIFT_NOTE = "expected; may shift a day"

        /**
         * @param zone decides which calendar days the period's first and last instant fall on.
         * @param dayMonth formats a date as day and abbreviated month in the traveller's locale, for example "8 Feb".
         */
        fun of(
            period: LocalRhythmPeriod,
            now: Instant,
            zone: ZoneId,
            dayMonth: (LocalDate) -> String,
        ): LocalRhythmRow {
            val first = period.start.atZone(zone).toLocalDate()
            val last = period.end.atZone(zone).toLocalDate()
            val startsIn = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), first)

            val timing = when {
                startsIn <= 0 -> "Now"
                startsIn == 1L -> "Tomorrow"
                else -> "In $startsIn days"
            }
            val firstText = dayMonth(first)
            val lastText = dayMonth(last)
            val range = if (firstText == lastText) firstText else "$firstText – $lastText"

            return LocalRhythmRow(
                id = period.id,
                name = period.shortName,
                timing = timing,
                dates = if (period.confidence == LocalRhythmDateConfidence.EXPECTED) "$range ($SHIFT_NOTE)" else range,
                guidance = period.guidance,
            )
        }
    }
}
