package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.model.ResourceMode

internal const val OpenOfflineEssentialsButtonTag = "settings_open_offline_essentials_button"
internal const val QuietAlertsSwitchTag = "settings_quiet_alerts_switch"

internal fun resourceModeTag(mode: ResourceMode): String = "settings_data_usage_${mode.name}"

@Composable
internal fun ConnectionGroup(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group) {
        SettingsGroup.DATA_USAGE -> DataUsageGroup(scope)
        SettingsGroup.OFFLINE_ESSENTIALS -> OfflineEssentialsGroup(scope)
        SettingsGroup.QUIET_ALERTS -> QuietAlertsGroup(scope)
        else -> error("$group is not a Connection & Data group")
    }
}

@Composable
private fun DataUsageGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.DATA_USAGE,
        subtitle = "Auto reduces optional downloads on metered, roaming or constrained connections and under battery or thermal pressure. Saved cards remain available.",
        highlighted = scope.highlighted == SettingsGroup.DATA_USAGE,
        footer = "Low Data defers fuel catalog downloads. Use a card's Refresh action when you need an update.",
    ) {
        SettingsChoiceRow(
            options = ResourceMode.entries,
            selected = scope.settings.resourceMode,
            label = { mode ->
                when (mode) {
                    ResourceMode.AUTO -> "Auto (recommended)"
                    ResourceMode.NORMAL -> "Normal"
                    ResourceMode.LOW_DATA -> "Low Data"
                }
            },
            onSelect = { mode -> scope.update { it.copy(resourceMode = mode) } },
            tagFor = ::resourceModeTag,
        )
    }
}

@Composable
private fun OfflineEssentialsGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.OFFLINE_ESSENTIALS,
        subtitle = "Addresses, contacts, notes and map links you choose to keep for when there is no signal.",
        highlighted = scope.highlighted == SettingsGroup.OFFLINE_ESSENTIALS,
        footer = "Essentials stay on this device unless you export a backup yourself. Enabled travel cards also stay available offline with their last update time.",
    ) {
        OutlinedButton(
            onClick = scope.actions.onOpenCompanion,
            modifier = Modifier.testTag(OpenOfflineEssentialsButtonTag),
        ) {
            Text("Open offline essentials")
        }
    }
}

@Composable
private fun QuietAlertsGroup(scope: SettingsGroupScope) {
    val preferences = scope.state.companionPreferences
    val notificationPermission = rememberNotificationPermission { granted ->
        scope.updateCompanion { it.copy(quietNotificationsEnabled = granted) }
    }
    SettingsGroupCard(
        group = SettingsGroup.QUIET_ALERTS,
        subtitle = "Quiet notifications about your connection, only while the app is open.",
        highlighted = scope.highlighted == SettingsGroup.QUIET_ALERTS,
        footer = "Alerts use sustained state changes, cooldowns, and quiet hours. They never include a location or IP address.",
    ) {
        SettingsToggleRow(
            title = "Allow quiet connection alerts",
            description = "Unknown or stale readings never create a failure alert.",
            checked = preferences.quietNotificationsEnabled,
            switchModifier = Modifier.testTag(QuietAlertsSwitchTag),
        ) { enabled ->
            if (enabled && !notificationPermission.isGranted) {
                notificationPermission.request()
            } else {
                scope.updateCompanion { it.copy(quietNotificationsEnabled = enabled) }
            }
        }
        SettingsStepperRow(
            title = "Quiet hours start",
            value = preferences.quietHoursStartMinute.formatClock(),
            onDecrement = { scope.updateCompanion { it.copy(quietHoursStartMinute = it.quietHoursStartMinute.shiftedByHours(-1)) } },
            onIncrement = { scope.updateCompanion { it.copy(quietHoursStartMinute = it.quietHoursStartMinute.shiftedByHours(1)) } },
        )
        SettingsStepperRow(
            title = "Quiet hours end",
            value = preferences.quietHoursEndMinute.formatClock(),
            onDecrement = { scope.updateCompanion { it.copy(quietHoursEndMinute = it.quietHoursEndMinute.shiftedByHours(-1)) } },
            onIncrement = { scope.updateCompanion { it.copy(quietHoursEndMinute = it.quietHoursEndMinute.shiftedByHours(1)) } },
        )
        SettingsStepperRow(
            title = "Minimum time between alerts",
            value = preferences.notificationCooldownMinutes.formatCooldown(),
            onDecrement = { scope.updateCompanion { it.copy(notificationCooldownMinutes = it.notificationCooldownMinutes.steppedCooldown(-1)) } },
            onIncrement = { scope.updateCompanion { it.copy(notificationCooldownMinutes = it.notificationCooldownMinutes.steppedCooldown(1)) } },
            canDecrement = preferences.notificationCooldownMinutes > CompanionCooldownRange.first,
            canIncrement = preferences.notificationCooldownMinutes < CompanionCooldownRange.last,
        )
        if (preferences.quietNotificationsEnabled || !notificationPermission.isGranted) {
            SettingsBodyText(
                if (notificationPermission.isGranted) {
                    "Android notifications are allowed for Nomad Dashboard."
                } else {
                    "Android notifications are off for Nomad Dashboard, so alerts cannot be shown."
                },
                isError = !notificationPermission.isGranted,
            )
        }
        if (!notificationPermission.isGranted) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = notificationPermission.request) { Text("Allow notifications") }
                TextButton(onClick = notificationPermission.openNotificationSettings) { Text("Open notification settings") }
            }
        }
        scope.state.companionError?.let { SettingsBodyText(it, isError = true) }
    }
}

internal val CompanionCooldownRange = 30..480

internal fun Int.formatClock(): String = "%02d:%02d".format(this / 60, this % 60)

/** Moves a minute-of-day by whole hours, wrapping around midnight. */
internal fun Int.shiftedByHours(hours: Int): Int = ((this + hours * 60) % 1_440 + 1_440) % 1_440

internal fun Int.formatCooldown(): String = when {
    this < 60 -> "$this minutes"
    this % 60 == 0 -> if (this == 60) "1 hour" else "${this / 60} hours"
    else -> "${this / 60} h ${this % 60} min"
}

/** Steps the cooldown by 30 minutes, staying within [CompanionCooldownRange]. */
internal fun Int.steppedCooldown(direction: Int): Int =
    (this + direction * 30).coerceIn(CompanionCooldownRange)
