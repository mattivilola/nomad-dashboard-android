package com.iloapps.nomaddashboard.core.travel.stayscard

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.stays.Today
import com.iloapps.nomaddashboard.core.travel.stays.visit
import java.util.Locale
import org.junit.Test

class StaysCardPresentationTest {
    /** [count] consecutive days in [code], the last one [endingOffset] days before today. */
    private fun daysIn(code: String, count: Int, endingOffset: Int = 0): List<VisitedCountryDay> =
        (0 until count).map { visit(Today.minusDays((endingOffset + count - 1 - it).toLong()), code) }

    private fun card(
        days: List<VisitedCountryDay>,
        current: String? = "MA",
        settings: AppSettings = AppSettings(),
    ) = StaysCardPresentation.of(settings, days, current, Today, Locale.ENGLISH)

    // region Current stay

    @Test
    fun `the current stay names the country the day and the start date`() {
        val stay = card(daysIn("MA", 23)).currentStay!!

        assertThat(stay.title).isEqualTo("Morocco · day 23")
        assertThat(stay.since).isEqualTo("since 20 Sep")
        assertThat(stay.gapNote).isNull()
    }

    @Test
    fun `a bridged gap is mentioned`() {
        val days = daysIn("MA", 10).filter { it.date != Today.minusDays(4) }

        val stay = card(days).currentStay!!

        assertThat(stay.title).isEqualTo("Morocco · day 10")
        assertThat(stay.gapNote).isEqualTo("Includes 1 day without a record, counted as spent here")
    }

    @Test
    fun `the live country counts today before it is recorded`() {
        val stay = card(daysIn("MA", 5, endingOffset = 1)).currentStay!!

        assertThat(stay.title).isEqualTo("Morocco · day 6")
    }

    @Test
    fun `without a current country there is no stay and no allowance`() {
        val presentation = card(daysIn("MA", 5), current = null, settings = AppSettings(stayAllowances = mapOf("MA" to 90)))

        assertThat(presentation.currentStay).isNull()
        assertThat(presentation.allowance).isNull()
    }

    // endregion

    // region Allowance

    @Test
    fun `an allowance shows the usage and the last permitted day`() {
        val presentation = card(daysIn("MA", 67), settings = AppSettings(stayAllowances = mapOf("MA" to 90)))
        val allowance = presentation.allowance!!

        assertThat(allowance.title).isEqualTo("Morocco allowance")
        assertThat(allowance.text).isEqualTo("67 of 90 days · leave by 4 Nov")
        assertThat(allowance.progress).isWithin(0.0001f).of(67f / 90f)
        assertThat(allowance.tone).isEqualTo(StayTone.Neutral)
        assertThat(presentation.badge).isEqualTo(StaysCardPresentation.Badge("Tracking", StayTone.Neutral))
    }

    @Test
    fun `an allowance turns sand at fourteen days left and coral at three`() {
        fun tone(usedDays: Int) =
            card(daysIn("MA", usedDays), settings = AppSettings(stayAllowances = mapOf("MA" to 90))).allowance!!.tone

        assertThat(tone(75)).isEqualTo(StayTone.Neutral)
        assertThat(tone(76)).isEqualTo(StayTone.Caution)
        assertThat(tone(86)).isEqualTo(StayTone.Caution)
        assertThat(tone(87)).isEqualTo(StayTone.Attention)
    }

    @Test
    fun `the last day and an overstay are worded`() {
        val settings = AppSettings(stayAllowances = mapOf("MA" to 90))

        val lastDay = card(daysIn("MA", 90), settings = settings).allowance!!
        val over = card(daysIn("MA", 93), settings = settings)

        assertThat(lastDay.text).isEqualTo("90 of 90 days · last day today")
        assertThat(lastDay.tone).isEqualTo(StayTone.Attention)
        assertThat(over.allowance!!.text).isEqualTo("93 of 90 days · 3 days over · ended 9 Oct")
        assertThat(over.allowance!!.tone).isEqualTo(StayTone.Attention)
        assertThat(over.badge).isEqualTo(StaysCardPresentation.Badge("Over Limit", StayTone.Attention))
    }

    @Test
    fun `a sand allowance makes the badge sand`() {
        val presentation = card(daysIn("MA", 80), settings = AppSettings(stayAllowances = mapOf("MA" to 90)))

        assertThat(presentation.badge).isEqualTo(StaysCardPresentation.Badge("Limit Near", StayTone.Caution))
    }

    @Test
    fun `an allowance for another country does not apply`() {
        val presentation = card(daysIn("MA", 80), settings = AppSettings(stayAllowances = mapOf("ES" to 90)))

        assertThat(presentation.allowance).isNull()
    }

    // endregion

    // region Schengen

    @Test
    fun `an EU citizen is told there is no limit`() {
        val presentation = card(daysIn("ES", 100), current = "ES", settings = AppSettings(citizenshipCountryCodes = listOf("FI")))
        val schengen = presentation.schengen!!

        assertThat(schengen.kind).isEqualTo(StaysCardPresentation.Schengen.Kind.Exempt)
        assertThat(schengen.line).isEqualTo("Schengen: no limit for EU/EEA/Swiss citizens")
        assertThat(schengen.progress).isNull()
        assertThat(schengen.tone).isEqualTo(StayTone.Neutral)
        assertThat(presentation.badge.title).isEqualTo("Tracking")
    }

    @Test
    fun `without a citizenship the counts come with a hint`() {
        val schengen = card(daysIn("ES", 61), current = "ES").schengen!!

        assertThat(schengen.kind).isEqualTo(StaysCardPresentation.Schengen.Kind.CitizenshipUnknown)
        assertThat(schengen.line).isEqualTo("61 of 90 days used (rolling 180 days)")
        assertThat(schengen.hint).isEqualTo("Set your citizenship to check if this applies")
        assertThat(schengen.tone).isEqualTo(StayTone.Neutral)
    }

    @Test
    fun `a non EU citizen sees the bar and the leave by date`() {
        val presentation = card(daysIn("ES", 61), current = "ES", settings = AppSettings(citizenshipCountryCodes = listOf("US")))
        val schengen = presentation.schengen!!

        assertThat(schengen.kind).isEqualTo(StaysCardPresentation.Schengen.Kind.Counting)
        assertThat(schengen.usage).isEqualTo("61 of 90 days used (rolling 180 days)")
        assertThat(schengen.deadline).startsWith("leave by ")
        assertThat(schengen.progress!!).isWithin(0.0001f).of(61f / 90f)
        assertThat(schengen.hint).isNull()
        assertThat(schengen.tone).isEqualTo(StayTone.Neutral)
    }

    @Test
    fun `Schengen gets urgent only while in Schengen`() {
        val settings = AppSettings(citizenshipCountryCodes = listOf("US"))

        val inside = card(daysIn("ES", 80), current = "ES", settings = settings)
        val outside = card(daysIn("ES", 80, endingOffset = 1), current = "MA", settings = settings)

        assertThat(inside.schengen!!.tone).isEqualTo(StayTone.Caution)
        assertThat(inside.badge.title).isEqualTo("Limit Near")
        assertThat(outside.schengen!!.tone).isEqualTo(StayTone.Neutral)
        assertThat(outside.schengen!!.deadline).isNull()
    }

    @Test
    fun `leaving today is worded and an exceeded count is over the limit`() {
        val settings = AppSettings(citizenshipCountryCodes = listOf("US"))

        val atLimit = card(daysIn("ES", 90), current = "ES", settings = settings)
        val over = card(daysIn("ES", 95), current = "ES", settings = settings)

        assertThat(atLimit.schengen!!.deadline).isEqualTo("leave today")
        assertThat(atLimit.schengen!!.tone).isEqualTo(StayTone.Attention)
        assertThat(atLimit.badge.title).isEqualTo("Limit Near")
        assertThat(over.badge).isEqualTo(StaysCardPresentation.Badge("Over Limit", StayTone.Attention))
    }

    @Test
    fun `Schengen is hidden when the counter is off`() {
        val presentation = card(daysIn("ES", 61), current = "ES", settings = AppSettings(schengenCounterEnabled = false))

        assertThat(presentation.schengen).isNull()
    }

    // endregion

    // region Tax year

    @Test
    fun `this years top five countries are listed against the threshold`() {
        // 2026-10-12 is day 285. Spain 97 days, then Portugal 40, Morocco 30, France 20, Italy 10 and Greece 5.
        val days = daysIn("ES", 97) +
            daysIn("PT", 40, endingOffset = 97) +
            daysIn("MA", 30, endingOffset = 137) +
            daysIn("FR", 20, endingOffset = 167) +
            daysIn("IT", 10, endingOffset = 187) +
            daysIn("GR", 5, endingOffset = 197)

        val tax = card(days, current = "ES").taxYear!!

        assertThat(tax.heading).isEqualTo("Days in 2026")
        assertThat(tax.thresholdText).isEqualTo("Threshold 183 days")
        assertThat(tax.countries.map { it.name }).containsExactly("Spain", "Portugal", "Morocco", "France", "Italy").inOrder()
        assertThat(tax.countries.first().daysText).isEqualTo("97 days")
        assertThat(tax.countries.first().progress).isWithin(0.0001f).of(97f / 183f)
        assertThat(tax.unrecordedText).isEqualTo("Unrecorded days this year: 83")
        assertThat(tax.emptyText).isNull()
    }

    @Test
    fun `a country near the threshold turns sand but never coral`() {
        fun tax(days: Int) = card(daysIn("ES", days), current = "ES").taxYear!!

        assertThat(tax(176).countries.first().tone).isEqualTo(StayTone.Caution)
        assertThat(tax(175).countries.first().tone).isEqualTo(StayTone.Neutral)
        assertThat(tax(200).countries.first().tone).isEqualTo(StayTone.Caution)
        assertThat(tax(200).countries.first().progress).isGreaterThan(1f)
    }

    @Test
    fun `the threshold follows the setting`() {
        val tax = card(daysIn("ES", 60), current = "ES", settings = AppSettings(taxResidenceThresholdDays = 90)).taxYear!!

        assertThat(tax.thresholdText).isEqualTo("Threshold 90 days")
        assertThat(tax.countries.first().progress).isWithin(0.0001f).of(60f / 90f)
    }

    @Test
    fun `a year without days says so`() {
        val tax = card(emptyList(), current = null).taxYear!!

        assertThat(tax.countries).isEmpty()
        assertThat(tax.emptyText).isEqualTo("No days recorded yet this year")
        assertThat(tax.unrecordedText).isEqualTo("Unrecorded days this year: 285")
    }

    @Test
    fun `tax days are hidden when off`() {
        val presentation = card(daysIn("ES", 10), settings = AppSettings(taxResidenceDaysEnabled = false))

        assertThat(presentation.taxYear).isNull()
    }

    @Test
    fun `the tax footer disclaims advice`() {
        assertThat(StaysCardPresentation.TaxFooter).contains("not tax or legal advice")
    }

    // endregion

    // region Off, summary and badge

    @Test
    fun `the card is off without visited places`() {
        val presentation = card(daysIn("MA", 10), settings = AppSettings(visitedPlacesEnabled = false))

        assertThat(presentation.isEnabled).isFalse()
        assertThat(presentation.summary).isEqualTo("Off")
        assertThat(presentation.badge.title).isEqualTo("Off")
        assertThat(presentation.currentStay).isNull()
        assertThat(presentation.taxYear).isNull()
    }

    @Test
    fun `the summary leads with the stay and its allowance`() {
        val presentation = card(daysIn("MA", 23), settings = AppSettings(stayAllowances = mapOf("MA" to 90)))

        assertThat(presentation.summary).isEqualTo("Morocco day 23 · 23/90")
        assertThat(presentation.summaryHelp).contains("Morocco · day 23 (since 20 Sep)")
        assertThat(presentation.summaryHelp).contains("Morocco allowance: 23 of 90 days")
    }

    @Test
    fun `the summary leads with Schengen while in Schengen where the limit counts`() {
        val presentation = card(daysIn("ES", 61), current = "ES", settings = AppSettings(citizenshipCountryCodes = listOf("US")))

        assertThat(presentation.summary).isEqualTo("Schengen 61/90")
        assertThat(presentation.summaryHelp).contains("Spain · day 61")
    }

    @Test
    fun `the summary falls back to Schengen then the busiest country then collecting`() {
        val schengenOnly = card(daysIn("ES", 61, endingOffset = 1), current = null)
        val taxOnly = card(
            daysIn("MA", 20, endingOffset = 1),
            current = null,
            settings = AppSettings(schengenCounterEnabled = false),
        )
        val nothing = card(emptyList(), current = null, settings = AppSettings(schengenCounterEnabled = false))

        assertThat(schengenOnly.summary).isEqualTo("Schengen 61/90")
        assertThat(taxOnly.summary).isEqualTo("Morocco 20 days")
        assertThat(nothing.summary).isEqualTo("Collecting")
    }

    // endregion
}
