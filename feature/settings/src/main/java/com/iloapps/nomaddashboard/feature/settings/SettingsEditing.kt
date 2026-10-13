package com.iloapps.nomaddashboard.feature.settings

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.withCardEnsured
import com.iloapps.nomaddashboard.core.travel.stayscard.StaySettingsPresentation

/** Pure edits to [AppSettings] that the settings pages apply, kept out of the UI so they can be tested. */
internal object SettingsEditing {
    fun canAddCitizenship(settings: AppSettings): Boolean =
        settings.citizenshipCountryCodes.size < AppSettings.MaximumCitizenships

    fun addCitizenship(settings: AppSettings, code: String): AppSettings =
        settings.copy(
            citizenshipCountryCodes = AppSettings.sanitizedCitizenshipCodes(settings.citizenshipCountryCodes + code),
        )

    fun removeCitizenship(settings: AppSettings, code: String): AppSettings {
        val normalized = AppSettings.normalizedCountryCode(code) ?: return settings
        return settings.copy(citizenshipCountryCodes = settings.citizenshipCountryCodes.filterNot { it == normalized })
    }

    fun setLocalInfoEnabled(settings: AppSettings, enabled: Boolean): AppSettings =
        if (enabled) {
            settings.withCardEnsured(DashboardCardId.LOCAL_INFO).copy(localInfoEnabled = true)
        } else {
            settings.copy(localInfoEnabled = false)
        }

    /** A whole number of days within [range], or null for anything else. */
    fun parseDays(text: String, range: IntRange): Int? =
        text.trim().toIntOrNull()?.takeIf { it in range }

    val stayAllowanceDays: IntRange = 1..AppSettings.MaximumStayAllowanceDays

    val taxResidenceThresholdDays: IntRange =
        AppSettings.MinimumTaxResidenceThresholdDays..AppSettings.MaximumTaxResidenceThresholdDays

    fun setStayAllowance(settings: AppSettings, code: String, days: Int): AppSettings =
        settings.copy(stayAllowances = StaySettingsPresentation.settingAllowance(days, code, settings.stayAllowances))

    fun removeStayAllowance(settings: AppSettings, code: String): AppSettings =
        settings.copy(stayAllowances = StaySettingsPresentation.removingAllowance(code, settings.stayAllowances))
}
