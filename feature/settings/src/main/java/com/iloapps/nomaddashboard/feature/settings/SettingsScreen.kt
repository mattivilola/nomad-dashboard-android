package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader
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
                NomadSectionHeader(
                    title = "Settings",
                    subtitle = "Manage Android-first behavior while preserving macOS parity goals.",
                )
                SettingsToggle("Use current location for weather", settings.useCurrentLocationForWeather) {
                    onUpdate { current -> current.copy(useCurrentLocationForWeather = it) }
                }
                SettingsToggle("Use current location for visited places", settings.useCurrentLocationForVisitedPlaces) {
                    onUpdate { current -> current.copy(useCurrentLocationForVisitedPlaces = it) }
                }
                SettingsToggle("Show external IP location", settings.publicIpGeolocationEnabled) {
                    onUpdate { current -> current.copy(publicIpGeolocationEnabled = it) }
                }
                SettingsToggle(
                    title = "Expand weather forecast",
                    checked = settings.weatherForecastExpanded,
                    switchModifier = Modifier.testTag("settings_expand_weather_forecast_switch"),
                ) {
                    onUpdate { current -> current.copy(weatherForecastExpanded = it) }
                }
                SettingsToggle("Enable fuel prices", settings.fuelPricesEnabled) {
                    onUpdate { current -> current.copy(fuelPricesEnabled = it) }
                }
                SettingsToggle("Enable emergency care", settings.emergencyCareEnabled) {
                    onUpdate { current -> current.copy(emergencyCareEnabled = it) }
                }
                SettingsToggle("Enable visited places", settings.visitedPlacesEnabled) {
                    onUpdate { current -> current.copy(visitedPlacesEnabled = it) }
                }
                SettingsToggle("Enable project time tracking", settings.projectTimeTrackingEnabled) {
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
                NomadSectionHeader(
                    title = "Dashboard Layout",
                    subtitle = "Adjust card order and width preferences. Narrow cards take effect on larger layouts.",
                )
                settings.dashboardCardOrder.forEach { card ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(card.label(), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Width: ${settings.dashboardCardWidthModes[card] ?: DashboardCardWidthMode.WIDE}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
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
                            }) {
                                Text(if (settings.dashboardCardWidthModes[card] == DashboardCardWidthMode.NARROW) "N" else "W")
                            }
                            IconButton(onClick = { onUpdate { it.moveCard(card, -1) } }) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(onClick = { onUpdate { it.moveCard(card, 1) } }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    }
                }
            }
        }
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
        NomadSectionHeader(
            title = "Provider Credentials",
            subtitle = "User-supplied keys stay encrypted on this device only and are excluded from Android backup.",
        )
        OutlinedTextField(
            value = tankerkoenigApiKey,
            onValueChange = { tankerkoenigApiKey = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TankerkoenigApiKeyFieldTag),
            label = { Text("Tankerkönig API key") },
            supportingText = {
                Text("Required only for Germany fuel lookups. Leave blank to disable the Germany provider.")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
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
                .padding(top = 16.dp)
                .testTag(ReliefWebAppNameFieldTag),
            label = { Text("ReliefWeb app name") },
            supportingText = {
                Text("Required only for regional security lookups. Save the approved ReliefWeb app name here.")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
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
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    checked: Boolean,
    switchModifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier,
        )
    }
}

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
