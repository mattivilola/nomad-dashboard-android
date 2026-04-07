package com.iloapps.nomaddashboard.feature.visited

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
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
    val hasMapsApiKey = remember(context) { context.hasConfiguredMapsApiKey() }
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
        hasMapsApiKey = hasMapsApiKey,
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
    hasMapsApiKey: Boolean,
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
    val countryShapes = rememberVisitedCountryShapes()
    val countryBoundsByCode = remember(countryShapes) {
        countryShapes.orEmpty().associate { it.countryCode to it.bounds }
    }
    val mapPresentation = remember(
        state.places,
        state.countryDays,
        selectedYear,
        countryBoundsByCode,
    ) {
        buildVisitedMapPresentation(
            places = state.places,
            countryDays = state.countryDays,
            selectedYear = selectedYear,
            countryBoundsByCode = countryBoundsByCode,
        )
    }

    LazyColumn(
        modifier = Modifier.testTag("visited_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onRefresh,
                        enabled = state.settings.visitedPlacesEnabled,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Text("Capture now", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (
                        state.settings.visitedPlacesEnabled &&
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
                BoxWithConstraints {
                    val useWideLayout = maxWidth >= 920.dp
                    if (useWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            WorldFootprintCard(
                                modifier = Modifier.weight(1.5f),
                                hasMapsApiKey = hasMapsApiKey,
                                selectedYear = selectedYear,
                                mapPresentation = mapPresentation,
                                countryShapes = countryShapes,
                            )
                            CountryDaysCard(
                                modifier = Modifier.weight(1f),
                                availableYears = availableYears,
                                selectedYear = selectedYear,
                                onSelectYear = { selectedYear = it },
                                selectedYearSummary = selectedYearSummary,
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            WorldFootprintCard(
                                modifier = Modifier.fillMaxWidth(),
                                hasMapsApiKey = hasMapsApiKey,
                                selectedYear = selectedYear,
                                mapPresentation = mapPresentation,
                                countryShapes = countryShapes,
                            )
                            CountryDaysCard(
                                modifier = Modifier.fillMaxWidth(),
                                availableYears = availableYears,
                                selectedYear = selectedYear,
                                onSelectYear = { selectedYear = it },
                                selectedYearSummary = selectedYearSummary,
                            )
                        }
                    }
                }
            }

            items(monthlySummaries, key = { it.id }) { summary ->
                MonthSummaryCard(summary = summary)
            }

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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldFootprintCard(
    modifier: Modifier = Modifier,
    hasMapsApiKey: Boolean,
    selectedYear: Int,
    mapPresentation: VisitedMapPresentation,
    countryShapes: List<VisitedMapCountryShape>?,
) {
    val highlightedStyle = rememberHighlightedCountryStyle()
    val unvisitedStyle = rememberUnvisitedCountryStyle()

    NomadCard(
        modifier = modifier.testTag("visited_world_footprint"),
    ) {
        NomadSectionHeader(
            title = "World Footprint",
            subtitle = "Pins show all saved places across all time. Country shading follows the selected $selectedYear country-day view.",
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NomadPill(text = "Pins: all-time")
            NomadPill(text = "Shading: $selectedYear")
            NomadPill(
                text = when {
                    hasMapsApiKey.not() -> "Map key needed"
                    countryShapes == null -> "Boundaries loading"
                    else -> "${mapPresentation.highlightedCountryCodes.size} countries highlighted"
                },
            )
        }

        if (hasMapsApiKey.not()) {
            InlineStatusContent(
                title = "Google Maps key needed",
                body = "Set a local package-restricted NOMAD_MAPS_API_KEY in Config/AppConfig.env to render the visited map. Saved place markers and country-day summaries remain available below.",
                icon = Icons.Rounded.TravelExplore,
            )
        } else {
            VisitedWorldMap(
                countryShapes = countryShapes.orEmpty(),
                mapPresentation = mapPresentation,
                highlightedStyle = highlightedStyle,
                unvisitedStyle = unvisitedStyle,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountryDaysCard(
    modifier: Modifier = Modifier,
    availableYears: List<Int>,
    selectedYear: Int,
    onSelectYear: (Int) -> Unit,
    selectedYearSummary: com.iloapps.nomaddashboard.core.model.VisitedCountryDayYearSummary?,
) {
    NomadCard(modifier = modifier) {
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
                        modifier = Modifier.testTag("visited-year-$year"),
                        selected = year == selectedYear,
                        onClick = { onSelectYear(year) },
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

@Composable
private fun VisitedWorldMap(
    countryShapes: List<VisitedMapCountryShape>,
    mapPresentation: VisitedMapPresentation,
    highlightedStyle: VisitedMapCountryStyle,
    unvisitedStyle: VisitedMapCountryStyle,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(18.0, 0.0), 1.2f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var appliedViewportKey by rememberSaveable { mutableStateOf<String?>(null) }
    val viewportKey = remember(mapPresentation.viewport) {
        mapPresentation.viewport.bounds.viewportKey(mapPresentation.viewport.source)
    }

    LaunchedEffect(mapLoaded, viewportKey) {
        if (mapLoaded.not() || appliedViewportKey == viewportKey) {
            return@LaunchedEffect
        }

        runCatching {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngBounds(
                    mapPresentation.viewport.bounds.toLatLngBounds(),
                    96,
                ),
                durationMs = 800,
            )
        }
        appliedViewportKey = viewportKey
    }

    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp),
            ),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isBuildingEnabled = false,
            isIndoorEnabled = false,
            isMyLocationEnabled = false,
        ),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = true,
        ),
        onMapLoaded = { mapLoaded = true },
    ) {
        countryShapes.forEach { country ->
            val style = if (mapPresentation.highlightedCountryCodes.contains(country.countryCode)) {
                highlightedStyle
            } else {
                unvisitedStyle
            }

            country.polygons.forEach { polygon ->
                Polygon(
                    points = polygon.outerRing.map(VisitedMapCoordinate::toLatLng),
                    holes = polygon.holes.map { hole -> hole.map(VisitedMapCoordinate::toLatLng) },
                    fillColor = style.fillColor,
                    strokeColor = style.strokeColor,
                    strokeWidth = style.strokeWidth,
                    clickable = false,
                )
            }
        }

        mapPresentation.markers.forEach { marker ->
            Marker(
                state = MarkerState(position = marker.coordinate.toLatLng()),
                title = marker.title,
                snippet = marker.subtitle,
            )
        }
    }
}

@Composable
private fun rememberHighlightedCountryStyle(): VisitedMapCountryStyle =
    VisitedMapCountryStyle(
        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
        strokeWidth = 1.4f,
    )

@Composable
private fun rememberUnvisitedCountryStyle(): VisitedMapCountryStyle =
    VisitedMapCountryStyle(
        fillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        strokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
        strokeWidth = 0.7f,
    )

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
    icon: ImageVector,
) {
    NomadCard {
        InlineStatusContent(
            title = title,
            body = body,
            icon = icon,
        )
    }
}

@Composable
private fun InlineStatusContent(
    title: String,
    body: String,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp),
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyLarge)
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

private fun Context.hasVisitedLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasConfiguredMapsApiKey(): Boolean {
    val metaData = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
    }.getOrNull()

    val keyValue = metaData?.getString("com.google.android.geo.API_KEY").orEmpty().trim()
    return keyValue.isNotBlank() && keyValue.startsWith("\${").not()
}

private fun VisitedMapBounds.toLatLngBounds(): LatLngBounds =
    LatLngBounds(
        southWest.toLatLng(),
        northEast.toLatLng(),
    )

private fun VisitedMapCoordinate.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun VisitedMapBounds.viewportKey(source: VisitedMapViewportSource): String =
    listOf(
        source.name,
        southWest.latitude.toString(),
        southWest.longitude.toString(),
        northEast.latitude.toString(),
        northEast.longitude.toString(),
    ).joinToString("|")
