package com.iloapps.nomaddashboard.core.travel.stays

import java.time.LocalDate

/** A run of consecutive days in one country, ending today. */
data class CurrentStay(
    val countryCode: String,
    /** The first day of the stay, as recorded. */
    val startDate: LocalDate,
    /**
     * Calendar days from [startDate] to today, both included. Unrecorded gap days inside the stay (see
     * [unrecordedGapDays]) are counted: they are assumed to have been spent in the country.
     */
    val dayCount: Int,
    /** Days inside the stay that have no record at all (a single missing day is bridged). */
    val unrecordedGapDays: Int,
)

data class StayAllowanceStatus(
    val countryCode: String,
    val allowanceDays: Int,
    /** Days of the current stay so far, today included. */
    val usedDays: Int,
    /** Days left, never below zero. Zero means today is the last permitted day, or the allowance is exceeded. */
    val remainingDays: Int,
    /** How many days past the allowance the stay already is; zero within the allowance. */
    val overstayDays: Int,
    /** The last permitted day: in the past after an overstay. */
    val lastPermittedDay: LocalDate,
    val stay: CurrentStay,
)

data class SchengenAllowance(
    /**
     * Days with Schengen presence in the rolling window, counting entry and exit days as full days. Can
     * exceed [SchengenArea.MaximumStayDays] after an overstay.
     */
    val usedDays: Int,
    /** Days left of the 90-day allowance, never below zero. */
    val remainingDays: Int,
    /**
     * The last day the user can still be in Schengen without exceeding 90 days, if they stay. Today when
     * the allowance is used up or exceeded. Null when the user is not in Schengen today.
     */
    val mustLeaveBy: LocalDate?,
    /** First day of the rolling 180-day window that ends today. */
    val windowStart: LocalDate,
)

sealed interface SchengenStatus {
    /** The Schengen allowance behind this status; null when the limit does not apply. */
    val allowance: SchengenAllowance?
        get() = null

    /** A citizenship with free movement rights: no limit applies. */
    data object Exempt : SchengenStatus

    /** The 90/180 limit applies to the user's citizenships. */
    data class Counting(override val allowance: SchengenAllowance) : SchengenStatus

    /** No citizenship is set, so it is unknown whether the limit applies. Counts are still provided. */
    data class UnknownCitizenship(override val allowance: SchengenAllowance) : SchengenStatus
}

data class CountryDayCount(
    val countryCode: String?,
    val country: String,
    val days: Int,
) {
    val id: String
        get() = countryCode ?: country
}

data class TaxYearDays(
    val year: Int,
    /** Days per country, most days first. A border day with two countries counts once for each. */
    val countries: List<CountryDayCount>,
    /** Days of the year so far (up to today) without any record. */
    val unrecordedDays: Int,
) {
    fun daysIn(countryCode: String): Int {
        val code = countryCode.uppercase()
        return countries.firstOrNull { it.countryCode == code }?.days ?: 0
    }
}
