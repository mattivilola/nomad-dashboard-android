package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadActionChip
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadChartShell
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricBlock
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSummaryTile
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.SummaryTile
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVisited: () -> Unit,
    onOpenTimeTracking: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier.testTag("dashboard_top_bar"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NomadTopBar(
                    title = "Nomad Dashboard",
                    subtitle = dashboardLocationLabel(state),
                    supportingText = dashboardSupportLine(state),
                    badgeText = state.snapshot.overallSummary.headline,
                    badgeTone = toneForLevel(state.snapshot.overallSummary.level),
                    trailing = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NomadActionChip(label = "Visited", icon = Icons.Rounded.Map, onClick = onOpenVisited)
                    NomadActionChip(label = "Tracking", icon = Icons.Rounded.Timer, onClick = onOpenTimeTracking)
                    NomadActionChip(label = "About", icon = Icons.Rounded.Info, onClick = onOpenAbout)
                }
            }
        }

        item {
            DashboardSummaryStrip(
                overall = state.snapshot.overallSummary,
                network = state.snapshot.networkSummary,
                power = state.snapshot.powerSummary,
            )
        }

        items(state.settings.dashboardCardOrder, key = { it.name }) { cardId ->
            when (cardId) {
                DashboardCardId.WEATHER -> WeatherSectionCard(
                    snapshot = state.snapshot.weather,
                    surfSpot = state.settings.surfSpot,
                    forecastExpanded = state.settings.weatherForecastExpanded,
                    modifier = Modifier.testTag("dashboard_weather_card"),
                )

                DashboardCardId.TRAVEL_ALERTS -> TravelAlertsSectionCard(
                    snapshot = state.snapshot.travelAlerts,
                )

                DashboardCardId.CONNECTIVITY -> ConnectivitySectionCard(
                    state = state,
                )

                DashboardCardId.TRAVEL_CONTEXT -> TravelContextSectionCard(
                    state = state,
                    onOpenMap = {
                        state.snapshot.travelContext.latitude?.let { latitude ->
                            state.snapshot.travelContext.longitude?.let { longitude ->
                                uriHandler.openUri(mapSearchUrl(latitude, longitude))
                            }
                        }
                    },
                )

                DashboardCardId.FUEL_PRICES -> FuelPricesSectionCard(
                    enabled = state.settings.fuelPricesEnabled,
                    snapshot = state.snapshot.fuelPrices,
                    onOpenMap = { station ->
                        uriHandler.openUri(mapSearchUrl(station.latitude, station.longitude))
                    },
                )

                DashboardCardId.POWER -> PowerSectionCard(
                    state = state,
                )

                DashboardCardId.TIME_TRACKING -> TimeTrackingSectionCard(
                    headline = state.snapshot.timeTracking.headline,
                    detail = state.snapshot.timeTracking.detail,
                )

                DashboardCardId.EMERGENCY_CARE -> EmergencyCareSectionCard(
                    enabled = state.settings.emergencyCareEnabled,
                    snapshot = state.snapshot.emergencyCare,
                    onOpenMap = {
                        state.snapshot.emergencyCare.facility?.let { facility ->
                            uriHandler.openUri(mapSearchUrl(facility.latitude, facility.longitude))
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardSummaryStrip(
    overall: SummaryTile,
    network: SummaryTile,
    power: SummaryTile,
) {
    BoxWithConstraints(modifier = Modifier.testTag("dashboard_summary_strip")) {
        val spacing = 12.dp
        val columns = if (maxWidth > 330.dp) 3 else 2
        val tileWidth = (maxWidth - spacing * (columns - 1)) / columns
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            maxItemsInEachRow = columns,
        ) {
            CompactSummaryTile(tile = overall, modifier = Modifier.width(tileWidth))
            CompactSummaryTile(tile = network, modifier = Modifier.width(tileWidth))
            CompactSummaryTile(tile = power, modifier = Modifier.width(tileWidth))
        }
    }
}

@Composable
private fun CompactSummaryTile(
    tile: SummaryTile,
    modifier: Modifier = Modifier,
) {
    NomadSummaryTile(
        tile = tile,
        modifier = modifier.heightIn(min = 132.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeatherSectionCard(
    snapshot: WeatherSnapshot,
    surfSpot: SurfSpotConfiguration,
    forecastExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    NomadCard(modifier = modifier) {
        NomadSectionClusterHeader(
            title = "Weather",
            subtitle = snapshot.summary,
            badges = listOf(
                statusBadgeForWeather(snapshot),
                snapshot.sourceName to NomadBadgeTone.Info,
            ),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            maxItemsInEachRow = 2,
        ) {
            NomadMetricBlock("Current", snapshot.currentTemperatureCelsius.formatDegrees())
            NomadMetricBlock("Feels Like", snapshot.apparentTemperatureCelsius.formatDegrees())
            NomadMetricBlock("Rain Chance", snapshot.rainChancePercent?.let { "$it%" } ?: "n/a")
            NomadMetricBlock("Wind", snapshot.windSpeedKph?.let { "%.0f km/h".format(it) } ?: "Unavailable")
        }

        Text(
            text = "Next checkpoints",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            snapshot.dailyForecast.take(3).forEachIndexed { index, day ->
                ForecastCheckpoint(
                    day = day,
                    label = "+${(index + 1) * 24}h",
                )
            }
        }

        if (forecastExpanded && snapshot.dailyForecast.isNotEmpty()) {
            Text(
                text = "Forecast",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                snapshot.dailyForecast.take(5).forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = day.date.dayOfWeek.name.lowercase().replaceFirstChar(Char::titlecase),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = day.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            )
                        }
                        Text(
                            text = "${day.minCelsius?.toInt() ?: 0}° / ${day.maxCelsius?.toInt() ?: 0}°",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        NomadCard {
            NomadSectionClusterHeader(
                title = "Surf Spot",
                subtitle = surfSpot.name.ifBlank { "Configured surf spot" },
                badges = listOf("Android marine parity pending" to NomadBadgeTone.Warning),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2,
            ) {
                NomadMetricBlock("Wind", snapshot.windSpeedKph?.let { "%.0f km/h".format(it) } ?: "Unavailable")
                NomadMetricBlock("Direction", windDirectionLabel(snapshot.windDirectionDegrees))
            }
            Text(
                text = "The Android weather card now reserves a dedicated surf section so marine data can land without another layout rewrite.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectivitySectionCard(
    state: DashboardUiState,
) {
    NomadCard {
        NomadSectionClusterHeader(
            title = "Connectivity",
            subtitle = listOfNotNull(
                state.snapshot.connectivity.wifiName,
                state.snapshot.connectivity.internetState,
            ).joinToString(" · ").ifBlank { "Network detail" },
            badges = listOf(state.snapshot.networkSummary.headline to toneForLevel(state.snapshot.networkSummary.level)),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            NomadMetricBlock("Down", state.snapshot.connectivity.downloadMbps?.let { "%.1f Mbps".format(it) } ?: "n/a")
            NomadMetricBlock("Up", state.snapshot.connectivity.uploadMbps?.let { "%.1f Mbps".format(it) } ?: "n/a")
            NomadMetricBlock("Latency", state.snapshot.connectivity.latencyMs?.let { "${it.toInt()} ms" } ?: "n/a")
            NomadMetricBlock("Jitter", state.snapshot.connectivity.jitterMs?.let { "${it.toInt()} ms" } ?: "n/a")
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            NomadChartShell(
                title = "Throughput",
                subtitle = "History surface reserved for retained down/up samples.",
                modifier = Modifier.fillMaxWidth(0.48f),
            )
            NomadChartShell(
                title = "Latency",
                subtitle = "Realtime trend preview will unlock once retention lands.",
                modifier = Modifier.fillMaxWidth(0.48f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravelContextSectionCard(
    state: DashboardUiState,
    onOpenMap: () -> Unit,
) {
    NomadCard {
        NomadSectionClusterHeader(
            title = "Travel Context",
            subtitle = dashboardLocationLabel(state),
            badges = listOf(
                (state.snapshot.travelContext.publicIp ?: "No public IP") to NomadBadgeTone.Info,
            ),
            actions = {
                val hasCoordinates = state.snapshot.travelContext.latitude != null && state.snapshot.travelContext.longitude != null
                NomadActionChip(
                    label = "Map",
                    icon = Icons.Rounded.Map,
                    onClick = onOpenMap,
                    enabled = hasCoordinates,
                )
            },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            NomadMetricBlock("Region", state.snapshot.travelContext.region ?: "Unavailable")
            NomadMetricBlock("Country", state.snapshot.travelContext.country ?: "Unavailable")
            NomadMetricBlock("Time Zone", state.snapshot.travelContext.timeZoneId ?: state.snapshot.connectivity.timeZoneId)
            NomadMetricBlock("VPN", if (state.snapshot.connectivity.vpnActive) "Active" else "Inactive")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PowerSectionCard(
    state: DashboardUiState,
) {
    NomadCard {
        NomadSectionClusterHeader(
            title = "Power",
            subtitle = state.snapshot.power.batteryHealthSummary,
            badges = listOf(state.snapshot.powerSummary.headline to toneForLevel(state.snapshot.powerSummary.level)),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            NomadMetricBlock("Battery", state.snapshot.power.batteryPercent?.let { "$it%" } ?: "Estimating")
            NomadMetricBlock("Charging", if (state.snapshot.power.charging) "Yes" else "No")
            NomadMetricBlock("Drain", state.snapshot.power.dischargeWatts?.let { "%.1f W".format(it) } ?: "Unavailable")
            NomadMetricBlock("State", state.snapshot.powerSummary.detail)
        }
        NomadChartShell(
            title = "Battery History",
            subtitle = "Retention-backed charge and drain charts are queued next; this shell reserves the phone layout.",
        )
    }
}

@Composable
private fun TimeTrackingSectionCard(
    headline: String,
    detail: String,
) {
    NomadCard {
        NomadSectionClusterHeader(
            title = "Time Tracking",
            subtitle = detail,
            badges = listOf(headline to NomadBadgeTone.Accent),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelPricesSectionCard(
    enabled: Boolean,
    snapshot: FuelPriceSnapshot,
    onOpenMap: (FuelStationPrice) -> Unit,
) {
    if (enabled.not()) {
        DashboardNarrativeCard(
            title = "Fuel Prices",
            subtitle = "Off",
            lines = listOf("Enable fuel prices in Settings to compare nearby diesel and gasoline options on the dashboard."),
        )
        return
    }

    NomadCard {
        NomadSectionClusterHeader(
            title = "Fuel Prices",
            subtitle = fuelPricesSubtitle(enabled = true, snapshot = snapshot),
            badges = listOf(snapshot.sourceName to badgeToneForFuel(snapshot)),
        )
        when (snapshot.status) {
            FuelPriceStatus.READY -> {
                snapshot.diesel?.let { FuelPriceRow(price = it, onOpenMap = { onOpenMap(it) }) }
                snapshot.gasoline?.let { FuelPriceRow(price = it, onOpenMap = { onOpenMap(it) }) }
                listOfNotNull(snapshot.detail.takeIf(String::isNotBlank), snapshot.note).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            }
            else -> fuelPriceLines(enabled = true, snapshot = snapshot).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                )
            }
        }
    }
}

@Composable
private fun FuelPriceRow(
    price: FuelStationPrice,
    onOpenMap: () -> Unit,
) {
    NomadCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = when (price.fuelType) {
                        FuelType.DIESEL -> "Diesel"
                        FuelType.GASOLINE -> "Gasoline"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = price.stationName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(price.locality ?: price.address ?: "Unknown location")
                        append(" · ")
                        append(String.format(Locale.US, "%.1f km", price.distanceKilometers))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            Text(
                text = String.format(Locale.US, "%.3f %s/L", price.pricePerLiter, price.currencyCode),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NomadActionChip(label = "Map", icon = Icons.Rounded.Map, onClick = onOpenMap)
        }
    }
}

@Composable
private fun EmergencyCareSectionCard(
    enabled: Boolean,
    snapshot: EmergencyCareSnapshot,
    onOpenMap: () -> Unit,
) {
    val subtitle = when {
        enabled.not() -> "Off"
        snapshot.countryName != null -> "${snapshot.countryName} · within ${snapshot.searchRadiusKilometers.toInt()} km"
        else -> snapshot.detail
    }
    NomadCard {
        NomadSectionClusterHeader(
            title = "Emergency Care",
            subtitle = subtitle,
            badges = listOf(snapshot.sourceName to badgeToneForEmergency(snapshot)),
            actions = {
                val hasFacility = snapshot.facility != null
                NomadActionChip(
                    label = "Map",
                    icon = Icons.Rounded.Map,
                    onClick = onOpenMap,
                    enabled = hasFacility,
                )
            },
        )
        if (enabled.not()) {
            Text(
                text = "Enable emergency care in Settings to keep nearby hospitals visible from the main dashboard.",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            snapshot.facility?.let { facility ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = facility.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = facility.address ?: snapshot.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f km", facility.distanceKilometers),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } ?: Text(
                text = snapshot.detail,
                style = MaterialTheme.typography.bodyLarge,
            )
            snapshot.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
internal fun TravelAlertsSectionCard(
    snapshot: TravelAlertsSnapshot,
    modifier: Modifier = Modifier,
) {
    NomadCard(modifier = modifier.testTag(TravelAlertsCardTag)) {
        NomadSectionClusterHeader(
            title = "Travel Alerts",
            subtitle = snapshot.primaryCountryName?.let { travelAlertsCoverageText(snapshot, it) } ?: "Monitoring travel signals",
            badges = listOf(travelAlertsSubtitle(snapshot) to badgeToneForAlerts(snapshot)),
        )
        snapshot.enabledKinds.forEach { kind ->
            snapshot.state(kind)?.let { state ->
                TravelAlertRow(
                    state = state,
                    modifier = Modifier.testTag("travel-alert-row-${kind.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun TravelAlertRow(
    state: TravelAlertSignalState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = state.kind.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            NomadStatusBadge(
                text = travelAlertStatusLabel(state),
                tone = badgeToneForAlertState(state),
            )
        }
        Text(
            text = travelAlertSummary(state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
        )
        Text(
            text = state.signal?.sourceName ?: state.sourceName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun DashboardNarrativeCard(
    title: String,
    subtitle: String,
    lines: List<String>,
) {
    NomadCard {
        NomadSectionClusterHeader(title = title, subtitle = subtitle)
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
            )
        }
    }
}

@Composable
private fun ForecastCheckpoint(
    day: WeatherDayForecast,
    label: String,
) {
    NomadCard(modifier = Modifier.width(132.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = day.summary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${day.minCelsius?.toInt() ?: 0}° / ${day.maxCelsius?.toInt() ?: 0}°",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
        )
    }
}

private fun statusBadgeForWeather(snapshot: WeatherSnapshot): Pair<String, NomadBadgeTone> =
    if (snapshot.currentTemperatureCelsius != null) {
        "Live" to NomadBadgeTone.Good
    } else {
        "Limited" to NomadBadgeTone.Warning
    }

private fun badgeToneForFuel(snapshot: FuelPriceSnapshot): NomadBadgeTone = when (snapshot.status) {
    FuelPriceStatus.READY -> NomadBadgeTone.Good
    FuelPriceStatus.NO_STATIONS_FOUND,
    FuelPriceStatus.CONFIGURATION_REQUIRED,
    FuelPriceStatus.UNAVAILABLE,
    FuelPriceStatus.UNSUPPORTED,
    -> NomadBadgeTone.Warning
}

private fun badgeToneForEmergency(snapshot: EmergencyCareSnapshot): NomadBadgeTone = when (snapshot.status) {
    EmergencyCareStatus.READY -> NomadBadgeTone.Good
    EmergencyCareStatus.LOADING -> NomadBadgeTone.Info
    EmergencyCareStatus.CONFIGURATION_REQUIRED,
    EmergencyCareStatus.PERMISSION_REQUIRED,
    EmergencyCareStatus.UNAVAILABLE,
    EmergencyCareStatus.ERROR,
    -> NomadBadgeTone.Warning
}

private fun badgeToneForAlerts(snapshot: TravelAlertsSnapshot): NomadBadgeTone = when {
    snapshot.highestSeverity?.rank ?: 0 >= TravelAlertSeverity.WARNING.rank -> NomadBadgeTone.Warning
    snapshot.highestSeverity != null -> NomadBadgeTone.Good
    else -> NomadBadgeTone.Info
}

private fun badgeToneForAlertState(state: TravelAlertSignalState): NomadBadgeTone = when (state.status) {
    TravelAlertSignalStatus.CHECKING,
    TravelAlertSignalStatus.STALE,
    TravelAlertSignalStatus.UNAVAILABLE,
    -> NomadBadgeTone.Info
    TravelAlertSignalStatus.READY -> when (state.signal?.severity) {
        TravelAlertSeverity.CAUTION,
        TravelAlertSeverity.WARNING,
        TravelAlertSeverity.CRITICAL,
        -> NomadBadgeTone.Warning
        TravelAlertSeverity.INFO,
        TravelAlertSeverity.CLEAR,
        null,
        -> NomadBadgeTone.Good
    }
}

private fun toneForLevel(level: SignalLevel): NomadBadgeTone = when (level) {
    SignalLevel.GOOD -> NomadBadgeTone.Good
    SignalLevel.WARNING,
    SignalLevel.BAD,
    -> NomadBadgeTone.Warning
    SignalLevel.NEUTRAL -> NomadBadgeTone.Info
}

private fun fuelPricesSubtitle(
    enabled: Boolean,
    snapshot: FuelPriceSnapshot,
): String {
    if (enabled.not()) {
        return "Off"
    }

    return when (snapshot.status) {
        FuelPriceStatus.READY,
        FuelPriceStatus.NO_STATIONS_FOUND,
        -> snapshot.countryName?.let { "$it · within ${snapshot.searchRadiusKilometers.toInt()} km" }
            ?: "Within ${snapshot.searchRadiusKilometers.toInt()} km"
        FuelPriceStatus.CONFIGURATION_REQUIRED,
        FuelPriceStatus.UNAVAILABLE,
        FuelPriceStatus.UNSUPPORTED,
        -> snapshot.sourceName
    }
}

private fun fuelPriceLines(
    enabled: Boolean,
    snapshot: FuelPriceSnapshot,
): List<String> {
    if (enabled.not()) {
        return listOf("Enable fuel prices in Settings")
    }

    val rows = when (snapshot.status) {
        FuelPriceStatus.READY -> listOfNotNull(
            snapshot.diesel?.toFuelLine(),
            snapshot.gasoline?.toFuelLine(),
            snapshot.detail.takeIf(String::isNotBlank),
            snapshot.note,
        )
        FuelPriceStatus.NO_STATIONS_FOUND,
        FuelPriceStatus.CONFIGURATION_REQUIRED,
        FuelPriceStatus.UNAVAILABLE,
        FuelPriceStatus.UNSUPPORTED,
        -> listOfNotNull(snapshot.detail.takeIf(String::isNotBlank), snapshot.note)
    }

    return rows.ifEmpty { listOf("Fuel prices unavailable") }
}

private fun FuelStationPrice.toFuelLine(): String {
    val label = when (fuelType) {
        FuelType.DIESEL -> "Diesel"
        FuelType.GASOLINE -> "Gasoline"
    }
    val price = String.format(Locale.US, "%.3f", pricePerLiter)
    val distance = String.format(Locale.US, "%.1f", distanceKilometers)
    val localityText = locality?.let { " · $it" }.orEmpty()
    return "$label: $price $currencyCode/L · $stationName · $distance km$localityText"
}

private fun dashboardLocationLabel(state: DashboardUiState): String =
    listOfNotNull(
        state.snapshot.travelContext.city,
        state.snapshot.travelContext.country,
    ).joinToString(", ").ifBlank { "Travel-ready system telemetry" }

private fun dashboardSupportLine(state: DashboardUiState): String {
    val refreshText = state.snapshot.lastRefresh?.let { "Last refresh ${it.formatDashboardTimestamp()}" }
        ?: if (state.snapshot.isRefreshing) "Refreshing dashboard..." else "Waiting for the first refresh"
    return buildString {
        append(refreshText)
        append(" · ")
        append(state.snapshot.overallSummary.detail)
    }
}

private fun travelAlertsSubtitle(snapshot: TravelAlertsSnapshot): String {
    val highestSeverity = snapshot.highestSeverity
    return when {
        highestSeverity != null && highestSeverity.rank >= TravelAlertSeverity.WARNING.rank -> highestSeverity.badgeTitle()
        snapshot.hasStaleStates -> "Stale"
        snapshot.hasUnavailableStates -> "Limited"
        highestSeverity != null -> highestSeverity.badgeTitle()
        else -> "Checking"
    }
}

private fun travelAlertsCoverageText(
    snapshot: TravelAlertsSnapshot,
    primaryCountryName: String,
): String {
    val nearbyCountries = (snapshot.coverageCountryCodes.size - 1).coerceAtLeast(0)
    return if (nearbyCountries > 0) {
        "$primaryCountryName + $nearbyCountries nearby countries"
    } else {
        primaryCountryName
    }
}

private fun travelAlertStatusLabel(state: TravelAlertSignalState): String =
    when (state.status) {
        TravelAlertSignalStatus.CHECKING -> "Checking"
        TravelAlertSignalStatus.READY -> state.signal?.severity?.badgeTitle() ?: "Ready"
        TravelAlertSignalStatus.STALE -> "Stale"
        TravelAlertSignalStatus.UNAVAILABLE -> "Unavailable"
    }

private fun travelAlertSummary(state: TravelAlertSignalState): String =
    when (state.status) {
        TravelAlertSignalStatus.CHECKING -> "Checking alerts..."
        TravelAlertSignalStatus.READY -> state.signal?.summary ?: "No current alerts."
        TravelAlertSignalStatus.STALE -> state.signal?.summary?.let { "Last known: $it" }
            ?: "Last known alert status unavailable."
        TravelAlertSignalStatus.UNAVAILABLE -> state.diagnosticSummary
            ?: state.reason?.summary()
            ?: "Source unavailable"
    }

private fun TravelAlertKind.displayName(): String = when (this) {
    TravelAlertKind.ADVISORY -> "Travel Advisory"
    TravelAlertKind.SECURITY -> "Regional Security"
}

private fun TravelAlertSeverity.badgeTitle(): String = when (this) {
    TravelAlertSeverity.CLEAR -> "Clear"
    TravelAlertSeverity.INFO -> "Info"
    TravelAlertSeverity.CAUTION -> "Caution"
    TravelAlertSeverity.WARNING -> "Warning"
    TravelAlertSeverity.CRITICAL -> "Critical"
}

private fun TravelAlertUnavailableReason.summary(): String = when (this) {
    TravelAlertUnavailableReason.COUNTRY_REQUIRED -> "Country needed for nearby alerts"
    TravelAlertUnavailableReason.LOCATION_REQUIRED -> "Location needed for local alerts"
    TravelAlertUnavailableReason.SOURCE_UNAVAILABLE -> "Source unavailable"
    TravelAlertUnavailableReason.SOURCE_CONFIGURATION_REQUIRED -> "Source setup required"
}

private fun Double?.formatDegrees(): String = this?.let { "%.0f C".format(it) } ?: "Unavailable"

private fun windDirectionLabel(directionDegrees: Double?): String {
    if (directionDegrees == null) return "Unavailable"
    val index = (((directionDegrees % 360) / 45.0).toInt()).mod(8)
    val labels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return labels[index]
}

private fun mapSearchUrl(latitude: Double, longitude: Double): String =
    "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

private fun Instant.formatDashboardTimestamp(): String =
    DateTimeFormatter.ofPattern("HH.mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

internal const val TravelAlertsCardTag = "travel-alerts-card"
