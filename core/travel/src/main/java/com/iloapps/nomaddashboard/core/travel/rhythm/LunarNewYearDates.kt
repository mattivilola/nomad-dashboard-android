package com.iloapps.nomaddashboard.core.travel.rhythm

import java.time.LocalDate

/**
 * The Gregorian date of Lunar New Year (the first day of the first Chinese lunar month, not a leap month),
 * for 2000-2100.
 *
 * This is a table and not an ICU computation on purpose: ICU4J, which `android.icu` is built from, derives
 * new moons astronomically and is a day off in 2027 (7 Feb, the Beijing new moon falls minutes before
 * midnight on 6 Feb) and 2030 (2 Feb instead of 3 Feb). The values below are Foundation's Chinese
 * calendar on macOS, whose ICU4C carries the Hong Kong Observatory 1901-2100 conversion table, taken as
 * (month, day) per year. They agree with the Hong Kong and Singapore public holiday lists (17 Feb 2026,
 * 6 Feb 2027; https://www.gov.hk/en/about/abouthk/holiday/2026.htm,
 * https://www.mom.gov.sg/employment-practices/public-holidays) and with the macOS tests, and ICU4J
 * matches every other year in the range.
 *
 * Years outside the range are unverified, so they have no date and the calendar returns nothing for them.
 */
internal object LunarNewYearDates {
    private const val FIRST_YEAR = 2000

    /** Month and day as `month * 100 + day`, one entry per year from [FIRST_YEAR]. */
    private val monthDays = intArrayOf(
        205, 124, 212, 201, 122, 209, 129, 218, 207, 126, // 2000-2009
        214, 203, 123, 210, 131, 219, 208, 128, 216, 205, // 2010-2019
        125, 212, 201, 122, 210, 129, 217, 206, 126, 213, // 2020-2029
        203, 123, 211, 131, 219, 208, 128, 215, 204, 124, // 2030-2039
        212, 201, 122, 210, 130, 217, 206, 126, 214, 202, // 2040-2049
        123, 211, 201, 219, 208, 128, 215, 204, 124, 212, // 2050-2059
        202, 121, 209, 129, 217, 205, 126, 214, 203, 123, // 2060-2069
        211, 131, 219, 207, 127, 215, 205, 124, 212, 202, // 2070-2079
        122, 209, 129, 217, 206, 126, 214, 203, 124, 210, // 2080-2089
        130, 218, 207, 127, 215, 205, 125, 212, 201, 121, // 2090-2099
        209, // 2100
    )

    fun dateIn(gregorianYear: Int): LocalDate? {
        val monthDay = monthDays.getOrNull(gregorianYear - FIRST_YEAR) ?: return null
        return LocalDate.of(gregorianYear, monthDay / 100, monthDay % 100)
    }
}
