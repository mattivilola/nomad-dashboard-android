package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadPill
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSummaryTile
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
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
                    subtitle = fuelPricesSubtitle(
                        enabled = state.settings.fuelPricesEnabled,
                        snapshot = state.snapshot.fuelPrices,
                    ),
                    lines = fuelPriceLines(
                        enabled = state.settings.fuelPricesEnabled,
                        snapshot = state.snapshot.fuelPrices,
                    ),
                )
                DashboardCardId.EMERGENCY_CARE -> DashboardSectionCard(
                    title = "Emergency Care",
                    subtitle = if (state.settings.emergencyCareEnabled) "Enabled" else "Off",
                    lines = listOf(state.snapshot.emergencyCare.summary),
                )
                DashboardCardId.TRAVEL_ALERTS -> TravelAlertsSectionCard(
                    snapshot = state.snapshot.travelAlerts,
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

@Composable
internal fun TravelAlertsSectionCard(
    snapshot: TravelAlertsSnapshot,
    modifier: Modifier = Modifier,
) {
    NomadCard(modifier = modifier.testTag(TravelAlertsCardTag)) {
        NomadSectionHeader(
            title = "Travel Alerts",
            subtitle = travelAlertsSubtitle(snapshot),
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = state.kind.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = travelAlertStatusLabel(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.width(72.dp),
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

internal const val TravelAlertsCardTag = "travel-alerts-card"
