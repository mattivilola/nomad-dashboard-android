package com.iloapps.nomaddashboard.core.travel.rhythm

import java.time.LocalDate

/**
 * The calendar conversions the local-rhythm rules need, as Gregorian civil dates.
 *
 * The rules module stays free of platform calendars: production code uses
 * [AndroidIcuLunisolarCalendars], tests use an equivalent implementation on `com.ibm.icu`.
 */
interface LunisolarCalendars {
    /** The Umm al-Qura (Islamic) year that the Gregorian [date] falls in. */
    fun hijriYear(date: LocalDate): Int

    /** The Gregorian date of an Umm al-Qura date; [month] is 1-based (9 = Ramadan, 12 = Dhu al-Hijjah). */
    fun ummAlQuraToGregorian(hijriYear: Int, month: Int, day: Int): LocalDate?

    /** The Gregorian date of a Hebrew date; [month] is 1-based and starts at Tishrei (1 = Tishrei). */
    fun hebrewToGregorian(hebrewYear: Int, month: Int, day: Int): LocalDate?
}
