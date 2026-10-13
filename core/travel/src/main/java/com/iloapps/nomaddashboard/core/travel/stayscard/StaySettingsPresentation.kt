package com.iloapps.nomaddashboard.core.travel.stayscard

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames

/**
 * Text and list logic behind the Stays & Visas settings page, ported from the macOS
 * `StaySettingsPresentation`. Visited countries and citizenship are sensitive: everything here works
 * on values stored on this device.
 */
object StaySettingsPresentation {
    const val SchengenSectionTitle = "Schengen counter"
    const val SchengenToggleTitle = "Schengen 90/180 counter"
    const val SchengenToggleDescription = "Count your days in the Schengen area."

    const val SchengenFooter =
        "Visitors from outside the EU, EEA and Switzerland may stay 90 days in any rolling 180 days in the " +
            "Schengen area. Entry and exit days count as full days. Citizens of EU, EEA and Swiss countries are " +
            "exempt, so set your citizenship under General for an accurate count."

    const val AllowancesTitle = "Stay allowances"
    const val NoAllowancesText = "No allowances set"

    const val AllowancesFooter =
        "Use the number of days your visa or entry stamp allows. Nomad counts the days of your current stay in " +
            "that country."

    const val AddAllowanceTitle = "Add country…"
    const val AllowancePickerTitle = "Choose country"
    const val DefaultAllowanceDays = 90

    const val TaxSectionTitle = "Tax residence days"
    const val TaxToggleTitle = "Count days for tax residence"
    const val TaxThresholdTitle = "Residence threshold"

    const val TaxFooter =
        "Counts the days recorded in each country this calendar year against your threshold, 183 days by " +
            "default. Rules differ by country. Not tax advice."

    const val DataTitle = "Data"
    const val OpenVisitedMapTitle = "Open visited map to review recorded days"
    const val VisitedPlacesPointerTitle = "Privacy & Location › Visited places"

    const val DataFooter =
        "Days come from the places Nomad recorded on this device and never leave it. Correct or add days in " +
            "the visited map if the device was off or in another country."

    const val VisitedPlacesOffNotice =
        "Stays & Visas is off because “Enable visited places” is off. Turn it on under Privacy & Location to " +
            "count your days."

    /** One stay allowance in the settings list. */
    data class AllowanceRow(
        val countryCode: String,
        val countryName: String,
        val days: Int,
    ) {
        val id: String
            get() = countryCode
    }

    /** The allowances sorted by country name in the user's language. */
    fun allowanceRows(allowances: Map<String, Int>, countryNames: CountryNames): List<AllowanceRow> =
        allowances
            .map { (code, days) -> AllowanceRow(countryCode = code, countryName = countryNames.name(code), days = days) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.countryName })

    /** [allowances] with [countryCode] set to [days] (kept within 1..[AppSettings.MaximumStayAllowanceDays]). */
    fun settingAllowance(days: Int, countryCode: String, allowances: Map<String, Int>): Map<String, Int> {
        val code = countryCode.trim().uppercase()
        return AppSettings.sanitizedStayAllowances(
            allowances + (code to days.coerceIn(1, AppSettings.MaximumStayAllowanceDays)),
        )
    }

    /** [allowances] without [countryCode]. */
    fun removingAllowance(countryCode: String, allowances: Map<String, Int>): Map<String, Int> {
        val code = countryCode.trim().uppercase()
        return allowances.filterKeys { it != code }
    }

    /** "90 days" or "1 day". */
    fun daysText(days: Int): String = if (days == 1) "1 day" else "$days days"
}
