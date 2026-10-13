package com.iloapps.nomaddashboard.core.travel.summary

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import com.iloapps.nomaddashboard.core.travel.stays.SchengenAllowance
import com.iloapps.nomaddashboard.core.travel.stays.SchengenArea
import com.iloapps.nomaddashboard.core.travel.stays.SchengenStatus
import com.iloapps.nomaddashboard.core.travel.stays.StayAllowanceStatus
import com.iloapps.nomaddashboard.core.travel.stays.StayCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The collapsed line of the Stays card ("Morocco day 23 · 67/90", "Schengen 61/90"), ported from the
 * macOS `StaysCardPresentation`. Everything is computed locally from the user's own country days.
 */
object StaysSummary {
    const val SCHENGEN_EXEMPT_REASON = "no limit for EU/EEA/Swiss citizens"

    fun of(
        settings: AppSettings,
        countryDays: List<VisitedCountryDay>,
        currentCountryCode: String?,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
        countryNames: CountryNames = CountryNames.forLocale(locale),
    ): DashboardCardSummary {
        if (!DashboardCardId.STAYS.isFeatureEnabled(settings)) return DashboardCardSummary.OFF

        val code = currentCountryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }
        val calculator = StayCalculator(countryDays, today, code)
        val dayText = { date: LocalDate -> DateTimeFormatter.ofPattern("d MMM", locale).format(date) }

        val stay = code?.let(calculator::currentStay)
        val countryName = code?.let(countryNames::name)
        val allowance = code?.let { current ->
            settings.stayAllowances[current]?.let { calculator.allowanceStatus(current, it) }
        }
        val schengen = if (settings.schengenCounterEnabled) {
            calculator.schengenStatus(settings.citizenshipCountryCodes)
        } else {
            null
        }
        val topTaxCountry = if (settings.taxResidenceDaysEnabled) {
            calculator.taxYearDays(today.year).countries.firstOrNull()?.let { entry ->
                "${entry.countryCode?.let(countryNames::name) ?: entry.country} ${dayCount(entry.days)}"
            }
        } else {
            null
        }

        val stayPhrase = stay?.let { found -> countryName?.let { "$it day ${found.dayCount}" } }
        val summary = summaryLine(
            stayPhrase = stayPhrase,
            allowance = allowance,
            schengen = schengen,
            isInSchengen = code?.let(SchengenArea::isMember) ?: false,
            topTaxCountry = topTaxCountry,
        )

        val help = listOfNotNull(
            stay?.let { found ->
                countryName?.let { "$it · day ${found.dayCount} (since ${dayText(found.startDate)})" }
            },
            allowance?.let { status ->
                countryName?.let { "$it allowance: ${allowanceText(status, today, dayText)}" }
            },
            schengen?.let { schengenLine(it, today, dayText) },
        )

        return DashboardCardSummary(text = summary, help = help.takeIf { it.isNotEmpty() }?.joinToString(". ") ?: summary)
    }

    /**
     * "Morocco day 23 · 67/90", or "Schengen 61/90" while in Schengen where the limit counts. The
     * busiest tax-year country stands in when neither is known.
     */
    private fun summaryLine(
        stayPhrase: String?,
        allowance: StayAllowanceStatus?,
        schengen: SchengenStatus?,
        isInSchengen: Boolean,
        topTaxCountry: String?,
    ): String {
        // The collapsed line is short, so inside Schengen the 90-day count is the news, not the day number.
        val schengenUsed = schengen?.allowance?.usedDays
        if (allowance == null && schengenUsed != null && (isInSchengen || stayPhrase == null)) {
            return "Schengen $schengenUsed/${SchengenArea.MaximumStayDays}"
        }

        val parts = listOfNotNull(stayPhrase, allowance?.let { "${it.usedDays}/${it.allowanceDays}" }).toMutableList()
        if (parts.isEmpty() && topTaxCountry != null) parts += topTaxCountry
        return if (parts.isEmpty()) "Collecting" else parts.joinToString(" · ")
    }

    private fun allowanceText(status: StayAllowanceStatus, today: LocalDate, dayText: (LocalDate) -> String): String {
        val deadline = when {
            status.overstayDays > 0 -> "${dayCount(status.overstayDays)} over · ended ${dayText(status.lastPermittedDay)}"
            status.lastPermittedDay == today -> "last day today"
            else -> "leave by ${dayText(status.lastPermittedDay)}"
        }
        return "${status.usedDays} of ${dayCount(status.allowanceDays)} · $deadline"
    }

    private fun schengenLine(status: SchengenStatus, today: LocalDate, dayText: (LocalDate) -> String): String =
        when (status) {
            SchengenStatus.Exempt -> "Schengen: $SCHENGEN_EXEMPT_REASON"
            is SchengenStatus.Counting -> {
                val deadline = status.allowance.mustLeaveBy?.let { date ->
                    if (date == today) "leave today" else "leave by ${dayText(date)}"
                }
                listOfNotNull(usage(status.allowance), deadline).joinToString(" · ")
            }

            is SchengenStatus.UnknownCitizenship -> usage(status.allowance)
        }

    private fun usage(allowance: SchengenAllowance): String =
        "${allowance.usedDays} of ${SchengenArea.MaximumStayDays} days used (rolling ${SchengenArea.WindowDays} days)"

    private fun dayCount(count: Int): String = if (count == 1) "1 day" else "$count days"
}
