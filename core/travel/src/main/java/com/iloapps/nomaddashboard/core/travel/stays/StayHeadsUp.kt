package com.iloapps.nomaddashboard.core.travel.stays

import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpCandidate
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpDecision
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a stay heads-up writes country names and dates; injectable so the UI layer can localize them. */
data class StayHeadsUpFormat(
    val countryName: (String) -> String = CountryNames.forLocale(Locale.ENGLISH)::name,
    val day: (LocalDate) -> String = { DayFormatter.format(it) },
) {
    companion object {
        private val DayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    }
}

/** What the stay heads-ups need: the user's own country-day history and the stay settings. */
data class StayHeadsUpInput(
    val countryDays: List<VisitedCountryDay>,
    /** Today in the user's own time zone. */
    val today: LocalDate,
    /** ISO 3166-1 alpha-2 code of the country the device is in right now, if known. */
    val currentCountryCode: String?,
    val citizenships: List<String> = emptyList(),
    /** Allowed days per country code, as stored in the settings. */
    val stayAllowances: Map<String, Int> = emptyMap(),
    val schengenCounterEnabled: Boolean = true,
    val taxResidenceDaysEnabled: Boolean = true,
    val taxResidenceThresholdDays: Int = DefaultTaxResidenceThresholdDays,
) {
    companion object {
        const val DefaultTaxResidenceThresholdDays = 183
    }
}

/**
 * Stay allowance, Schengen 90/180 and tax-residence-days heads-ups, derived from the local country-day
 * history with no network.
 */
object StayHeadsUps {
    /**
     * All stay heads-ups that apply ([HeadsUpKind.STAY_ALLOWANCE], [HeadsUpKind.SCHENGEN_LIMIT] and
     * [HeadsUpKind.TAX_RESIDENCE_DAYS]), in that order.
     * The caller decides whether the Stays feature is on at all (the visited-places setting) before
     * calling this.
     */
    fun evaluate(input: StayHeadsUpInput, format: StayHeadsUpFormat = StayHeadsUpFormat()): List<HeadsUpCandidate> {
        val currentCode = normalizedCountryCode(input.currentCountryCode)
        val calculator = StayCalculator(
            days = input.countryDays,
            today = input.today,
            liveCountryCode = currentCode,
        )
        return listOfNotNull(
            stayAllowance(calculator, currentCode, input.stayAllowances, format)
                ?.let { HeadsUpCandidate(HeadsUpKind.STAY_ALLOWANCE, it) },
            schengenLimit(calculator, input.citizenships, input.schengenCounterEnabled, format)
                ?.let { HeadsUpCandidate(HeadsUpKind.SCHENGEN_LIMIT, it) },
            taxResidenceDays(
                calculator = calculator,
                currentCountryCode = currentCode,
                enabled = input.taxResidenceDaysEnabled,
                thresholdDays = input.taxResidenceThresholdDays,
                year = input.today.year,
                format = format,
            )?.let { HeadsUpCandidate(HeadsUpKind.TAX_RESIDENCE_DAYS, it) },
        )
    }

    /** The user-set allowance for the current country: caution at 14 days left, attention at 3 and when over. */
    fun stayAllowance(
        calculator: StayCalculator,
        currentCountryCode: String?,
        stayAllowances: Map<String, Int>,
        format: StayHeadsUpFormat = StayHeadsUpFormat(),
    ): HeadsUpDecision? {
        val code = normalizedCountryCode(currentCountryCode) ?: return null
        val allowanceDays = stayAllowances[code] ?: return null
        val status = calculator.allowanceStatus(code, allowanceDays) ?: return null
        if (status.remainingDays > StayWarningThresholds.CAUTION_DAYS) return null

        val leaveBy = format.day(status.lastPermittedDay)
        val (phrase, statusText) = when {
            status.overstayDays > 0 -> dayCount(status.overstayDays).let { "$it over" to "$it over" }
            status.remainingDays == 0 -> "last day" to "Last day"
            else -> dayCount(status.remainingDays).let { "$it left" to "$it left" }
        }
        return HeadsUpDecision(
            severity = if (
                status.remainingDays <= StayWarningThresholds.ATTENTION_DAYS
            ) {
                HeadsUpSeverity.ATTENTION
            } else {
                HeadsUpSeverity.CAUTION
            },
            title = "${format.countryName(code)}: $phrase",
            statusText = statusText,
            detail = if (status.overstayDays > 0) {
                "Allowance ${dayCount(allowanceDays)} · ended $leaveBy"
            } else {
                "Allowance ${dayCount(allowanceDays)} · leave by $leaveBy"
            },
        )
    }

    /**
     * The Schengen 90/180 limit for non-exempt citizens who are in Schengen today: caution at 14 days
     * remaining, attention at 3. Silent without a citizenship, since it is then unknown whether it applies.
     */
    fun schengenLimit(
        calculator: StayCalculator,
        citizenships: List<String>,
        counterEnabled: Boolean,
        format: StayHeadsUpFormat = StayHeadsUpFormat(),
    ): HeadsUpDecision? {
        if (!counterEnabled) return null
        val allowance = (calculator.schengenStatus(citizenships) as? SchengenStatus.Counting)?.allowance ?: return null
        val mustLeaveBy = allowance.mustLeaveBy ?: return null
        if (allowance.remainingDays > StayWarningThresholds.CAUTION_DAYS) return null

        val limit = SchengenArea.MaximumStayDays
        val overstay = allowance.usedDays - limit
        return HeadsUpDecision(
            severity = if (
                allowance.remainingDays <= StayWarningThresholds.ATTENTION_DAYS
            ) {
                HeadsUpSeverity.ATTENTION
            } else {
                HeadsUpSeverity.CAUTION
            },
            title = "Schengen: ${allowance.remainingDays} of $limit days left",
            statusText = "Schengen ${allowance.remainingDays}d",
            detail = if (overstay > 0) {
                "Over the limit by ${dayCount(overstay)}"
            } else {
                "Leave by ${format.day(mustLeaveBy)}"
            },
        )
    }

    /**
     * Days in the current country this calendar year: info from 30 days before the threshold, caution
     * from 7 days before it. Always labelled as not tax advice.
     */
    fun taxResidenceDays(
        calculator: StayCalculator,
        currentCountryCode: String?,
        enabled: Boolean,
        thresholdDays: Int,
        year: Int,
        format: StayHeadsUpFormat = StayHeadsUpFormat(),
    ): HeadsUpDecision? {
        if (!enabled) return null
        val code = normalizedCountryCode(currentCountryCode) ?: return null
        val days = calculator.taxYearDays(year).daysIn(code)
        if (days <= 0 || days < thresholdDays - StayWarningThresholds.TAX_RESIDENCE_INFO_LEAD_DAYS) return null

        return HeadsUpDecision(
            severity = if (
                days >= thresholdDays - StayWarningThresholds.TAX_RESIDENCE_CAUTION_LEAD_DAYS
            ) {
                HeadsUpSeverity.CAUTION
            } else {
                HeadsUpSeverity.INFO
            },
            title = "${format.countryName(code)}: ${dayCount(days)} this year",
            statusText = dayCount(days),
            detail = "$thresholdDays-day tax residence threshold · not tax advice",
        )
    }

    private fun dayCount(count: Int): String = if (count == 1) "1 day" else "$count days"

    /** Upper-case ISO 3166-1 alpha-2 code, or null when [code] is not a pair of ASCII letters. */
    private fun normalizedCountryCode(code: String?): String? =
        code?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { char -> char in 'A'..'Z' } }
}
