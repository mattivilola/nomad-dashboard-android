package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal const val RequestLocationPermissionButtonTag = "settings_request_location_permission_button"
internal const val OpenAppSettingsButtonTag = "settings_open_app_settings_button"
internal const val OpenVisitedMapButtonTag = "settings_open_visited_map_button"

@Composable
internal fun PrivacyGroup(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group) {
        SettingsGroup.LOCATION_PERMISSION -> LocationPermissionGroup(scope)
        SettingsGroup.EXTERNAL_IP -> ExternalIpGroup(scope)
        SettingsGroup.WIFI_NAMES -> WifiNamesGroup(scope)
        SettingsGroup.VISITED_PLACES -> VisitedPlacesGroup(scope)
        SettingsGroup.ANALYTICS -> AnalyticsGroup(scope)
        else -> error("$group is not a Privacy & Location group")
    }
}

@Composable
private fun LocationPermissionGroup(scope: SettingsGroupScope) {
    val location = rememberLocationPermission()
    SettingsGroupCard(
        group = SettingsGroup.LOCATION_PERMISSION,
        subtitle = when (location.access) {
            LocationAccess.PRECISE -> "Android location access is allowed (precise)."
            LocationAccess.APPROXIMATE -> "Android location access is allowed (approximate only)."
            LocationAccess.NONE -> "Android location access is not allowed."
        },
        highlighted = scope.highlighted == SettingsGroup.LOCATION_PERMISSION,
        footer = "Weather, nearby fuel prices, nearby emergency hospitals and visited places use device location only when you opt in.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (location.access != LocationAccess.PRECISE) {
                Button(
                    onClick = location.request,
                    modifier = Modifier.testTag(RequestLocationPermissionButtonTag),
                ) {
                    Text("Allow location")
                }
            }
            OutlinedButton(
                onClick = location.openAppSettings,
                modifier = Modifier.testTag(OpenAppSettingsButtonTag),
            ) {
                Text("Open Android settings")
            }
        }
    }
}

@Composable
private fun ExternalIpGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.EXTERNAL_IP,
        subtitle = "Show the city and country your connection appears to be in.",
        highlighted = scope.highlighted == SettingsGroup.EXTERNAL_IP,
        footer = "External IP lookups use a third-party geolocation service to show city and country, and are on by default.",
    ) {
        SettingsToggleRow(
            title = "Show external IP location",
            description = "Keep public-IP travel context visible on the dashboard and in visited history.",
            checked = scope.settings.publicIpGeolocationEnabled,
            switchModifier = Modifier.testTag("settings_external_ip_switch"),
        ) { enabled -> scope.update { it.copy(publicIpGeolocationEnabled = enabled) } }
    }
}

@Composable
private fun WifiNamesGroup(scope: SettingsGroupScope) {
    val location = rememberLocationPermission()
    SettingsGroupCard(
        group = SettingsGroup.WIFI_NAMES,
        subtitle = if (location.isGranted) {
            "Wi-Fi network names can be read."
        } else {
            "Wi-Fi network names cannot be read until location access is allowed."
        },
        highlighted = scope.highlighted == SettingsGroup.WIFI_NAMES,
        footer = "Android only shares Wi-Fi network names (SSIDs) with apps that may use your location, so the connection card " +
            "shows a name only while location access is allowed.",
    ) {
        if (!location.isGranted) {
            Button(onClick = location.request) { Text("Allow location") }
        }
    }
}

@Composable
private fun VisitedPlacesGroup(scope: SettingsGroupScope) {
    val settings = scope.settings
    SettingsGroupCard(
        group = SettingsGroup.VISITED_PLACES,
        subtitle = "A local ledger of the places and countries you have been to.",
        highlighted = scope.highlighted == SettingsGroup.VISITED_PLACES,
        footer = "Visited places and country-day history stay on this device until you clear them in the Visited tab.",
    ) {
        SettingsToggleRow(
            title = "Enable visited places",
            description = "Keep the local visited-map ledger active on this device.",
            checked = settings.visitedPlacesEnabled,
            switchModifier = Modifier.testTag("settings_visited_places_switch"),
        ) { enabled -> scope.update { it.copy(visitedPlacesEnabled = enabled) } }
        SettingsToggleRow(
            title = "Use current location for visited places",
            description = "Prefer device-derived visited places when Android location is available.",
            checked = settings.useCurrentLocationForVisitedPlaces,
            switchModifier = Modifier.testTag("settings_visited_location_switch"),
        ) { enabled -> scope.update { it.copy(useCurrentLocationForVisitedPlaces = enabled) } }
        OutlinedButton(
            onClick = scope.actions.onOpenVisitedMap,
            modifier = Modifier.testTag(OpenVisitedMapButtonTag),
        ) {
            Text("Open visited map")
        }
    }
}

@Composable
private fun AnalyticsGroup(scope: SettingsGroupScope) {
    SettingsGroupCard(
        group = SettingsGroup.ANALYTICS,
        subtitle = "Help estimate how the app is used.",
        highlighted = scope.highlighted == SettingsGroup.ANALYTICS,
        footer = "Only anonymous usage counts. Your location, citizenship, Wi-Fi names and IP address are never part of it.",
    ) {
        SettingsToggleRow(
            title = "Share anonymous analytics",
            description = "Turn off to stop sharing usage counts.",
            checked = scope.settings.shareAnonymousAnalytics,
            switchModifier = Modifier.testTag("settings_analytics_switch"),
        ) { enabled -> scope.update { it.copy(shareAnonymousAnalytics = enabled) } }
    }
}
