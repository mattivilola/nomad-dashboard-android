package com.iloapps.nomaddashboard.core.travel.stays

import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.text.Collator
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Stay arithmetic over the recorded country days: current stay streaks, the Schengen 90/180 rule,
 * calendar-year day counts for tax residence and per-country stay allowances.
 *
 * Rules:
 * - Everything is counted in whole calendar days. "Today" is [today], the current date in the user's own
 *   time zone. Days are compared by calendar-day distance, never by 24-hour spans, so daylight-saving
 *   changes do not shift or lose a day. Days after today are ignored.
 * - A recorded day counts for every country recorded on it. The ledger keeps one country per day today,
 *   but if a day holds two (a border crossing) both get the full day: entry and exit days count.
 * - Inferred and manual days count like observed ones.
 * - A stay is a run of days in one country. One unrecorded day between two days in the country is
 *   bridged (the phone was off for a day); two or more unrecorded days in a row end the stay, and so
 *   does any day recorded only in other countries.
 *
 * @param liveCountryCode the country the device is in right now. When today has no record yet (the
 *   location has not been recorded since launch) today counts as spent there.
 */
class StayCalculator(
    days: List<VisitedCountryDay>,
    private val today: LocalDate,
    liveCountryCode: String? = null,
) {
    private class Entry(val offset: Int, val day: VisitedCountryDay)

    private val entries: List<Entry>

    /** Country codes recorded per day, keyed by days before today (0 is today). */
    private val presence: Map<Int, Set<String>>

    /** Days before today that have any record, including days without a country code. */
    private val recordedOffsets: Set<Int>

    init {
        val collected = days.mapNotNull { day ->
            val offset = ChronoUnit.DAYS.between(day.date, today).toInt()
            Entry(offset, day).takeIf { offset >= 0 }
        }.toMutableList()

        val live = liveCountryCode?.trim()?.uppercase()?.takeIf(String::isNotEmpty)
        if (live != null && collected.none { it.offset == 0 }) {
            collected += Entry(
                offset = 0,
                day = VisitedCountryDay(
                    date = today,
                    country = live,
                    countryCode = live,
                    source = VisitedPlaceSource.DEVICE_LOCATION,
                    isInferred = true,
                ),
            )
        }

        val presence = mutableMapOf<Int, MutableSet<String>>()
        for (entry in collected) {
            entry.day.countryCode?.let { presence.getOrPut(entry.offset) { mutableSetOf() } += it }
        }

        entries = collected
        this.presence = presence
        recordedOffsets = collected.mapTo(mutableSetOf()) { it.offset }
    }

    // region Current stay

    /**
     * The run of days ending today in [country] (ISO alpha-2), or null when the user is not there. Today
     * may be the one unrecorded day of the run.
     */
    fun currentStay(country: String): CurrentStay? {
        val code = country.trim().uppercase()
        val oldest = recordedOffsets.maxOrNull()
        if (code.isEmpty() || oldest == null) return null

        var earliestPresent: Int? = null
        var consecutiveGap = 0
        for (offset in 0..oldest) {
            if (presence[offset]?.contains(code) == true) {
                earliestPresent = offset
                consecutiveGap = 0
            } else if (offset in recordedOffsets) {
                break
            } else {
                consecutiveGap += 1
                if (consecutiveGap > 1) break
            }
        }

        val earliest = earliestPresent ?: return null
        return CurrentStay(
            countryCode = code,
            startDate = dateDaysBeforeToday(earliest),
            dayCount = earliest + 1,
            unrecordedGapDays = (0..earliest).count { it !in recordedOffsets },
        )
    }

    /**
     * The current stay in [country] measured against a user-set allowance of [allowanceDays]. The arrival
     * day is day one, so an allowance of 90 days that began on 1 January ends on 31 March.
     */
    fun allowanceStatus(country: String, allowanceDays: Int): StayAllowanceStatus? {
        if (allowanceDays <= 0) return null
        val stay = currentStay(country) ?: return null

        val remaining = allowanceDays - stay.dayCount
        return StayAllowanceStatus(
            countryCode = stay.countryCode,
            allowanceDays = allowanceDays,
            usedDays = stay.dayCount,
            remainingDays = maxOf(remaining, 0),
            overstayDays = maxOf(-remaining, 0),
            lastPermittedDay = dateDaysBeforeToday(-remaining),
            stay = stay,
        )
    }

    // endregion

    // region Schengen

    fun schengenStatus(citizenships: List<String>): SchengenStatus {
        if (SchengenArea.isExempt(citizenships)) return SchengenStatus.Exempt

        val allowance = schengenAllowance()
        val hasCitizenship = citizenships.any { it.trim().isNotEmpty() }
        return if (hasCitizenship) SchengenStatus.Counting(allowance) else SchengenStatus.UnknownCitizenship(allowance)
    }

    private fun schengenAllowance(): SchengenAllowance {
        val windowDays = SchengenArea.WindowDays
        val limit = SchengenArea.MaximumStayDays
        val members = SchengenArea.memberStates.toSet()

        // inSchengen[offset]: any Schengen presence that day; offset 0 is today, 179 the window start.
        val inSchengen = BooleanArray(windowDays) { offset -> presence[offset]?.any { it in members } == true }
        val used = inSchengen.count { it }

        var mustLeaveBy: LocalDate? = null
        if (inSchengen[0]) {
            // Staying d more days: the window then starts d days later, so the d oldest window days fall
            // out and d new days are added. The stay is allowed while that total stays within 90.
            val prefix = IntArray(windowDays)
            var running = 0
            for (offset in 0 until windowDays) {
                if (inSchengen[offset]) running += 1
                prefix[offset] = running
            }

            var lastAllowedExtraDays = 0
            for (extraDays in 0 until windowDays) {
                val total = prefix[windowDays - 1 - extraDays] + extraDays
                if (total > limit) break
                lastAllowedExtraDays = extraDays
            }
            mustLeaveBy = dateDaysBeforeToday(-lastAllowedExtraDays)
        }

        return SchengenAllowance(
            usedDays = used,
            remainingDays = maxOf(limit - used, 0),
            mustLeaveBy = mustLeaveBy,
            windowStart = dateDaysBeforeToday(windowDays - 1),
        )
    }

    // endregion

    // region Calendar year

    /**
     * Days per country in calendar [year], up to today, most days first. A day counts once per country
     * even if it is recorded more than once.
     */
    fun taxYearDays(year: Int): TaxYearDays {
        class Tally(val countryCode: String?, val country: String, var days: Int)

        val yearEntries = entries.filter { it.day.date.year == year }.sortedBy { it.day.date }
        val tallies = linkedMapOf<String, Tally>()
        for ((_, dayEntries) in yearEntries.groupBy { it.day.date }) {
            val seen = mutableSetOf<String>()
            for (entry in dayEntries) {
                val key = entry.day.countryCode ?: foldedName(entry.day.country)
                if (!seen.add(key)) continue
                tallies.getOrPut(key) { Tally(entry.day.countryCode, entry.day.country, 0) }.days += 1
            }
        }

        val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.SECONDARY }
        val countries = tallies.values
            .map { CountryDayCount(countryCode = it.countryCode, country = it.country, days = it.days) }
            .sortedWith(compareByDescending<CountryDayCount> { it.days }.thenBy(collator) { it.country })

        val recordedDays = yearEntries.mapTo(mutableSetOf()) { it.day.date }.size
        return TaxYearDays(
            year = year,
            countries = countries,
            unrecordedDays = maxOf(daysElapsed(year) - recordedDays, 0),
        )
    }

    /** Days of [year] up to and including today; the whole year once it is over, zero before it starts. */
    private fun daysElapsed(year: Int): Int = when {
        today.year < year -> 0
        today.year == year -> today.dayOfYear
        else -> LocalDate.of(year, 1, 1).lengthOfYear()
    }

    // endregion

    /** The date [offset] days before today (a negative offset is in the future). */
    private fun dateDaysBeforeToday(offset: Int): LocalDate = today.minusDays(offset.toLong())

    private fun foldedName(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(CombiningMarks, "")
            .lowercase(Locale.ROOT)

    private companion object {
        val CombiningMarks = Regex("\\p{M}+")
    }
}
