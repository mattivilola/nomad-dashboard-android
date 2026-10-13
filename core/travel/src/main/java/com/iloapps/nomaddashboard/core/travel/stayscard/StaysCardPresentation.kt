package com.iloapps.nomaddashboard.core.travel.stayscard

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import com.iloapps.nomaddashboard.core.travel.stays.SchengenAllowance
import com.iloapps.nomaddashboard.core.travel.stays.SchengenArea
import com.iloapps.nomaddashboard.core.travel.stays.SchengenStatus
import com.iloapps.nomaddashboard.core.travel.stays.StayAllowanceStatus
import com.iloapps.nomaddashboard.core.travel.stays.StayCalculator
import com.iloapps.nomaddashboard.core.travel.stays.StayWarningThresholds
import com.iloapps.nomaddashboard.core.travel.stays.TaxYearDays
import com.iloapps.nomaddashboard.core.travel.summary.StaysSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How urgent a figure on the Stays & Visas card is. Colour means status only: [Caution] is sand and
 * [Attention] is coral; everything else stays [Neutral].
 */
enum class StayTone {
    Neutral,
    Caution,
    Attention,
    ;

    /** Ranks the tones that carry a status: attention over caution over neutral. */
    internal val urgency: Int
        get() = ordinal
}

/**
 * Everything the "Stays & Visas" card shows, derived from the days Nomad recorded on this device, the
 * country the device is in and the user's stay settings. Ported from the macOS `StaysCardPresentation`;
 * the UI only renders it.
 *
 * Visited countries and citizenship are sensitive: everything here is computed locally and is never
 * part of analytics.
 */
data class StaysCardPresentation(
    val isEnabled: Boolean,
    val badge: Badge,
    val currentStay: CurrentStay?,
    val allowance: Allowance?,
    val schengen: Schengen?,
    val taxYear: TaxYear?,
    /** One line for the collapsed card. */
    val summary: String,
    val summaryHelp: String,
) {
    data class Badge(val title: String, val tone: StayTone)

    /** The run of days in the current country: "Morocco · day 23" since "since 2 Jan". */
    data class CurrentStay(
        val title: String,
        val since: String,
        /** Set when the stay bridges days that have no record. */
        val gapNote: String?,
    )

    /** The user-set allowance for the current country (a visa or an entry stamp). */
    data class Allowance(
        val title: String,
        /** "67 of 90 days". */
        val usage: String,
        /** "leave by 3 Mar", "last day today" or "3 days over · ended 28 Feb". */
        val deadline: String,
        val progress: Float,
        val tone: StayTone,
    ) {
        val text: String
            get() = "$usage · $deadline"
    }

    data class Schengen(
        val kind: Kind,
        val title: String,
        /** "61 of 90 days used (rolling 180 days)"; the reason for the missing limit when exempt. */
        val usage: String,
        /** "leave by 14 Mar", only while in Schengen and the limit applies. */
        val deadline: String?,
        val hint: String?,
        val progress: Float?,
        val tone: StayTone,
    ) {
        enum class Kind {
            /** A citizenship with free movement: no limit applies. */
            Exempt,

            /** No citizenship is set, so it is unknown whether the limit applies. */
            CitizenshipUnknown,
            Counting,
        }

        /** The whole row as one line. */
        val line: String
            get() = when (kind) {
                Kind.Exempt -> "$title: $usage"
                Kind.CitizenshipUnknown, Kind.Counting -> listOfNotNull(usage, deadline).joinToString(" · ")
            }
    }

    data class CountryDays(
        val id: String,
        val name: String,
        /** "97 days". */
        val daysText: String,
        /** Days against the tax residence threshold, 0 and up (above 1 once over). */
        val progress: Float,
        val tone: StayTone,
    )

    /** This year's days per country against the tax residence threshold. */
    data class TaxYear(
        val heading: String,
        /** "Threshold 183 days". */
        val thresholdText: String,
        /** The countries with the most days, at most [TaxCountryLimit]. */
        val countries: List<CountryDays>,
        /** "Unrecorded days this year: 12", null when every day has a record. */
        val unrecordedText: String?,
        val emptyText: String?,
    )

    companion object {
        const val TaxCountryLimit = 5

        const val Subtitle = "Days recorded on this device"
        const val TaxFooter = "Based on days Nomad recorded on this device · not tax or legal advice"
        const val CitizenshipActionTitle = "Set citizenship"
        const val CitizenshipHint = "Set your citizenship to check if this applies"
        const val SettingsActionTitle = "Stays settings"
        const val ReviewDaysActionTitle = "Review days"
        const val LocationPendingText = "Current country not known yet"
        const val OffTitle = "Stays & Visas Off"
        const val OffMessage = "Turn on “Enable visited places” in Settings to count your days in each country."

        private val OffPresentation = StaysCardPresentation(
            isEnabled = false,
            badge = Badge("Off", StayTone.Neutral),
            currentStay = null,
            allowance = null,
            schengen = null,
            taxYear = null,
            summary = "Off",
            summaryHelp = "Off",
        )

        /**
         * @param today the current date in the user's own time zone.
         * @param currentCountryCode the country the device is in right now (ISO alpha-2), if known.
         */
        fun of(
            settings: AppSettings,
            countryDays: List<VisitedCountryDay>,
            currentCountryCode: String?,
            today: LocalDate,
            locale: Locale = Locale.getDefault(),
            countryNames: CountryNames = CountryNames.forLocale(locale),
        ): StaysCardPresentation {
            if (!DashboardCardId.STAYS.isFeatureEnabled(settings)) return OffPresentation

            val code = currentCountryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }
            val calculator = StayCalculator(countryDays, today, code)
            val dayFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
            val dayText = { date: LocalDate -> dayFormatter.format(date) }

            val stay = code?.let(calculator::currentStay)
            val countryName = code?.let(countryNames::name)

            val currentStay = stay?.let { found ->
                countryName?.let { name ->
                    CurrentStay(
                        title = "$name · day ${found.dayCount}",
                        since = "since ${dayText(found.startDate)}",
                        gapNote = gapNote(found.unrecordedGapDays),
                    )
                }
            }

            val allowanceStatus = code?.let { current ->
                settings.stayAllowances[current]?.let { calculator.allowanceStatus(current, it) }
            }
            val allowance = allowanceStatus?.let { status ->
                countryName?.let { allowance(status, it, today, dayText) }
            }

            val schengenStatus = if (settings.schengenCounterEnabled) {
                calculator.schengenStatus(settings.citizenshipCountryCodes)
            } else {
                null
            }
            val schengen = schengenStatus?.let { schengen(it, today, dayText) }

            val taxYear = if (settings.taxResidenceDaysEnabled) {
                taxYear(
                    days = calculator.taxYearDays(today.year),
                    threshold = settings.taxResidenceThresholdDays,
                    countryNames = countryNames,
                )
            } else {
                null
            }

            val tones = listOfNotNull(allowance?.tone, schengen?.tone) + taxYear?.countries?.map { it.tone }.orEmpty()
            val urgent = tones.maxByOrNull { it.urgency } ?: StayTone.Neutral
            val isOverLimit = (allowanceStatus?.overstayDays ?: 0) > 0 || schengenIsExceeded(schengenStatus)

            // The collapsed line is the shared summary, so the card and the dashboard header never differ.
            val collapsed = StaysSummary.of(settings, countryDays, code, today, locale, countryNames)

            return StaysCardPresentation(
                isEnabled = true,
                badge = badge(urgent, isOverLimit),
                currentStay = currentStay,
                allowance = allowance,
                schengen = schengen,
                taxYear = taxYear,
                summary = collapsed.text,
                summaryHelp = collapsed.help,
            )
        }

        private fun dayCount(count: Int): String = if (count == 1) "1 day" else "$count days"

        private fun gapNote(days: Int): String? =
            days.takeIf { it > 0 }?.let { "Includes ${dayCount(it)} without a record, counted as spent here" }

        private fun tone(daysLeft: Int, isOver: Boolean): StayTone = when {
            isOver || daysLeft <= StayWarningThresholds.ATTENTION_DAYS -> StayTone.Attention
            daysLeft <= StayWarningThresholds.CAUTION_DAYS -> StayTone.Caution
            else -> StayTone.Neutral
        }

        private fun allowance(
            status: StayAllowanceStatus,
            countryName: String,
            today: LocalDate,
            dayText: (LocalDate) -> String,
        ): Allowance {
            val deadline = when {
                status.overstayDays > 0 ->
                    "${dayCount(status.overstayDays)} over · ended ${dayText(status.lastPermittedDay)}"
                status.lastPermittedDay == today -> "last day today"
                else -> "leave by ${dayText(status.lastPermittedDay)}"
            }
            return Allowance(
                title = "$countryName allowance",
                usage = "${status.usedDays} of ${dayCount(status.allowanceDays)}",
                deadline = deadline,
                progress = status.usedDays.toFloat() / status.allowanceDays,
                tone = tone(daysLeft = status.remainingDays, isOver = status.overstayDays > 0),
            )
        }

        private fun schengen(
            status: SchengenStatus,
            today: LocalDate,
            dayText: (LocalDate) -> String,
        ): Schengen {
            val title = "Schengen"
            return when (status) {
                SchengenStatus.Exempt -> Schengen(
                    kind = Schengen.Kind.Exempt,
                    title = title,
                    usage = StaysSummary.SCHENGEN_EXEMPT_REASON,
                    deadline = null,
                    hint = null,
                    progress = null,
                    tone = StayTone.Neutral,
                )

                is SchengenStatus.Counting -> {
                    val allowance = status.allowance
                    Schengen(
                        kind = Schengen.Kind.Counting,
                        title = title,
                        usage = usage(allowance),
                        deadline = allowance.mustLeaveBy?.let { date ->
                            if (date == today) "leave today" else "leave by ${dayText(date)}"
                        },
                        hint = null,
                        progress = progress(allowance),
                        // Only a stay inside Schengen can run into the limit; outside it the count is for planning.
                        tone = if (allowance.mustLeaveBy == null) {
                            StayTone.Neutral
                        } else {
                            tone(allowance.remainingDays, isOver = allowance.usedDays > SchengenArea.MaximumStayDays)
                        },
                    )
                }

                is SchengenStatus.UnknownCitizenship -> Schengen(
                    kind = Schengen.Kind.CitizenshipUnknown,
                    title = title,
                    usage = usage(status.allowance),
                    deadline = null,
                    hint = CitizenshipHint,
                    progress = progress(status.allowance),
                    tone = StayTone.Neutral,
                )
            }
        }

        private fun usage(allowance: SchengenAllowance): String =
            "${allowance.usedDays} of ${SchengenArea.MaximumStayDays} days used (rolling ${SchengenArea.WindowDays} days)"

        private fun progress(allowance: SchengenAllowance): Float =
            allowance.usedDays.toFloat() / SchengenArea.MaximumStayDays

        private fun schengenIsExceeded(status: SchengenStatus?): Boolean {
            val counting = status as? SchengenStatus.Counting ?: return false
            return counting.allowance.mustLeaveBy != null && counting.allowance.usedDays > SchengenArea.MaximumStayDays
        }

        private fun taxYear(days: TaxYearDays, threshold: Int, countryNames: CountryNames): TaxYear {
            val countries = days.countries.take(TaxCountryLimit).map { entry ->
                CountryDays(
                    id = entry.id,
                    name = entry.countryCode?.let(countryNames::name) ?: entry.country,
                    daysText = dayCount(entry.days),
                    progress = entry.days.toFloat() / maxOf(threshold, 1),
                    tone = if (entry.days >= threshold - StayWarningThresholds.TAX_RESIDENCE_CAUTION_LEAD_DAYS) {
                        StayTone.Caution
                    } else {
                        StayTone.Neutral
                    },
                )
            }
            return TaxYear(
                heading = "Days in ${days.year}",
                thresholdText = "Threshold ${dayCount(threshold)}",
                countries = countries,
                unrecordedText = days.unrecordedDays.takeIf { it > 0 }?.let { "Unrecorded days this year: $it" },
                emptyText = if (countries.isEmpty()) "No days recorded yet this year" else null,
            )
        }

        private fun badge(tone: StayTone, isOverLimit: Boolean): Badge = when (tone) {
            StayTone.Attention -> Badge(if (isOverLimit) "Over Limit" else "Limit Near", StayTone.Attention)
            StayTone.Caution -> Badge("Limit Near", StayTone.Caution)
            StayTone.Neutral -> Badge("Tracking", StayTone.Neutral)
        }
    }
}
