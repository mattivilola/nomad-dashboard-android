package com.iloapps.nomaddashboard.core.travel.headsup

import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Public holiday heads-up from the Local Info snapshot. */
object PublicHolidayHeadsUp {
    const val LOOK_AHEAD_DAYS = 3
    private const val NAME_CHARACTER_LIMIT = 28

    /**
     * The public holiday that is on today or starts within three days: today is a caution, later days an
     * info. The stored phase was computed at fetch time, so this re-derives it from the period's dates
     * relative to [today].
     *
     * @param weekday the full weekday name of a date, in the traveller's locale.
     */
    fun evaluate(localInfo: LocalInfoSnapshot, today: LocalDate, weekday: (LocalDate) -> String): HeadsUpDecision? {
        val period = localInfo.publicHoliday?.period ?: return null
        val offset = offset(period, today)?.takeIf { it <= LOOK_AHEAD_DAYS } ?: return null

        val name = HeadsUpText.truncated(period.name, NAME_CHARACTER_LIMIT)
        return when (offset) {
            0 -> HeadsUpDecision(
                severity = HeadsUpSeverity.CAUTION,
                title = "Public holiday today",
                detail = "$name · shops may close",
                statusText = "Holiday today",
            )

            1 -> HeadsUpDecision(
                severity = HeadsUpSeverity.INFO,
                title = "Public holiday tomorrow",
                detail = name,
                statusText = "Holiday",
            )

            else -> HeadsUpDecision(
                severity = HeadsUpSeverity.INFO,
                title = "Public holiday ${weekday(period.startDate)}",
                detail = name,
                statusText = "Holiday",
            )
        }
    }

    /** Whole days from [today] to the period's start; 0 while it is on, null once it is over. */
    private fun offset(period: HolidayPeriod, today: LocalDate): Int? {
        if (period.startDate <= today && today <= period.endDate) return 0
        return ChronoUnit.DAYS.between(today, period.startDate).toInt().takeIf { it > 0 }
    }
}
