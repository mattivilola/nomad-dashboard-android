package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.safety.CitizenshipPresentation
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import java.util.Locale

internal const val ReliefWebAppNameFieldTag = "settings_reliefweb_app_name_field"
internal const val ReliefWebAppNameSaveButtonTag = "settings_reliefweb_app_name_save_button"
internal const val ChooseCitizenshipButtonTag = "settings_choose_citizenship_button"

@Composable
internal fun SafetyGroup(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group) {
        SettingsGroup.TRAVEL_ALERTS -> TravelAlertsGroup(scope)
        SettingsGroup.EMERGENCY_CARE -> EmergencyCareGroup(scope)
        SettingsGroup.YOUR_EMBASSY -> YourEmbassyGroup(scope)
        else -> error("$group is not a Safety group")
    }
}

@Composable
private fun TravelAlertsGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.TRAVEL_ALERTS,
        subtitle = "Travel advisories and regional security for your country and the ones around it.",
        highlighted = scope.highlighted == SettingsGroup.TRAVEL_ALERTS,
        footer = "Regional security lookups use ReliefWeb and need your own approved app name. " +
            "It stays encrypted on this device only and never ships inside the app.",
    ) {
        SettingsCredentialField(
            savedValue = scope.state.providerCredentials.reliefWebAppName,
            label = "ReliefWeb app name",
            supportingText = "Required only for regional security lookups.",
            clearLabel = "Clear app name",
            saveLabel = "Save app name",
            fieldTag = ReliefWebAppNameFieldTag,
            saveTag = ReliefWebAppNameSaveButtonTag,
            onSave = { appName -> scope.actions.onUpdateProviderCredentials { it.copy(reliefWebAppName = appName) } },
        )
    }
}

@Composable
private fun EmergencyCareGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.EMERGENCY_CARE,
        subtitle = "Local emergency numbers and the nearest hospitals.",
        highlighted = scope.highlighted == SettingsGroup.EMERGENCY_CARE,
        footer = "Emergency numbers are bundled with the app and shown offline. Nearby hospitals use your location.",
    ) {
        SettingsToggleRow(
            title = "Enable emergency care",
            description = "Reserve dashboard space for nearby hospital lookup and quick map access.",
            checked = scope.settings.emergencyCareEnabled,
            switchModifier = Modifier.testTag("settings_emergency_care_switch"),
        ) { enabled -> scope.update { it.copy(emergencyCareEnabled = enabled) } }
    }
}

@Composable
private fun YourEmbassyGroup(scope: SettingsGroupScope) {
    val countryNames = remember { CountryNames.forLocale(Locale.getDefault()) }
    SettingsGroupCard(
        group = SettingsGroup.YOUR_EMBASSY,
        subtitle = CitizenshipPresentation.embassyStatus(scope.settings.citizenshipCountryCodes, countryNames),
        highlighted = scope.highlighted == SettingsGroup.YOUR_EMBASSY,
        footer = CitizenshipPresentation.EmbassySectionFooter,
    ) {
        Button(
            onClick = { scope.actions.onOpenDestination(SettingsDestination.CITIZENSHIP) },
            modifier = Modifier.testTag(ChooseCitizenshipButtonTag),
        ) {
            Text("Choose citizenship")
        }
    }
}
