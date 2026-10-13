package com.iloapps.nomaddashboard.core.travel.arrival

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.stays.StayCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// The one-time "Welcome to Morocco" summary shown when the traveller crosses into a new country.
// `ArrivalDetector` decides whether an arrival just happened; `ArrivalBriefingBuilder` composes the
// content from data the dashboard already has. Every section is optional: missing data omits the
// section instead of guessing.
//
// Ported from the macOS ArrivalBriefing (NomadCore, commit d2fb26d).

/** A crossing into a country the traveller was not in on the most recent previously recorded day. */
data class ArrivalEvent(
    /** Upper-case ISO 3166-1 alpha-2 code of the country just entered. */
    val countryCode: String,
    /** Upper-case ISO 3166-1 alpha-2 code of the country of the most recent previously recorded day. */
    val previousCountryCode: String,
    /** The first day of the stay in the new country. */
    val arrivedOn: LocalDate,
)

object ArrivalDetector {
    /**
     * A previous record older than this (before the stay began) is stale history, not a border crossing:
     * the app was simply not running (or the traveller was away from the phone) for too long to call it
     * an arrival.
     */
    const val MAXIMUM_PREVIOUS_RECORD_AGE_DAYS = 30

    /**
     * How many days after the first day in a country the briefing is still offered, so opening the
     * dashboard a couple of days after crossing the border still shows it until it is acknowledged.
     */
    const val ARRIVAL_WINDOW_DAYS = 3

    /**
     * The arrival that should be briefed now, or null.
     *
     * An arrival is a stay in the current country (see [StayCalculator.currentStay]) that began at most
     * [ARRIVAL_WINDOW_DAYS] days ago, whose most recent recorded day before it was in another country. A
     * day can hold several countries (a border day): the last country recorded that day is the previous
     * one, and a previous day that also holds the current country is not a crossing. Days without a
     * usable country code are skipped. [ArrivalEvent.arrivedOn] is the first day of the stay.
     *
     * Null as well when briefings are disabled, when the country was already acknowledged, when there is
     * no history (first run), or when the previous record is more than
     * [MAXIMUM_PREVIOUS_RECORD_AGE_DAYS] before the stay began.
     *
     * @param now with [zone], decides what today is.
     */
    fun arrival(
        countryDays: List<VisitedCountryDay>,
        currentCountryCode: String?,
        settings: AppSettings,
        now: Instant,
        zone: ZoneId,
    ): ArrivalEvent? {
        if (!settings.arrivalBriefingEnabled) return null
        val current = currentCountryCode?.let(AppSettings::normalizedCountryCode) ?: return null
        if (settings.arrivalBriefingAcknowledgedCountryCode?.let(AppSettings::normalizedCountryCode) == current) return null

        val today = now.atZone(zone).toLocalDate()
        val stay = StayCalculator(countryDays, today, liveCountryCode = current).currentStay(current) ?: return null
        if (stay.dayCount - 1 > ARRIVAL_WINDOW_DAYS) return null

        val earlier = countryDays.mapNotNull { entry ->
            val code = entry.countryCode?.let(AppSettings::normalizedCountryCode)
            if (entry.date < stay.startDate && code != null) entry.date to code else null
        }
        val latestDay = earlier.maxOfOrNull { it.first } ?: return null

        val latestCodes = earlier.filter { it.first == latestDay }.map { it.second }
        if (current in latestCodes) return null
        val previous = latestCodes.last()

        if (ChronoUnit.DAYS.between(latestDay, stay.startDate) > MAXIMUM_PREVIOUS_RECORD_AGE_DAYS) return null

        return ArrivalEvent(countryCode = current, previousCountryCode = previous, arrivedOn = stay.startDate)
    }
}
