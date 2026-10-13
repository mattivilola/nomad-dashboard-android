package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.model.AppSettings

internal const val OpenWorkplaceDiaryButtonTag = "settings_open_workplace_diary_button"

@Composable
internal fun WorkGroup(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group) {
        SettingsGroup.TIME_TRACKING -> TimeTrackingGroup(scope)
        SettingsGroup.WORKPLACE_DIARY -> WorkplaceDiaryGroup(scope)
        else -> error("$group is not a Work group")
    }
}

@Composable
private fun TimeTrackingGroup(scope: SettingsGroupScope) {
    val settings = scope.settings
    SettingsGroupCard(
        group = SettingsGroup.TIME_TRACKING,
        subtitle = "A local project ledger with foreground tracking.",
        highlighted = scope.highlighted == SettingsGroup.TIME_TRACKING,
        badges = listOf(
            "${settings.projectTimeTrackingAutoStartMinutes.formatTimeWindow()}-${settings.projectTimeTrackingAutoStopMinutes.formatTimeWindow()}" to
                NomadBadgeTone.Info,
        ),
        footer = "Project time tracking stays on this device. Inside the tracking window the app resumes continuous capture when " +
            "the buffer is empty; outside it you can still run manual capture.",
    ) {
        SettingsToggleRow(
            title = "Enable project time tracking",
            description = "Turn on the local project ledger and foreground tracking controls.",
            checked = settings.projectTimeTrackingEnabled,
            switchModifier = Modifier.testTag("settings_time_tracking_switch"),
        ) { enabled -> scope.update { it.copy(projectTimeTrackingEnabled = enabled) } }
        TrackingWindowFields(
            settings = settings,
            onSave = { startMinutes, stopMinutes ->
                scope.update {
                    it.copy(
                        projectTimeTrackingAutoStartMinutes = startMinutes,
                        projectTimeTrackingAutoStopMinutes = stopMinutes,
                    )
                }
            },
        )
    }
}

@Composable
private fun TrackingWindowFields(
    settings: AppSettings,
    onSave: (startMinutes: Int, stopMinutes: Int) -> Unit,
) {
    var startText by rememberSaveable(settings.projectTimeTrackingAutoStartMinutes) {
        mutableStateOf(settings.projectTimeTrackingAutoStartMinutes.formatTimeWindow())
    }
    var stopText by rememberSaveable(settings.projectTimeTrackingAutoStopMinutes) {
        mutableStateOf(settings.projectTimeTrackingAutoStopMinutes.formatTimeWindow())
    }
    val startMinutes = parseTimeWindow(startText)
    val stopMinutes = parseTimeWindow(stopText)
    val isValid = startMinutes != null && stopMinutes != null && stopMinutes > startMinutes

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = startText,
            onValueChange = { startText = it },
            modifier = Modifier.weight(1f),
            label = { Text("Start") },
            supportingText = { Text("HH:MM") },
            singleLine = true,
        )
        OutlinedTextField(
            value = stopText,
            onValueChange = { stopText = it },
            modifier = Modifier.weight(1f),
            label = { Text("Stop") },
            supportingText = { Text("HH:MM") },
            singleLine = true,
        )
    }
    Text(
        text = if (isValid) {
            "The buffer will stop counting after ${stopMinutes!!.formatTimeWindow()} unless you manually resume."
        } else {
            "Use a same-day range such as 07:00 to 19:00."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (isValid) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.secondary
        },
    )
    Button(
        onClick = { onSave(startMinutes!!, stopMinutes!!) },
        enabled = isValid,
    ) {
        Text("Save tracking window")
    }
}

@Composable
private fun WorkplaceDiaryGroup(scope: SettingsGroupScope) {
    val preferences = scope.state.companionPreferences
    SettingsGroupCard(
        group = SettingsGroup.WORKPLACE_DIARY,
        subtitle = "Opt in, then save a foreground observation of where you worked and how the connection was.",
        highlighted = scope.highlighted == SettingsGroup.WORKPLACE_DIARY,
        footer = "Observations are saved only when you choose, on this device. Connection names and location-based Maps links " +
            "are kept only with their own opt-in.",
    ) {
        SettingsToggleRow(
            title = "Save workplace observations",
            description = "Allow the diary to store the observations you save.",
            checked = preferences.workplaceDiaryEnabled,
            switchModifier = Modifier.testTag("settings_workplace_diary_switch"),
        ) { enabled -> scope.updateCompanion { it.copy(workplaceDiaryEnabled = enabled) } }
        SettingsToggleRow(
            title = "Retain connection name",
            description = "Keep the Wi-Fi network name with each observation.",
            checked = preferences.retainConnectionName,
            switchModifier = Modifier.testTag("settings_retain_connection_name_switch"),
        ) { enabled -> scope.updateCompanion { it.copy(retainConnectionName = enabled) } }
        SettingsToggleRow(
            title = "Retain location-based Maps link",
            description = "Keep a Maps link to where each observation was made.",
            checked = preferences.retainWorkplaceMapLink,
            switchModifier = Modifier.testTag("settings_retain_map_link_switch"),
        ) { enabled -> scope.updateCompanion { it.copy(retainWorkplaceMapLink = enabled) } }
        scope.state.companionError?.let { SettingsBodyText(it, isError = true) }
        OutlinedButton(
            onClick = scope.actions.onOpenCompanion,
            modifier = Modifier.testTag(OpenWorkplaceDiaryButtonTag),
        ) {
            Text("Open workplace diary")
        }
    }
}

internal fun Int.formatTimeWindow(): String = "%02d:%02d".format(this / 60, this % 60)

internal fun parseTimeWindow(raw: String): Int? {
    val parts = raw.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
