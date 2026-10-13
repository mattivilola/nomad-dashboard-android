package com.iloapps.nomaddashboard.core.travel.rhythm

import android.icu.util.Calendar
import android.icu.util.HebrewCalendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * [LunisolarCalendars] on the platform's ICU (`android.icu`).
 *
 * Every calendar runs in UTC and is read at noon, so a civil date maps to the same date in every
 * calendar regardless of the device time zone. `android.icu` is a repackaged ICU4J: the test source set
 * holds `IcuLunisolarCalendars`, the same code on `com.ibm.icu`, and the two must stay line-for-line
 * equivalent apart from the imports.
 *
 * ICU calendars are not thread safe, so each call uses its own instance.
 */
class AndroidIcuLunisolarCalendars : LunisolarCalendars {
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
