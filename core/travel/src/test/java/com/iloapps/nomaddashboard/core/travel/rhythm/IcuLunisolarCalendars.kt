package com.iloapps.nomaddashboard.core.travel.rhythm

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.HebrewCalendar
import com.ibm.icu.util.IslamicCalendar
import com.ibm.icu.util.TimeZone
import com.ibm.icu.util.ULocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * [LunisolarCalendars] on ICU4J (`com.ibm.icu`) for JVM unit tests.
 *
 * This is line-for-line the production `AndroidIcuLunisolarCalendars`, which runs on the platform's
 * `android.icu` (a repackaged ICU4J with the same API). Only the imports and this comment differ; keep
 * them equivalent.
 */
class IcuLunisolarCalendars : LunisolarCalendars {
    override fun hijriYear(date: LocalDate): Int {
        val calendar = ummAlQuraCalendar()
        calendar.timeInMillis = noonMillis(date)
        return calendar.get(Calendar.YEAR)
    }

    override fun ummAlQuraToGregorian(hijriYear: Int, month: Int, day: Int): LocalDate? =
        gregorianDate(ummAlQuraCalendar(), hijriYear, month, day)

    override fun hebrewToGregorian(hebrewYear: Int, month: Int, day: Int): LocalDate? =
        gregorianDate(HebrewCalendar(TimeZone.GMT_ZONE, ULocale.ROOT), hebrewYear, month, day)

    private fun ummAlQuraCalendar(): IslamicCalendar =
        IslamicCalendar(TimeZone.GMT_ZONE, ULocale.ROOT).apply {
            calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        }

    /** [month] is 1-based; ICU months are 0-based. */
    private fun gregorianDate(calendar: Calendar, year: Int, month: Int, day: Int): LocalDate? {
        calendar.clear()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        return Instant.ofEpochMilli(calendar.timeInMillis).atZone(ZoneOffset.UTC).toLocalDate()
    }

    private fun noonMillis(date: LocalDate): Long =
        date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
}
