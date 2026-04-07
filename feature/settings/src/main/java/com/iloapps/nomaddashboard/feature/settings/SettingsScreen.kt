package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricBlock
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onUpdate = viewModel::update,
        onUpdateProviderCredentials = viewModel::updateProviderCredentials,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onUpdateProviderCredentials: ((ProviderCredentialSettings) -> ProviderCredentialSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = uiState.settings
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NomadCard {
                NomadTopBar(
                    title = "Settings",
                    subtitle = "Control room",
                    supportingText = "Tune Android behavior while keeping the app local-first, travel-focused, and parity-minded.",
                    badgeText = "Device local only",
                    badgeTone = NomadBadgeTone.Good,
                    trailing = {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NomadMetricBlock("Sources", enabledSourceCount(settings).toString(), "active capture and provider surfaces")
                    NomadMetricBlock("Cards", settings.dashboardCardOrder.size.toString(), "dashboard modules available")
                }
            }
        }

        item {
            SettingsGroupCard(
                title = "Location And Capture",
                subtitle = "Choose which travel signals the app may use when refreshing weather and visited history.",
            ) {
                SettingsToggleRow(
                    title = "Use current location for weather",
                    description = "Prefer on-device location for the weather card when available.",
                    checked = settings.useCurrentLocationForWeather,
                ) {
                    onUpdate { current -> current.copy(useCurrentLocationForWeather = it) }
                }
                SettingsToggleRow(
                    title = "Use current location for visited places",
                    description = "Capture device-derived visited places in addition to IP-based travel observations.",
                    checked = settings.useCurrentLocationForVisitedPlaces,
                ) {
                    onUpdate { current -> current.copy(useCurrentLocationForVisitedPlaces = it) }
                }
                SettingsToggleRow(
                    title = "Show external IP location",
                    description = "Keep public-IP travel context visible on the dashboard and in visited history.",
                    checked = settings.publicIpGeolocationEnabled,
                ) {
                    onUpdate { current -> current.copy(publicIpGeolocationEnabled = it) }
                }
                SettingsToggleRow(
                    title = "Enable visited places",
                    description = "Keep the local visited-map ledger active on this device.",
                    checked = settings.visitedPlacesEnabled,
                ) {
                    onUpdate { current -> current.copy(visitedPlacesEnabled = it) }
                }
            }
        }

        item {
            SettingsGroupCard(
                title = "Dashboard Modules",
                subtitle = "Control which modules are active and how much detail the main screen should expose.",
            ) {
                SettingsToggleRow(
                    title = "Expand weather forecast",
                    description = "Show the longer forecast block inside the main weather card.",
                    checked = settings.weatherForecastExpanded,
                    switchModifier = Modifier.testTag("settings_expand_weather_forecast_switch"),
                ) {
                    onUpdate { current -> current.copy(weatherForecastExpanded = it) }
                }
                SettingsToggleRow(
                    title = "Enable fuel prices",
                    description = "Keep nearby fuel rows visible when country coverage is available.",
                    checked = settings.fuelPricesEnabled,
                ) {
                    onUpdate { current -> current.copy(fuelPricesEnabled = it) }
                }
                SettingsToggleRow(
                    title = "Enable emergency care",
                    description = "Reserve dashboard space for nearby hospital lookup and quick map access.",
                    checked = settings.emergencyCareEnabled,
                ) {
                    onUpdate { current -> current.copy(emergencyCareEnabled = it) }
                }
                SettingsToggleRow(
                    title = "Enable project time tracking",
                    description = "Turn on the local project ledger and foreground tracking controls.",
                    checked = settings.projectTimeTrackingEnabled,
                ) {
                    onUpdate { current -> current.copy(projectTimeTrackingEnabled = it) }
                }
            }
        }

        item {
            ProviderCredentialCard(
                providerCredentials = uiState.providerCredentials,
                onSaveTankerkoenigApiKey = { apiKey ->
                    onUpdateProviderCredentials { current ->
                        current.copy(tankerkoenigApiKey = apiKey)
                    }
                },
                onSaveReliefWebAppName = { appName ->
                    onUpdateProviderCredentials { current ->
                        current.copy(reliefWebAppName = appName)
                    }
                },
            )
        }

        item {
            NomadCard {
                NomadSectionClusterHeader(
                    title = "Dashboard Order",
                    subtitle = "Prioritize the phone dashboard the way you actually scan it. Width preferences still matter on wider layouts.",
                    badges = listOf("Weather-first" to NomadBadgeTone.Good),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    settings.dashboardCardOrder.forEach { card ->
                        DashboardLayoutRow(
                            card = card,
                            widthMode = settings.dashboardCardWidthModes[card] ?: DashboardCardWidthMode.WIDE,
                            onToggleWidth = {
                                onUpdate { current ->
                                    current.copy(
                                        dashboardCardWidthModes = current.dashboardCardWidthModes.toMutableMap().apply {
                                            this[card] = if (this[card] == DashboardCardWidthMode.NARROW) {
                                                DashboardCardWidthMode.WIDE
                                            } else {
                                                DashboardCardWidthMode.NARROW
                                            }
                                        },
                                    )
                                }
                            },
                            onMoveUp = { onUpdate { it.moveCard(card, -1) } },
                            onMoveDown = { onUpdate { it.moveCard(card, 1) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    NomadCard {
        NomadSectionClusterHeader(title = title, subtitle = subtitle)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun ProviderCredentialCard(
    providerCredentials: ProviderCredentialSettings,
    onSaveTankerkoenigApiKey: (String) -> Unit,
    onSaveReliefWebAppName: (String) -> Unit,
) {
    var tankerkoenigApiKey by rememberSaveable(providerCredentials.tankerkoenigApiKey) {
        mutableStateOf(providerCredentials.tankerkoenigApiKey)
    }
    var reliefWebAppName by rememberSaveable(providerCredentials.reliefWebAppName) {
        mutableStateOf(providerCredentials.reliefWebAppName)
    }
    val isTankerkoenigDirty = tankerkoenigApiKey != providerCredentials.tankerkoenigApiKey
    val isReliefWebDirty = reliefWebAppName != providerCredentials.reliefWebAppName

    NomadCard {
        NomadSectionClusterHeader(
            title = "Provider Credentials",
            subtitle = "User-managed provider values stay encrypted on this device only and never ship inside the app artifact.",
            badges = listOf("Encrypted on-device" to NomadBadgeTone.Good),
        )
        OutlinedTextField(
            value = tankerkoenigApiKey,
            onValueChange = { tankerkoenigApiKey = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TankerkoenigApiKeyFieldTag),
            label = { Text("Tankerkönig API key") },
            supportingText = {
                Text("Required only for Germany fuel prices.")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            TextButton(
                enabled = providerCredentials.tankerkoenigApiKey.isNotBlank() || tankerkoenigApiKey.isNotBlank(),
                onClick = {
                    tankerkoenigApiKey = ""
                    onSaveTankerkoenigApiKey("")
                },
            ) {
                Text("Clear key")
            }
            Button(
                enabled = isTankerkoenigDirty,
                modifier = Modifier.testTag(TankerkoenigApiKeySaveButtonTag),
                onClick = { onSaveTankerkoenigApiKey(tankerkoenigApiKey) },
            ) {
                Text("Save key")
            }
        }

        OutlinedTextField(
            value = reliefWebAppName,
            onValueChange = { reliefWebAppName = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .testTag(ReliefWebAppNameFieldTag),
            label = { Text("ReliefWeb app name") },
            supportingText = {
                Text("Required only for regional security lookups.")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            TextButton(
                enabled = providerCredentials.reliefWebAppName.isNotBlank() || reliefWebAppName.isNotBlank(),
                onClick = {
                    reliefWebAppName = ""
                    onSaveReliefWebAppName("")
                },
            ) {
                Text("Clear app name")
            }
            Button(
                enabled = isReliefWebDirty,
                modifier = Modifier.testTag(ReliefWebAppNameSaveButtonTag),
                onClick = { onSaveReliefWebAppName(reliefWebAppName) },
            ) {
                Text("Save app name")
            }
        }

        Text(
            text = "Map SDK keys remain app-level local configuration, not per-user secrets in this screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    switchModifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier,
        )
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
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
            }
        }
    }
}

private fun enabledSourceCount(settings: AppSettings): Int =
    listOf(
        settings.useCurrentLocationForWeather,
        settings.useCurrentLocationForVisitedPlaces,
        settings.publicIpGeolocationEnabled,
        settings.fuelPricesEnabled,
        settings.emergencyCareEnabled,
        settings.projectTimeTrackingEnabled,
    ).count { it }

private fun DashboardCardId.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)

private fun AppSettings.moveCard(card: DashboardCardId, delta: Int): AppSettings {
    val list = dashboardCardOrder.toMutableList()
    val index = list.indexOf(card)
    if (index == -1) return this
    val target = (index + delta).coerceIn(0, list.lastIndex)
    list.removeAt(index)
    list.add(target, card)
    return copy(dashboardCardOrder = list)
}

private const val TankerkoenigApiKeyFieldTag = "settings_tankerkoenig_api_key_field"
private const val TankerkoenigApiKeySaveButtonTag = "settings_tankerkoenig_api_key_save_button"
private const val ReliefWebAppNameFieldTag = "settings_reliefweb_app_name_field"
private const val ReliefWebAppNameSaveButtonTag = "settings_reliefweb_app_name_save_button"
