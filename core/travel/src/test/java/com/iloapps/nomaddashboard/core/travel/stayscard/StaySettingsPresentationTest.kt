package com.iloapps.nomaddashboard.core.travel.stayscard

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import java.util.Locale
import org.junit.Test

class StaySettingsPresentationTest {
    private val names = CountryNames.forLocale(Locale.ENGLISH)

    @Test
    fun `allowance rows are sorted by country name`() {
        val rows = StaySettingsPresentation.allowanceRows(mapOf("TH" to 60, "MA" to 90, "AL" to 90), names)

        assertThat(rows.map { it.countryName }).containsExactly("Albania", "Morocco", "Thailand").inOrder()
        assertThat(rows.map { it.days }).containsExactly(90, 90, 60).inOrder()
        assertThat(rows.map { it.countryCode }).containsExactly("AL", "MA", "TH").inOrder()
    }

    @Test
    fun `setting an allowance keeps it within one and 365 days`() {
        val start = mapOf("MA" to 90)

        assertThat(StaySettingsPresentation.settingAllowance(30, "th", start)).containsExactly("MA", 90, "TH", 30)
        assertThat(StaySettingsPresentation.settingAllowance(0, "MA", start)).containsExactly("MA", 1)
        assertThat(StaySettingsPresentation.settingAllowance(900, "MA", start)).containsExactly("MA", 365)
    }

    @Test
    fun `setting an allowance for an invalid code changes nothing`() {
        assertThat(StaySettingsPresentation.settingAllowance(30, "XYZ", mapOf("MA" to 90))).containsExactly("MA", 90)
    }

    @Test
    fun `removing an allowance leaves the others`() {
        assertThat(StaySettingsPresentation.removingAllowance("ma", mapOf("MA" to 90, "TH" to 60))).containsExactly("TH", 60)
        assertThat(StaySettingsPresentation.removingAllowance("XX", mapOf("MA" to 90))).containsExactly("MA", 90)
    }

    @Test
    fun `days text is singular for one`() {
        assertThat(StaySettingsPresentation.daysText(1)).isEqualTo("1 day")
        assertThat(StaySettingsPresentation.daysText(183)).isEqualTo("183 days")
    }

    @Test
    fun `the documented footers are in place`() {
        assertThat(StaySettingsPresentation.AllowancesFooter).startsWith("Use the number of days your visa or entry stamp allows.")
        assertThat(StaySettingsPresentation.SchengenFooter).contains("90 days in any rolling 180 days")
        assertThat(StaySettingsPresentation.SchengenFooter).contains("EU, EEA and Swiss")
        assertThat(StaySettingsPresentation.TaxFooter).contains("Not tax advice")
        assertThat(StaySettingsPresentation.DefaultAllowanceDays).isEqualTo(90)
        assertThat(StaySettingsPresentation.AllowancePickerTitle).isEqualTo("Choose country")
    }
}
