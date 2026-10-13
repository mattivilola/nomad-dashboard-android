package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal const val RefreshNowButtonTag = "settings_refresh_now_button"
internal const val OpenAboutButtonTag = "settings_open_about_button"

@Composable
internal fun AdvancedGroup(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group) {
        SettingsGroup.REFRESH -> RefreshGroup(scope)
        SettingsGroup.ABOUT -> AboutGroup(scope)
        else -> error("$group is not an Advanced group")
    }
}

@Composable
private fun RefreshGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.REFRESH,
        subtitle = "Fetch fresh data for the whole dashboard once, right now.",
        highlighted = scope.highlighted == SettingsGroup.REFRESH,
        footer = "Opening the dashboard reuses fresh data. Data usage mode decides how much optional data a refresh downloads.",
    ) {
        Button(
            onClick = scope.actions.onRefresh,
            modifier = Modifier.testTag(RefreshNowButtonTag),
        ) {
            Text("Refresh dashboard now")
        }
    }
}

@Composable
private fun AboutGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.ABOUT,
        subtitle = "Version, sources and credits for Nomad Dashboard.",
        highlighted = scope.highlighted == SettingsGroup.ABOUT,
    ) {
        OutlinedButton(
            onClick = scope.actions.onOpenAbout,
            modifier = Modifier.testTag(OpenAboutButtonTag),
        ) {
            Text("About Nomad Dashboard")
        }
    }
}
