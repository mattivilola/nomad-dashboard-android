package com.iloapps.nomaddashboard.feature.visited

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadPill
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedCountryDayMonthSummary
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.model.availableYears
import com.iloapps.nomaddashboard.core.model.monthlySummaries
import com.iloapps.nomaddashboard.core.model.visitedPlaceSummary
import com.iloapps.nomaddashboard.core.model.yearSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun VisitedRoute(
    viewModel: VisitedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember { mutableStateOf(context.hasVisitedLocationPermission()) }
    val currentRefresh by rememberUpdatedState(viewModel::refresh)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = context.hasVisitedLocationPermission()
        if (hasLocationPermission) {
            currentRefresh()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = context.hasVisitedLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.settings.useCurrentLocationForVisitedPlaces) {
        hasLocationPermission = context.hasVisitedLocationPermission()
    }

    VisitedScreen(
        state = state,
        hasLocationPermission = hasLocationPermission,
        onRefresh = viewModel::refresh,
        onRequestLocationPermission = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisitedScreen(
    state: VisitedUiState,
    hasLocationPermission: Boolean,
    onRefresh: () -> Unit,
    onRequestLocationPermission: () -> Unit,
) {
    val placeSummary = state.places.visitedPlaceSummary()
    val availableYears = state.countryDays.availableYears()
    val currentYear = LocalDate.now().year
    var selectedYear by rememberSaveable { mutableIntStateOf(currentYear) }

    LaunchedEffect(availableYears) {
        selectedYear = when {
            availableYears.contains(selectedYear) -> selectedYear
            availableYears.contains(currentYear) -> currentYear
            availableYears.isNotEmpty() -> availableYears.first()
            else -> currentYear
        }
    }

    val selectedYearSummary = state.countryDays.yearSummary(selectedYear)
    val monthlySummaries = state.countryDays.monthlySummaries(selectedYear)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            NomadCard {
                NomadSectionHeader(
                    title = "Visited Places",
                    subtitle = headerSubtitle(state.settings, placeSummary, state.countryDays),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NomadPill(text = trackingModeLabel(state.settings))
                    NomadPill(text = "Tracked days: ${state.countryDays.size}")
                }
            }
        }

        item {
            NomadCard {
                NomadSectionHeader(
                    title = "How Capture Works",
                    subtitle = "Saved locally on this device only.",
                )
                Text(
                    text = "The app records travel history during refresh. Device location replaces same-day IP captures, the first resolved country wins for a day, and missing in-between days are inferred by splitting gaps between the surrounding countries.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            NomadCard {
                NomadSectionHeader(
                    title = "Capture Status",
                    subtitle = captureSubtitle(state.settings, hasLocationPermission),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.settings.publicIpGeolocationEnabled.takeIf { it }?.let {
                        NomadPill(text = "IP enabled")
                    }
                    state.settings.useCurrentLocationForVisitedPlaces.takeIf { it }?.let {
                        NomadPill(text = if (hasLocationPermission) "Device ready" else "Device permission needed")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onRefresh,
                        enabled = state.settings.visitedPlacesEnabled,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Text("Capture now", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (state.settings.visitedPlacesEnabled &&
                        state.settings.useCurrentLocationForVisitedPlaces &&
                        hasLocationPermission.not()
                    ) {
                        Button(onClick = onRequestLocationPermission) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null)
                            Text("Allow location", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        if (state.settings.visitedPlacesEnabled.not()) {
            item {
                StatusCard(
                    title = "Visited history is off",
                    body = "Enable visited places in Settings to resume local place and country-day capture. Existing saved history remains local to the device.",
                    icon = Icons.Rounded.TravelExplore,
                )
            }
        } else if (state.places.isEmpty() && state.countryDays.isEmpty()) {
            item {
                StatusCard(
                    title = "No saved travel history yet",
                    body = if (state.settings.publicIpGeolocationEnabled || state.settings.useCurrentLocationForVisitedPlaces) {
                        "Refresh once you have network or device location available, and saved places plus country days will begin appearing here."
                    } else {
                        "Enable IP geolocation or visited-place device capture so refresh has a location source to save."
                    },
                    icon = Icons.Rounded.TravelExplore,
                )
            }
        } else {
            item {
                NomadCard {
                    NomadSectionHeader(
                        title = "Saved Places",
                        subtitle = "${state.places.size} saved entries ordered by most recent visit.",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.places.forEach { place ->
                            SavedPlaceRow(place = place)
                        }
                    }
                }
            }

            item {
                NomadCard {
                    NomadSectionHeader(
                        title = "Country Days",
                        subtitle = selectedYearSummary?.let {
                            "In $selectedYear you tracked ${it.totalTrackedDays} day${if (it.totalTrackedDays == 1) "" else "s"}."
                        } ?: "Yearly country totals will appear here as daily travel history is captured.",
                    )

                    if (availableYears.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            availableYears.forEach { year ->
                                FilterChip(
                                    selected = year == selectedYear,
                                    onClick = { selectedYear = year },
                                    label = { Text(year.toString()) },
                                )
                            }
                        }
                    }

                    selectedYearSummary?.items?.forEach { item ->
                        Text(
                            text = "${item.country}: ${item.dayCount} day${if (item.dayCount == 1) "" else "s"} · ${item.percentage.asPercent()}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }

            items(monthlySummaries, key = { it.id }) { summary ->
                MonthSummaryCard(summary = summary)
            }
        }
    }
}

@Composable
private fun SavedPlaceRow(place: VisitedPlace) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = place.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = buildString {
                append(place.region?.takeIf(String::isNotBlank) ?: place.country)
                append(" · Sources: ")
                append(place.sources.joinToString(" + ") { it.label() })
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
        )
        Text(
            text = "First: ${place.firstVisitedAt.formatTimestamp()} · Last: ${place.lastVisitedAt.formatTimestamp()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun MonthSummaryCard(summary: VisitedCountryDayMonthSummary) {
    NomadCard {
        NomadSectionHeader(
            title = monthLabel(summary.month),
            subtitle = "${summary.totalTrackedDays} tracked day${if (summary.totalTrackedDays == 1) "" else "s"}",
        )
        summary.items.forEach { item ->
            Text(
                text = "${item.country}: ${item.dayCount} day${if (item.dayCount == 1) "" else "s"} · ${item.percentage.asPercent()}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            summary.days.forEach { day ->
                Text(
                    text = "${day.date.dayOfMonth.toString().padStart(2, '0')} · ${day.country} · ${if (day.isInferred) "Inferred" else day.source.label()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    NomadCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun headerSubtitle(
    settings: AppSettings,
    summary: com.iloapps.nomaddashboard.core.model.VisitedPlaceSummary,
    countryDays: List<VisitedCountryDay>,
): String = when {
    settings.visitedPlacesEnabled.not() -> "Local place history is currently disabled."
    summary.citiesVisited == 0 && countryDays.isEmpty() -> "Your saved travel footprint will appear here."
    countryDays.isNotEmpty() -> "${summary.citiesVisited} saved cities across ${summary.countriesVisited} countries, plus ${countryDays.size} tracked country days."
    else -> "${summary.citiesVisited} saved cities across ${summary.countriesVisited} countries."
}

private fun trackingModeLabel(settings: AppSettings): String =
    listOfNotNull(
        "IP".takeIf { settings.publicIpGeolocationEnabled },
        "Device".takeIf { settings.useCurrentLocationForVisitedPlaces },
    ).joinToString(" + ").ifBlank { "No capture source enabled" }

private fun captureSubtitle(
    settings: AppSettings,
    hasLocationPermission: Boolean,
): String = when {
    settings.visitedPlacesEnabled.not() -> "Visited history capture is disabled."
    settings.useCurrentLocationForVisitedPlaces && hasLocationPermission.not() -> "Device capture is enabled but Android location permission is still needed."
    settings.useCurrentLocationForVisitedPlaces -> "Refresh will record both IP and device-derived travel observations when available."
    settings.publicIpGeolocationEnabled -> "Refresh currently records IP-based travel observations."
    else -> "Enable an IP or device location source so refresh has travel context to save."
}

private fun VisitedPlaceSource.label(): String = when (this) {
    VisitedPlaceSource.DEVICE_LOCATION -> "Device"
    VisitedPlaceSource.PUBLIC_IP_GEOLOCATION -> "IP"
}

private fun Double.asPercent(): String = "${(this * 100).toInt()}%"

private fun java.time.Instant.formatTimestamp(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
        .format(this)

private fun monthLabel(month: Int): String =
    java.time.Month.of(month).name.lowercase().replaceFirstChar(Char::titlecase)

private fun android.content.Context.hasVisitedLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
