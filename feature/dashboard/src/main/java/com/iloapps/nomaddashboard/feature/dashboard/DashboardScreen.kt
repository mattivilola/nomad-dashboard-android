package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadPill
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSummaryTile
import com.iloapps.nomaddashboard.core.model.DashboardCardId

@Composable
fun DashboardRoute(
    onOpenSettings: () -> Unit,
    onOpenVisited: () -> Unit,
    onOpenTimeTracking: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = uiState,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        onOpenVisited = onOpenVisited,
        onOpenTimeTracking = onOpenTimeTracking,
        onOpenAbout = onOpenAbout,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVisited: () -> Unit,
    onOpenTimeTracking: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Nomad Dashboard",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.snapshot.travelContext.city?.let { "$it instrument panel" } ?: "Travel-ready system telemetry",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NomadPill(text = "Settings", tint = MaterialTheme.colorScheme.surfaceVariant)
                    NomadPill(text = "Visited Map")
                    NomadPill(text = "Time Tracking")
                    NomadPill(text = "About")
                }
            }
        }

        item {
            BoxWithConstraints {
                val wide = maxWidth > 700.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NomadSummaryTile(state.snapshot.overallSummary, Modifier.weight(1f))
                        NomadSummaryTile(state.snapshot.networkSummary, Modifier.weight(1f))
                        NomadSummaryTile(state.snapshot.powerSummary, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NomadSummaryTile(state.snapshot.overallSummary)
                        NomadSummaryTile(state.snapshot.networkSummary)
                        NomadSummaryTile(state.snapshot.powerSummary)
                    }
                }
            }
        }

        items(state.settings.dashboardCardOrder, key = { it.name }) { cardId ->
            when (cardId) {
                DashboardCardId.CONNECTIVITY -> DashboardSectionCard(
                    title = "Travel Context",
                    subtitle = state.snapshot.travelContext.publicIp ?: "Network context",
                    lines = listOf(
                        "Internet: ${state.snapshot.connectivity.internetState}",
                        "Latency: ${state.snapshot.connectivity.latencyMs?.toInt()?.toString() ?: "n/a"} ms",
                        "Wi-Fi: ${state.snapshot.connectivity.wifiName ?: "Unavailable"}",
                        "VPN: ${if (state.snapshot.connectivity.vpnActive) "Active" else "Inactive"}",
                    ),
                )
                DashboardCardId.POWER -> DashboardSectionCard(
                    title = "Power",
                    subtitle = state.snapshot.power.batteryHealthSummary,
                    lines = listOf(
                        "Battery: ${state.snapshot.power.batteryPercent?.let { "$it%" } ?: "Estimating"}",
                        "Charging: ${if (state.snapshot.power.charging) "Yes" else "No"}",
                        "Drain: ${state.snapshot.power.dischargeWatts?.let { "%.1fW".format(it) } ?: "Unavailable"}",
                    ),
                )
                DashboardCardId.TIME_TRACKING -> DashboardSectionCard(
                    title = "Time Tracking",
                    subtitle = state.snapshot.timeTracking.headline,
                    lines = listOf(state.snapshot.timeTracking.detail),
                )
                DashboardCardId.TRAVEL_CONTEXT -> DashboardSectionCard(
                    title = "Travel Context",
                    subtitle = state.snapshot.travelContext.city ?: "Locating",
                    lines = listOf(
                        "Public IP: ${state.snapshot.travelContext.publicIp ?: "Unavailable"}",
                        "Country: ${state.snapshot.travelContext.country ?: "Unavailable"}",
                        "Time Zone: ${state.snapshot.travelContext.timeZoneId ?: state.snapshot.connectivity.timeZoneId}",
                    ),
                )
                DashboardCardId.FUEL_PRICES -> DashboardSectionCard(
                    title = "Fuel Prices",
                    subtitle = if (state.settings.fuelPricesEnabled) "Enabled" else "Off",
                    lines = listOf(state.snapshot.fuelPrices.summary),
                )
                DashboardCardId.EMERGENCY_CARE -> DashboardSectionCard(
                    title = "Emergency Care",
                    subtitle = if (state.settings.emergencyCareEnabled) "Enabled" else "Off",
                    lines = listOf(state.snapshot.emergencyCare.summary),
                )
                DashboardCardId.TRAVEL_ALERTS -> DashboardSectionCard(
                    title = "Travel Alerts",
                    subtitle = "Provider-ready",
                    lines = listOf(state.snapshot.travelAlerts.summary),
                )
                DashboardCardId.WEATHER -> DashboardSectionCard(
                    title = "Weather",
                    subtitle = state.snapshot.weather.summary,
                    lines = buildList {
                        add("Temperature: ${state.snapshot.weather.currentTemperatureCelsius?.let { "%.1f°C".format(it) } ?: "Unavailable"}")
                        add("Feels like: ${state.snapshot.weather.apparentTemperatureCelsius?.let { "%.1f°C".format(it) } ?: "Unavailable"}")
                        add("Wind: ${state.snapshot.weather.windSpeedKph?.let { "%.1f km/h".format(it) } ?: "Unavailable"}")
                        if (state.settings.weatherForecastExpanded) {
                            addAll(state.snapshot.weather.dailyForecast.take(3).map {
                                "${it.date.dayOfWeek.name.lowercase().replaceFirstChar(Char::titlecase)}: ${it.summary}, ${it.minCelsius?.toInt() ?: 0}°/${it.maxCelsius?.toInt() ?: 0}°"
                            })
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DashboardSectionCard(
    title: String,
    subtitle: String,
    lines: List<String>,
) {
    NomadCard {
        NomadSectionHeader(title = title, subtitle = subtitle)
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
