package com.iloapps.nomaddashboard.feature.settings

import com.iloapps.nomaddashboard.core.model.withDashboardLayoutReset
import com.iloapps.nomaddashboard.core.model.withCardMoved
import com.iloapps.nomaddashboard.core.model.withCardWidthToggled
import com.iloapps.nomaddashboard.core.model.withRecommendedOrder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.AppearanceMode
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import java.util.Locale

internal const val AddCitizenshipButtonTag = "settings_add_citizenship_button"
internal const val ChangeHomeTimeZoneButtonTag = "settings_change_home_time_zone_button"
internal const val UseRecommendedOrderButtonTag = "settings_use_recommended_order_button"
internal const val ResetDashboardLayoutButtonTag = "settings_reset_dashboard_layout_button"

internal fun removeCitizenshipTag(code: String): String = "settings_remove_citizenship_$code"

internal fun travelProfileTag(profile: TravelProfile): String = "settings_travel_profile_${profile.name}"

@Composable
internal fun GeneralGroup(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group) {
        SettingsGroup.TRAVEL_PROFILE -> TravelProfileGroup(scope)
        SettingsGroup.HOME_TIME_ZONE -> HomeTimeZoneGroup(scope)
        SettingsGroup.CITIZENSHIP -> CitizenshipGroup(scope)
        SettingsGroup.APPEARANCE -> AppearanceGroup(scope)
        SettingsGroup.STATUS_TILE -> StatusTileGroup(scope)
        SettingsGroup.ARRIVAL_BRIEFING -> ArrivalBriefingGroup(scope)
        SettingsGroup.DASHBOARD_LAYOUT -> DashboardLayoutGroup(scope)
        else -> error("$group is not a General group")
    }
}

@Composable
private fun TravelProfileGroup(scope: SettingsGroupScope) {
    val settings = scope.settings
    SettingsGroupCard(
        group = SettingsGroup.TRAVEL_PROFILE,
        subtitle = "Tunes the recommended card order and which heads-ups come first. It never hides a card.",
        highlighted = scope.highlighted == SettingsGroup.TRAVEL_PROFILE,
        footer = if (settings.isUsingRecommendedOrder) {
            settings.travelProfile.summary
        } else {
            "${settings.travelProfile.summary} $KeptCustomOrderNote"
        },
    ) {
        SettingsChoiceRow(
            options = TravelProfile.entries,
            selected = settings.travelProfile,
            label = TravelProfile::title,
            onSelect = { profile -> scope.update { it.applyTravelProfile(profile) } },
            tagFor = ::travelProfileTag,
        )
        if (!settings.isUsingRecommendedOrder) {
            OutlinedButton(
                onClick = { scope.update(AppSettings::withRecommendedOrder) },
                modifier = Modifier.testTag(UseRecommendedOrderButtonTag),
            ) {
                Text("Use recommended order")
            }
        }
    }
}

@Composable
private fun HomeTimeZoneGroup(scope: SettingsGroupScope) {
    val locale = Locale.getDefault()
    val homeZoneId = scope.state.companionPreferences.homeTimeZoneId
    val zone = remember(homeZoneId, locale) { TimeZoneDirectory.option(homeZoneId, locale) }
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsGroupCard(
        group = SettingsGroup.HOME_TIME_ZONE,
        subtitle = "Where your day is anchored. The dashboard clock and the time zone planner compare against it.",
        highlighted = scope.highlighted == SettingsGroup.HOME_TIME_ZONE,
        footer = "Stored only on this device.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(homeZoneId.replace('_', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SettingsBodyText(zone.subtitle)
        }
        scope.state.companionError?.let { SettingsBodyText(it, isError = true) }
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.testTag(ChangeHomeTimeZoneButtonTag),
        ) {
            Text("Change home time zone")
        }
    }
    if (picking) {
        TimeZonePickerDialog(
            title = "Home time zone",
            onSelect = { id ->
                scope.updateCompanion { it.copy(homeTimeZoneId = id) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CitizenshipGroup(scope: SettingsGroupScope) {
    val settings = scope.settings
    val codes = settings.citizenshipCountryCodes
    val countryNames = remember { CountryNames.forLocale(Locale.getDefault()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val canAdd = SettingsEditing.canAddCitizenship(settings)
    SettingsGroupCard(
        group = SettingsGroup.CITIZENSHIP,
        subtitle = "Up to ${AppSettings.MaximumCitizenships} countries. Used only to find your embassy and your foreign ministry's emergency line.",
        highlighted = scope.highlighted == SettingsGroup.CITIZENSHIP,
        footer = CitizenshipFooter,
    ) {
        if (codes.isEmpty()) {
            SettingsBodyText("No citizenship chosen")
        } else {
            codes.forEach { code ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(countryNames.name(code), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    IconButton(
                        onClick = { scope.update { SettingsEditing.removeCitizenship(it, code) } },
                        modifier = Modifier.testTag(removeCitizenshipTag(code)),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove ${countryNames.name(code)}")
                    }
                }
            }
        }
        Button(
            enabled = canAdd,
            onClick = { picking = true },
            modifier = Modifier.testTag(AddCitizenshipButtonTag),
        ) {
            Text("Add country")
        }
        if (!canAdd) {
            SettingsBodyText("You can add up to ${AppSettings.MaximumCitizenships} countries.")
        }
    }
    if (picking) {
        CountryPickerDialog(
            title = "Choose citizenship",
            excluding = codes,
            onSelect = { code ->
                scope.update { SettingsEditing.addCitizenship(it, code) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun AppearanceGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.APPEARANCE,
        subtitle = "System follows your Android light or dark setting.",
        highlighted = scope.highlighted == SettingsGroup.APPEARANCE,
    ) {
        SettingsChoiceRow(
            options = AppearanceMode.entries,
            selected = scope.settings.appearanceMode,
            label = { mode -> mode.name.lowercase().replaceFirstChar(Char::titlecase) },
            onSelect = { mode -> scope.update { it.copy(appearanceMode = mode) } },
            tagFor = { mode -> "settings_appearance_${mode.name}" },
        )
    }
}

@Composable
private fun StatusTileGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.STATUS_TILE,
        subtitle = "The Quick Settings tile shows one status line at a glance.",
        highlighted = scope.highlighted == SettingsGroup.STATUS_TILE,
        footer = "Only caution or attention items replace the usual status. Add the tile from the Quick Settings editor.",
    ) {
        SettingsToggleRow(
            title = "Show top heads-up in the Quick Settings tile",
            description = "Put the most important heads-up in the tile's text when there is one.",
            checked = scope.settings.statusShowsHeadsUp,
            switchModifier = Modifier.testTag("settings_status_heads_up_switch"),
        ) { enabled -> scope.update { it.copy(statusShowsHeadsUp = enabled) } }
    }
}

@Composable
private fun ArrivalBriefingGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.ARRIVAL_BRIEFING,
        subtitle = "A short note when you arrive in a new country.",
        highlighted = scope.highlighted == SettingsGroup.ARRIVAL_BRIEFING,
    ) {
        SettingsToggleRow(
            title = "Show arrival briefing",
            description = "Greet each new country with what matters first, once per arrival.",
            checked = scope.settings.arrivalBriefingEnabled,
            switchModifier = Modifier.testTag("settings_arrival_briefing_switch"),
        ) { enabled -> scope.update { it.copy(arrivalBriefingEnabled = enabled) } }
    }
}

@Composable
private fun DashboardLayoutGroup(scope: SettingsGroupScope) {
    val settings = scope.settings
    SettingsGroupCard(
        group = SettingsGroup.DASHBOARD_LAYOUT,
        subtitle = "Order the dashboard the way you scan it. Width preferences matter on wider layouts.",
        highlighted = scope.highlighted == SettingsGroup.DASHBOARD_LAYOUT,
        badges = listOf(settings.travelProfile.title to NomadBadgeTone.Good),
        footer = "Reset restores the ${settings.travelProfile.title} order, wide cards and expanded cards.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            settings.dashboardCardOrder.forEach { card ->
                DashboardLayoutRow(
                    card = card,
                    widthMode = settings.dashboardCardWidthModes[card] ?: DashboardCardWidthMode.WIDE,
                    onToggleWidth = { scope.update { it.withCardWidthToggled(card) } },
                    onMoveUp = { scope.update { it.withCardMoved(card, -1) } },
                    onMoveDown = { scope.update { it.withCardMoved(card, 1) } },
                )
            }
        }
        OutlinedButton(
            onClick = { scope.update(AppSettings::withDashboardLayoutReset) },
            modifier = Modifier.testTag(ResetDashboardLayoutButtonTag),
        ) {
            Text("Reset dashboard layout")
        }
    }
}

@Composable
private fun DashboardLayoutRow(
    card: DashboardCardId,
    widthMode: DashboardCardWidthMode,
    onToggleWidth: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(card.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            NomadStatusBadge(
                text = if (widthMode == DashboardCardWidthMode.NARROW) "Narrow on large screens" else "Wide by default",
                tone = NomadBadgeTone.Info,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleWidth) {
                Text(if (widthMode == DashboardCardWidthMode.NARROW) "Make wide" else "Make narrow")
            }
            IconButton(onClick = onMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${card.label()} up")
            }
            IconButton(onClick = onMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${card.label()} down")
            }
        }
    }
}

private fun DashboardCardId.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)

private const val KeptCustomOrderNote = "Your custom card order is kept."

internal const val CitizenshipFooter =
    "Stays on this device. It is never sent anywhere, written to logs or included in analytics."
