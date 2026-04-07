package com.iloapps.nomaddashboard.feature.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadPill
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.SummaryTile
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
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
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DashboardHeader(
                state = state,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
                onOpenVisited = onOpenVisited,
                onOpenTimeTracking = onOpenTimeTracking,
                onOpenAbout = onOpenAbout,
            )
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
                DashboardCardId.CONNECTIVITY -> DashboardMetricCard(
                    title = "Network Detail",
                    subtitle = state.snapshot.connectivity.internetState,
                    metrics = listOf(
                        DashboardMetric("Latency", state.snapshot.connectivity.latencyMs?.toInt()?.let { "$it ms" } ?: "n/a"),
                        DashboardMetric("Jitter", state.snapshot.connectivity.jitterMs?.toInt()?.let { "$it ms" } ?: "n/a"),
                        DashboardMetric("Wi-Fi", state.snapshot.connectivity.wifiName ?: "Unavailable"),
                        DashboardMetric("VPN", if (state.snapshot.connectivity.vpnActive) "Active" else "Inactive"),
                    ),
                    supportingLines = listOfNotNull(
                        state.snapshot.connectivity.downloadMbps?.let { "Down ${it.toInt()} Mbps" },
                        state.snapshot.connectivity.uploadMbps?.let { "Up ${it.toInt()} Mbps" },
                    ),
                )
                DashboardCardId.POWER -> DashboardMetricCard(
                    title = "Power",
                    subtitle = state.snapshot.power.batteryHealthSummary,
                    metrics = listOf(
                        DashboardMetric("Battery", state.snapshot.power.batteryPercent?.let { "$it%" } ?: "Estimating"),
                        DashboardMetric("Charging", if (state.snapshot.power.charging) "Yes" else "No"),
                        DashboardMetric("Drain", state.snapshot.power.dischargeWatts?.let { "%.1f W".format(it) } ?: "Unavailable"),
                    ),
                )
                DashboardCardId.TIME_TRACKING -> DashboardNarrativeCard(
                    title = "Time Tracking",
                    subtitle = state.snapshot.timeTracking.headline,
                    lines = listOf(state.snapshot.timeTracking.detail),
                )
                DashboardCardId.TRAVEL_CONTEXT -> DashboardMetricCard(
                    title = "Travel Context",
                    subtitle = dashboardLocationLabel(state),
                    metrics = listOf(
                        DashboardMetric("Public IP", state.snapshot.travelContext.publicIp ?: "Unavailable"),
                        DashboardMetric("Country", state.snapshot.travelContext.country ?: "Unavailable"),
                        DashboardMetric("Region", state.snapshot.travelContext.region ?: "Unavailable"),
                        DashboardMetric(
                            "Time Zone",
                            state.snapshot.travelContext.timeZoneId ?: state.snapshot.connectivity.timeZoneId,
                        ),
                    ),
                )
                DashboardCardId.FUEL_PRICES -> FuelPricesSectionCard(
                    enabled = state.settings.fuelPricesEnabled,
                    snapshot = state.snapshot.fuelPrices,
                )
                DashboardCardId.EMERGENCY_CARE -> EmergencyCareSectionCard(
                    enabled = state.settings.emergencyCareEnabled,
                    snapshot = state.snapshot.emergencyCare,
                )
                DashboardCardId.TRAVEL_ALERTS -> TravelAlertsSectionCard(
                    snapshot = state.snapshot.travelAlerts,
                )
                DashboardCardId.WEATHER -> WeatherSectionCard(
                    snapshot = state.snapshot.weather,
                    forecastExpanded = state.settings.weatherForecastExpanded,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardHeader(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVisited: () -> Unit,
    onOpenTimeTracking: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nomad Dashboard",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NomadPill(
                        text = state.snapshot.overallSummary.headline,
                        tint = levelTint(state.snapshot.overallSummary.level),
                    )
                    Text(
                        text = dashboardLocationLabel(state),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = dashboardHeaderSupportLine(state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DashboardQuickAction(
                label = "Visited",
                icon = Icons.Rounded.Map,
                onClick = onOpenVisited,
            )
            DashboardQuickAction(
                label = "Tracking",
                icon = Icons.Rounded.Timer,
                onClick = onOpenTimeTracking,
            )
            DashboardQuickAction(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                onClick = onOpenSettings,
            )
            DashboardQuickAction(
                label = "About",
                icon = Icons.Rounded.Info,
                onClick = onOpenAbout,
            )
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
    BoxWithConstraints {
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
    NomadCard(modifier = modifier.heightIn(min = 132.dp)) {
        Text(
            text = tile.title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(levelDotColor(tile.level))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = tile.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
        Text(
            text = tile.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun DashboardQuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(icon, contentDescription = null)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardMetricCard(
    title: String,
    subtitle: String,
    metrics: List<DashboardMetric>,
    supportingLines: List<String> = emptyList(),
) {
    NomadCard {
        NomadSectionHeader(title = title, subtitle = subtitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            metrics.forEach { metric ->
                DashboardMetricCell(metric = metric, modifier = Modifier.fillMaxWidth(0.48f))
            }
        }
        supportingLines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun DashboardMetricCell(
    metric: DashboardMetric,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = metric.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = metric.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeatherSectionCard(
    snapshot: WeatherSnapshot,
    forecastExpanded: Boolean,
) {
    NomadCard {
        NomadSectionHeader(
            title = "Weather",
            subtitle = snapshot.summary,
            trailing = {
                NomadPill(text = snapshot.sourceName, tint = MaterialTheme.colorScheme.surfaceVariant)
            },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            maxItemsInEachRow = 3,
        ) {
            WeatherMetric("Current", snapshot.currentTemperatureCelsius?.let { "%.0f C".format(it) } ?: "Unavailable")
            WeatherMetric("Feels Like", snapshot.apparentTemperatureCelsius?.let { "%.0f C".format(it) } ?: "Unavailable")
            WeatherMetric("Wind", snapshot.windSpeedKph?.let { "%.0f km/h".format(it) } ?: "Unavailable")
            WeatherMetric("Rain Chance", snapshot.rainChancePercent?.let { "$it%" } ?: "n/a")
        }
        if (forecastExpanded && snapshot.dailyForecast.isNotEmpty()) {
            Text(
                text = "Forecast",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            snapshot.dailyForecast.take(3).forEach { day ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
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
}

@Composable
private fun WeatherMetric(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
        )
    }
}

@Composable
private fun FuelPricesSectionCard(
    enabled: Boolean,
    snapshot: FuelPriceSnapshot,
) {
    if (enabled.not()) {
        DashboardNarrativeCard(
            title = "Fuel Prices",
            subtitle = "Off",
            lines = listOf("Enable fuel prices in Settings to compare nearby diesel and gasoline stations."),
        )
        return
    }

    NomadCard {
        NomadSectionHeader(
            title = "Fuel Prices",
            subtitle = fuelPricesSubtitle(enabled = true, snapshot = snapshot),
        )
        when (snapshot.status) {
            FuelPriceStatus.READY -> {
                snapshot.diesel?.let { FuelPriceRow(price = it) }
                snapshot.gasoline?.let { FuelPriceRow(price = it) }
                listOfNotNull(snapshot.detail.takeIf(String::isNotBlank), snapshot.note).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            }
            FuelPriceStatus.NO_STATIONS_FOUND,
            FuelPriceStatus.CONFIGURATION_REQUIRED,
            FuelPriceStatus.UNAVAILABLE,
            FuelPriceStatus.UNSUPPORTED,
            -> fuelPriceLines(enabled = true, snapshot = snapshot).forEach { line ->
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
                text = buildString {
                    append(price.stationName)
                    price.locality?.let {
                        append(" · ")
                        append(it)
                    }
                    append(" · ")
                    append(String.format(Locale.US, "%.1f km", price.distanceKilometers))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Text(
            text = String.format(Locale.US, "%.3f %s/L", price.pricePerLiter, price.currencyCode),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun EmergencyCareSectionCard(
    enabled: Boolean,
    snapshot: EmergencyCareSnapshot,
) {
    if (enabled.not()) {
        DashboardNarrativeCard(
            title = "Emergency Care",
            subtitle = "Off",
            lines = listOf("Enable emergency care in Settings to search nearby hospitals."),
        )
        return
    }

    val context = LocalContext.current
    NomadCard(modifier = Modifier.testTag(EmergencyCareCardTag)) {
        NomadSectionHeader(
            title = "Emergency Care",
            subtitle = emergencyCareSubtitle(snapshot),
            trailing = {
                snapshot.countryName?.let { countryName ->
                    NomadPill(text = countryName, tint = MaterialTheme.colorScheme.surfaceVariant)
                }
            },
        )
        when (snapshot.status) {
            EmergencyCareStatus.READY -> {
                snapshot.facility?.let { facility ->
                    Text(
                        text = facility.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = emergencyCareFacilityLine(facility),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                    )
                }
                listOfNotNull(snapshot.detail.takeIf(String::isNotBlank), snapshot.note).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
                snapshot.facility?.let { facility ->
                    FilledTonalButton(
                        onClick = { openEmergencyFacilityInMaps(context, facility) },
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = null)
                        Text(text = "Open in Maps", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            EmergencyCareStatus.LOADING,
            EmergencyCareStatus.CONFIGURATION_REQUIRED,
            EmergencyCareStatus.PERMISSION_REQUIRED,
            EmergencyCareStatus.UNAVAILABLE,
            EmergencyCareStatus.ERROR,
            -> listOfNotNull(snapshot.detail.takeIf(String::isNotBlank), snapshot.note).forEach { line ->
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
private fun DashboardNarrativeCard(
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
            )
        }
    }
}

@Composable
internal fun TravelAlertsSectionCard(
    snapshot: TravelAlertsSnapshot,
    modifier: Modifier = Modifier,
) {
    NomadCard(modifier = modifier.testTag(TravelAlertsCardTag)) {
        NomadSectionHeader(
            title = "Travel Alerts",
            subtitle = travelAlertsSubtitle(snapshot),
            trailing = {
                snapshot.primaryCountryName?.let {
                    NomadPill(text = it, tint = MaterialTheme.colorScheme.surfaceVariant)
                }
            },
        )
        snapshot.primaryCountryName?.let { countryName ->
            Text(
                text = travelAlertsCoverageText(snapshot, countryName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.kind.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            NomadPill(
                text = travelAlertStatusLabel(state),
                tint = travelAlertTint(state),
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

private fun dashboardHeaderSupportLine(state: DashboardUiState): String {
    val contextBits = listOfNotNull(
        state.snapshot.travelContext.region,
        state.snapshot.travelContext.timeZoneId ?: state.snapshot.connectivity.timeZoneId,
    )
    return buildString {
        append(state.snapshot.overallSummary.detail)
        if (contextBits.isNotEmpty()) {
            append(" · ")
            append(contextBits.joinToString(" · "))
        }
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
        "Monitoring: $primaryCountryName + $nearbyCountries nearby countries"
    } else {
        "Monitoring: $primaryCountryName"
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

private fun emergencyCareSubtitle(snapshot: EmergencyCareSnapshot): String =
    when (snapshot.status) {
        EmergencyCareStatus.LOADING -> "Loading"
        EmergencyCareStatus.READY -> "Ready"
        EmergencyCareStatus.CONFIGURATION_REQUIRED -> "Configuration"
        EmergencyCareStatus.PERMISSION_REQUIRED -> "Permission"
        EmergencyCareStatus.UNAVAILABLE -> "Unavailable"
        EmergencyCareStatus.ERROR -> "Error"
    }

private fun emergencyCareFacilityLine(facility: EmergencyCareFacility): String =
    listOfNotNull(
        facility.address,
        String.format(Locale.US, "%.1f km away", facility.distanceKilometers),
    ).joinToString(" · ")

private fun openEmergencyFacilityInMaps(
    context: android.content.Context,
    facility: EmergencyCareFacility,
) {
    val uri = if (facility.placeId.isNullOrBlank()) {
        Uri.parse(
            "geo:${facility.latitude},${facility.longitude}?q=${
                Uri.encode("${facility.latitude},${facility.longitude} (${facility.name})")
            }",
        )
    } else {
        Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=${
                Uri.encode(facility.name)
            }&query_place_id=${
                Uri.encode(facility.placeId)
            }",
        )
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
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

@Composable
private fun levelTint(level: SignalLevel): Color =
    when (level) {
        SignalLevel.GOOD -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        SignalLevel.WARNING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        SignalLevel.BAD -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
        SignalLevel.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }

@Composable
private fun levelDotColor(level: SignalLevel): Color =
    when (level) {
        SignalLevel.GOOD -> MaterialTheme.colorScheme.primary
        SignalLevel.WARNING -> MaterialTheme.colorScheme.secondary
        SignalLevel.BAD -> MaterialTheme.colorScheme.secondary
        SignalLevel.NEUTRAL -> MaterialTheme.colorScheme.outline
    }

@Composable
private fun travelAlertTint(state: TravelAlertSignalState): Color =
    when (state.status) {
        TravelAlertSignalStatus.CHECKING,
        TravelAlertSignalStatus.STALE,
        TravelAlertSignalStatus.UNAVAILABLE,
        -> MaterialTheme.colorScheme.surfaceVariant
        TravelAlertSignalStatus.READY -> when (state.signal?.severity) {
            TravelAlertSeverity.WARNING,
            TravelAlertSeverity.CRITICAL,
            TravelAlertSeverity.CAUTION,
            -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
            TravelAlertSeverity.INFO,
            TravelAlertSeverity.CLEAR,
            null,
            -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        }
    }

private data class DashboardMetric(
    val label: String,
    val value: String,
)

internal const val TravelAlertsCardTag = "travel-alerts-card"
internal const val EmergencyCareCardTag = "emergency-care-card"
