package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmDateConfidence
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPeriod
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPlace
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * One cultural or religious period of the current country in the Local Info card: Ramadan, Lunar New
 * Year, Holy Week. These change opening hours, traffic and prices, so the card names the dates and what
 * to expect. Ported from the macOS `LocalRhythmRowModel`.
 *
 * @property timing "Now", "Tomorrow" or "In 5 days".
 * @property dates "8 Feb – 9 Mar (expected; may shift a day)".
 */
data class LocalRhythmRow(
    val id: String,
    val name: String,
    val timing: String,
    val dates: String,
    val guidance: String,
) {
    companion object {
        /** How far ahead a period is listed, as for the heads-up item. */
        const val LOOK_AHEAD_DAYS = LocalRhythmCalendar.DEFAULT_LOOKAHEAD_DAYS
        const val SECTION_TITLE = "Local rhythm"
        const val SHIFT_NOTE = "expected; may shift a day"

        fun of(period: LocalRhythmPeriod, time: CardTime, now: Instant): LocalRhythmRow {
            val firstDay = time.localDate(period.start)
            val lastDay = time.localDate(period.end)
            val startsIn = ChronoUnit.DAYS.between(time.localDate(now), firstDay)

            val first = time.dayMonth(firstDay)
            val last = time.dayMonth(lastDay)
            val range = if (first == last) first else "$first – $last"

            return LocalRhythmRow(
                id = period.id,
                name = period.shortName,
                timing = when {
                    startsIn <= 0 -> "Now"
                    startsIn == 1L -> "Tomorrow"
                    else -> "In $startsIn days"
                },
                dates = if (period.confidence == LocalRhythmDateConfidence.EXPECTED) "$range ($SHIFT_NOTE)" else range,
                guidance = period.guidance,
            )
        }

        /**
         * The periods that are on or start within [LOOK_AHEAD_DAYS] for the current country, earliest
         * first. Nyepi only shows for a location in Bali (or one whose region is unknown).
         */
        fun rows(
            calendar: LocalRhythmCalendar,
            snapshot: DashboardSnapshot,
            time: CardTime,
            now: Instant,
        ): List<LocalRhythmRow> {
            val context = snapshot.travelContext
            return calendar.periods(
                countryCode = CardLocation.countryCode(snapshot),
                places = listOf(
                    LocalRhythmPlace(context.deviceCountryCode, context.deviceRegion),
                    LocalRhythmPlace(context.countryCode, context.region),
                ),
                now = now,
                zone = time.zone,
                lookaheadDays = LOOK_AHEAD_DAYS,
            ).map { of(it, time, now) }
        }
    }
}
