package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import com.iloapps.nomaddashboard.core.travel.stayscard.StaySettingsPresentation
import java.util.Locale

internal const val SchengenCounterSwitchTag = "settings_schengen_counter_switch"
internal const val TaxResidenceSwitchTag = "settings_tax_residence_switch"
internal const val TaxResidenceThresholdFieldTag = "settings_tax_residence_threshold_field"
internal const val TaxResidenceThresholdSaveButtonTag = "settings_tax_residence_threshold_save_button"
internal const val StayAllowanceCountryButtonTag = "settings_stay_allowance_country_button"
internal const val StayAllowanceDaysFieldTag = "settings_stay_allowance_days_field"
internal const val StayAllowanceAddButtonTag = "settings_stay_allowance_add_button"
internal const val StaysOpenVisitedMapButtonTag = "settings_stays_open_visited_map_button"
internal const val StaysVisitedPlacesPointerTag = "settings_stays_visited_places_pointer"
internal const val StaysVisitedPlacesOffNoticeTag = "settings_stays_visited_places_off_notice"

internal fun removeStayAllowanceTag(code: String): String = "settings_remove_stay_allowance_$code"

/** The Stays & Visas page: the Schengen counter, per-country allowances and tax residence days. */
@Composable
internal fun StaysGroup(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group) {
        SettingsGroup.SCHENGEN_COUNTER -> SchengenCounterGroup(scope)
        SettingsGroup.STAY_ALLOWANCES -> StayAllowancesGroup(scope)
        SettingsGroup.TAX_RESIDENCE_DAYS -> TaxResidenceGroup(scope)
        SettingsGroup.STAYS_DATA -> StaysDataGroup(scope)
        else -> error("$group is not a Stays & Visas group")
    }
}

@Composable
private fun SchengenCounterGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.SCHENGEN_COUNTER,
        subtitle = "Days in the Schengen area over the last 180 days.",
        highlighted = scope.highlighted == SettingsGroup.SCHENGEN_COUNTER,
        footer = StaySettingsPresentation.SchengenFooter,
    ) {
        SettingsToggleRow(
            title = StaySettingsPresentation.SchengenToggleTitle,
            description = StaySettingsPresentation.SchengenToggleDescription,
            checked = scope.settings.schengenCounterEnabled,
            switchModifier = Modifier.testTag(SchengenCounterSwitchTag),
        ) { enabled -> scope.update { it.copy(schengenCounterEnabled = enabled) } }
    }
}

@Composable
private fun TaxResidenceGroup(scope: SettingsGroupScope) {
    val settings = scope.settings
    SettingsGroupCard(
        group = SettingsGroup.TAX_RESIDENCE_DAYS,
        subtitle = "Days per country in the current calendar year.",
        highlighted = scope.highlighted == SettingsGroup.TAX_RESIDENCE_DAYS,
        footer = StaySettingsPresentation.TaxFooter,
    ) {
        SettingsToggleRow(
            title = StaySettingsPresentation.TaxToggleTitle,
            description = "Compare your days in each country with the threshold below.",
            checked = settings.taxResidenceDaysEnabled,
            switchModifier = Modifier.testTag(TaxResidenceSwitchTag),
        ) { enabled -> scope.update { it.copy(taxResidenceDaysEnabled = enabled) } }
        if (settings.taxResidenceDaysEnabled) {
            TaxResidenceThresholdField(
                thresholdDays = settings.taxResidenceThresholdDays,
                onSave = { days ->
                    scope.update { it.copy(taxResidenceThresholdDays = AppSettings.sanitizedTaxResidenceThreshold(days)) }
                },
            )
        }
    }
}

@Composable
private fun TaxResidenceThresholdField(thresholdDays: Int, onSave: (Int) -> Unit) {
    var text by rememberSaveable(thresholdDays) { mutableStateOf(thresholdDays.toString()) }
    val range = SettingsEditing.taxResidenceThresholdDays
    val parsed = SettingsEditing.parseDays(text, range)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .testTag(TaxResidenceThresholdFieldTag),
            label = { Text("${StaySettingsPresentation.TaxThresholdTitle} (days per year)") },
            supportingText = { Text("${range.first} to ${range.last} days.") },
            isError = parsed == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(
            enabled = parsed != null && parsed != thresholdDays,
            onClick = { onSave(parsed!!) },
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(TaxResidenceThresholdSaveButtonTag),
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun StayAllowancesGroup(scope: SettingsGroupScope) {
    val countryNames = remember { CountryNames.forLocale(Locale.getDefault()) }
    val rows = StaySettingsPresentation.allowanceRows(scope.settings.stayAllowances, countryNames)
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var daysText by rememberSaveable { mutableStateOf("") }
    val days = SettingsEditing.parseDays(daysText, SettingsEditing.stayAllowanceDays)

    SettingsGroupCard(
        group = SettingsGroup.STAY_ALLOWANCES,
        subtitle = "Days you may stay per visit in a country, for example a 90-day visa.",
        highlighted = scope.highlighted == SettingsGroup.STAY_ALLOWANCES,
        footer = StaySettingsPresentation.AllowancesFooter,
    ) {
        if (rows.isEmpty()) {
            SettingsBodyText(StaySettingsPresentation.NoAllowancesText)
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${row.countryName} · ${StaySettingsPresentation.daysText(row.days)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                IconButton(
                    onClick = { scope.update { SettingsEditing.removeStayAllowance(it, row.countryCode) } },
                    modifier = Modifier.testTag(removeStayAllowanceTag(row.countryCode)),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove allowance for ${row.countryName}")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { picking = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag(StayAllowanceCountryButtonTag),
            ) {
                Text(pickedCode?.let(countryNames::name) ?: StaySettingsPresentation.AllowancePickerTitle)
            }
            OutlinedTextField(
                value = daysText,
                onValueChange = { daysText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag(StayAllowanceDaysFieldTag),
                label = { Text("Days") },
                isError = daysText.isNotBlank() && days == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        SettingsBodyText(
            "Days must be between ${SettingsEditing.stayAllowanceDays.first} and ${SettingsEditing.stayAllowanceDays.last}.",
            isError = daysText.isNotBlank() && days == null,
        )
        Button(
            enabled = pickedCode != null && days != null,
            onClick = {
                scope.update { SettingsEditing.setStayAllowance(it, pickedCode!!, days!!) }
                pickedCode = null
                daysText = ""
            },
            modifier = Modifier.testTag(StayAllowanceAddButtonTag),
        ) {
            Text("Save allowance")
        }
    }
    if (picking) {
        CountryPickerDialog(
            title = StaySettingsPresentation.AllowancePickerTitle,
            excluding = emptyList(),
            onSelect = { code ->
                pickedCode = code
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun StaysDataGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.STAYS_DATA,
        subtitle = "Where the recorded days come from.",
        highlighted = scope.highlighted == SettingsGroup.STAYS_DATA,
        footer = StaySettingsPresentation.DataFooter,
    ) {
        if (!scope.settings.visitedPlacesEnabled) {
            Row(
                modifier = Modifier.testTag(StaysVisitedPlacesOffNoticeTag),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null)
                SettingsBodyText(StaySettingsPresentation.VisitedPlacesOffNotice)
            }
        }
        OutlinedButton(
            onClick = scope.actions.onOpenVisitedMap,
            modifier = Modifier.testTag(StaysOpenVisitedMapButtonTag),
        ) {
            Text(StaySettingsPresentation.OpenVisitedMapTitle)
        }
        OutlinedButton(
            onClick = { scope.actions.onOpenDestination(SettingsDestination.VISITED_PLACES) },
            modifier = Modifier.testTag(StaysVisitedPlacesPointerTag),
        ) {
            Text(StaySettingsPresentation.VisitedPlacesPointerTitle)
        }
    }
}
