package com.iloapps.nomaddashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScreen(settings = settings, onUpdate = viewModel::update)
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            NomadCard {
                NomadSectionHeader(
                    title = "Settings",
                    subtitle = "Manage Android-first behavior while preserving macOS parity goals.",
                )
                SettingsToggle("Use current location for weather", settings.useCurrentLocationForWeather) {
                    onUpdate { current -> current.copy(useCurrentLocationForWeather = it) }
                }
                SettingsToggle("Show external IP location", settings.publicIpGeolocationEnabled) {
                    onUpdate { current -> current.copy(publicIpGeolocationEnabled = it) }
                }
                SettingsToggle("Expand weather forecast", settings.weatherForecastExpanded) {
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
private fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
