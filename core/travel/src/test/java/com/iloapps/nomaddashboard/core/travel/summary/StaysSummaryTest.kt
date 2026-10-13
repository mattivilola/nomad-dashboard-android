package com.iloapps.nomaddashboard.core.travel.summary

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

class StaysSummaryTest {
    private val today = LocalDate.of(2026, 10, 12)

    private fun days(from: LocalDate, code: String, through: LocalDate = today): List<VisitedCountryDay> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(through) }.map {
            VisitedCountryDay(it, code, code, VisitedPlaceSource.DEVICE_LOCATION, isInferred = false)
        }.toList()

    private fun summary(
        settings: AppSettings,
        countryDays: List<VisitedCountryDay>,
        country: String?,
    ) = StaysSummary.of(settings, countryDays, country, today, Locale.ENGLISH)

    @Test
    fun `the stay day and the allowance lead the collapsed line`() {
        val settings = AppSettings(citizenshipCountryCodes = listOf("FI"), stayAllowances = mapOf("MA" to 90))

        val summary = summary(settings, days(LocalDate.of(2026, 9, 20), "MA"), "MA")

        assertThat(summary.text).isEqualTo("Morocco day 23 · 23/90")
        assertThat(summary.help).isEqualTo(
            "Morocco · day 23 (since 20 Sep). " +
                "Morocco allowance: 23 of 90 days · leave by 18 Dec. " +
                "Schengen: no limit for EU/EEA/Swiss citizens",
        )
    }

    @Test
    fun `without an allowance the stay day stands alone outside Schengen`() {
        val settings = AppSettings(citizenshipCountryCodes = listOf("US"))

        val summary = summary(settings, days(LocalDate.of(2026, 9, 20), "MA"), "MA")

        assertThat(summary.text).isEqualTo("Morocco day 23")
    }

    @Test
    fun `inside Schengen the 90 day count is the news`() {
        val settings = AppSettings(citizenshipCountryCodes = listOf("US"))

        val summary = summary(settings, days(LocalDate.of(2026, 8, 13), "ES"), "ES")

        assertThat(summary.text).isEqualTo("Schengen 61/90")
        assertThat(summary.help).startsWith("Spain · day 61 (since 13 Aug). 61 of 90 days used (rolling 180 days) · leave by ")
    }

    @Test
    fun `an allowance beats the Schengen count`() {
        val settings = AppSettings(citizenshipCountryCodes = listOf("US"), stayAllowances = mapOf("ES" to 90))

        assertThat(summary(settings, days(LocalDate.of(2026, 8, 13), "ES"), "ES").text).isEqualTo("Spain day 61 · 61/90")
    }

    @Test
    fun `an allowance in its last day or overstayed says so in the help`() {
        val lastDay = AppSettings(stayAllowances = mapOf("MA" to 23))
        val overstay = AppSettings(stayAllowances = mapOf("MA" to 20))
        val stay = days(LocalDate.of(2026, 9, 20), "MA")

        assertThat(summary(lastDay, stay, "MA").help).contains("Morocco allowance: 23 of 23 days · last day today")
        assertThat(summary(overstay, stay, "MA").help).contains("23 of 20 days · 3 days over · ended 9 Oct")
    }

    @Test
    fun `an unknown citizenship still counts Schengen days`() {
        val summary = summary(AppSettings(), days(LocalDate.of(2026, 10, 1), "FR"), "FR")

        assertThat(summary.text).isEqualTo("Schengen 12/90")
    }

    @Test
    fun `the busiest tax year country stands in when the current country is unknown`() {
        val settings = AppSettings(schengenCounterEnabled = false)

        val summary = summary(settings, days(LocalDate.of(2026, 3, 1), "PT", through = LocalDate.of(2026, 3, 10)), null)

        assertThat(summary.text).isEqualTo("Portugal 10 days")
    }

    @Test
    fun `nothing to count collects`() {
        val settings = AppSettings(schengenCounterEnabled = false, taxResidenceDaysEnabled = false)

        assertThat(summary(settings, emptyList(), null).text).isEqualTo("Collecting")
    }

    @Test
    fun `the card is off when visited places are not saved`() {
        val off = summary(AppSettings(visitedPlacesEnabled = false), emptyList(), "ES")

        assertThat(off).isEqualTo(DashboardCardSummary.OFF)
    }
}
