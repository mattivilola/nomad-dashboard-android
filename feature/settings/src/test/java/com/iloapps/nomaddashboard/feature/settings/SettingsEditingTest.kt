package com.iloapps.nomaddashboard.feature.settings

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.TravelProfile
import org.junit.Test

class SettingsEditingTest {
    @Test
    fun citizenship_can_be_added_up_to_the_limit_of_three() {
        var settings = AppSettings()
        listOf("FI", "SE", "NO").forEach { code ->
            assertThat(SettingsEditing.canAddCitizenship(settings)).isTrue()
            settings = SettingsEditing.addCitizenship(settings, code)
        }

        assertThat(settings.citizenshipCountryCodes).containsExactly("FI", "SE", "NO").inOrder()
        assertThat(SettingsEditing.canAddCitizenship(settings)).isFalse()
    }

    @Test
    fun a_fourth_citizenship_is_not_stored() {
        val full = AppSettings(citizenshipCountryCodes = listOf("FI", "SE", "NO"))

        val result = SettingsEditing.addCitizenship(full, "DK")

        assertThat(result.citizenshipCountryCodes).containsExactly("FI", "SE", "NO").inOrder()
    }

    @Test
    fun adding_a_citizenship_twice_or_in_lower_case_keeps_one_upper_case_entry() {
        val settings = SettingsEditing.addCitizenship(
            SettingsEditing.addCitizenship(AppSettings(), "fi"),
            "FI",
        )

        assertThat(settings.citizenshipCountryCodes).containsExactly("FI")
    }

    @Test
    fun removing_a_citizenship_frees_a_slot() {
        val full = AppSettings(citizenshipCountryCodes = listOf("FI", "SE", "NO"))

        val result = SettingsEditing.removeCitizenship(full, "SE")

        assertThat(result.citizenshipCountryCodes).containsExactly("FI", "NO").inOrder()
        assertThat(SettingsEditing.canAddCitizenship(result)).isTrue()
    }

    @Test
    fun turning_local_info_on_makes_sure_its_card_is_in_the_order() {
        val withoutCard = AppSettings(dashboardCardOrder = DashboardCardId.defaultOrder - DashboardCardId.LOCAL_INFO)

        val result = SettingsEditing.setLocalInfoEnabled(withoutCard, true)

        assertThat(result.localInfoEnabled).isTrue()
        assertThat(result.dashboardCardOrder).contains(DashboardCardId.LOCAL_INFO)
        assertThat(SettingsEditing.setLocalInfoEnabled(result, false).localInfoEnabled).isFalse()
    }

    @Test
    fun days_must_be_whole_numbers_inside_the_range() {
        assertThat(SettingsEditing.parseDays("90", SettingsEditing.stayAllowanceDays)).isEqualTo(90)
        assertThat(SettingsEditing.parseDays(" 365 ", SettingsEditing.stayAllowanceDays)).isEqualTo(365)
        assertThat(SettingsEditing.parseDays("0", SettingsEditing.stayAllowanceDays)).isNull()
        assertThat(SettingsEditing.parseDays("366", SettingsEditing.stayAllowanceDays)).isNull()
        assertThat(SettingsEditing.parseDays("abc", SettingsEditing.stayAllowanceDays)).isNull()
        assertThat(SettingsEditing.parseDays("29", SettingsEditing.taxResidenceThresholdDays)).isNull()
        assertThat(SettingsEditing.parseDays("30", SettingsEditing.taxResidenceThresholdDays)).isEqualTo(30)
    }

    @Test
    fun stay_allowances_are_set_replaced_and_removed_by_country() {
        val first = SettingsEditing.setStayAllowance(AppSettings(), "ma", 90)
        val replaced = SettingsEditing.setStayAllowance(first, "MA", 60)

        assertThat(first.stayAllowances).containsExactly("MA", 90)
        assertThat(replaced.stayAllowances).containsExactly("MA", 60)
        assertThat(SettingsEditing.removeStayAllowance(replaced, "MA").stayAllowances).isEmpty()
    }

    @Test
    fun an_invalid_country_code_is_not_stored_as_an_allowance() {
        val result = SettingsEditing.setStayAllowance(AppSettings(), "Morocco", 90)

        assertThat(result.stayAllowances).isEmpty()
    }
}
