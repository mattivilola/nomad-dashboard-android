package com.iloapps.nomaddashboard.feature.dashboard

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.R
import com.iloapps.nomaddashboard.core.designsystem.component.NomadActionChip
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricBlock
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.MarineForecastSlot
import com.iloapps.nomaddashboard.core.model.MarineSnapshot
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapPhase
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.model.isAutomaticallyTracked
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun DashboardRoute(
    onStartForegroundTracking: () -> Unit,
    onStopForegroundTracking: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember { mutableStateOf(context.hasDashboardLocationPermission()) }
    var hasNotificationPermission by remember { mutableStateOf(context.hasDashboardNotificationPermission()) }
    var interruptionPulseKey by remember { mutableStateOf(0) }
    val currentRefresh by rememberUpdatedState(viewModel::refresh)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = context.hasDashboardLocationPermission()
        if (hasLocationPermission) {
            currentRefresh()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = context.hasDashboardNotificationPermission()
        if (granted) {
            viewModel.startTracking()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = context.hasDashboardLocationPermission()
                hasNotificationPermission = context.hasDashboardNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                DashboardEffect.StartTrackingService -> onStartForegroundTracking()
                DashboardEffect.StopTrackingService -> onStopForegroundTracking()
                DashboardEffect.InterruptionReported -> interruptionPulseKey += 1
            }
        }
    }

    DashboardScreen(
        state = uiState,
        onRefresh = viewModel::refresh,
        hasLocationPermission = hasLocationPermission,
        hasNotificationPermission = hasNotificationPermission,
        interruptionPulseKey = interruptionPulseKey,
        onRequestLocationPermission = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        },
        onStartTracking = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasNotificationPermission.not()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.startTracking()
            }
        },
        onStopTracking = viewModel::stopTracking,
        onReportInterruption = viewModel::reportInterruption,
        onAllocateTrackedTime = viewModel::allocateTrackedTime,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    hasLocationPermission: Boolean = false,
    hasNotificationPermission: Boolean = false,
    interruptionPulseKey: Int = 0,
    onRequestLocationPermission: () -> Unit = {},
    onStartTracking: () -> Unit = {},
    onStopTracking: () -> Unit = {},
    onReportInterruption: () -> Unit = {},
    onAllocateTrackedTime: (java.util.UUID) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .testTag("dashboard_top_bar")
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NomadTopBar(
                    title = "Nomad Dashboard",
                    subtitle = dashboardLocationLabel(state),
                    supportingText = dashboardSupportLine(state),
                    titleLeading = {
                        Image(
                            painter = painterResource(id = R.drawable.nomad_symbol_mark),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                    },
                    trailing = {
                        IconButton(
                            onClick = {
                                if (state.snapshot.isRefreshing.not()) {
                                    onRefresh()
                                }
                            },
                            modifier = Modifier.testTag("dashboard_refresh_action"),
                        ) {
                            if (state.snapshot.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .testTag("dashboard_refresh_progress"),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            }
                        }
                    },
                )
            }
        }

        item {
            DashboardSummaryStrip(snapshot = state.snapshot)
        }

        if (hasLocationPermission.not()) {
            item {
                DashboardLocationAccessCard(
                    settings = state.settings,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenSettings = onOpenSettings,
                )
            }
        }

        items(state.settings.dashboardCardOrder, key = { it.name }) { cardId ->
            when (cardId) {
                DashboardCardId.WEATHER -> WeatherSectionCard(
                    snapshot = state.snapshot.weather,
                    startupLocation = state.snapshot.startupLocation,
                    surfSpot = state.settings.surfSpot,
                    marine = state.snapshot.marine,
                    forecastExpanded = state.settings.weatherForecastExpanded,
                    modifier = Modifier.testTag("dashboard_weather_card"),
                )

                DashboardCardId.TRAVEL_ALERTS -> TravelAlertsSectionCard(
                    snapshot = state.snapshot.travelAlerts,
                    startupLocation = state.snapshot.startupLocation,
                )

                DashboardCardId.CONNECTIVITY -> ConnectivitySectionCard(
                    state = state,
                )

                DashboardCardId.TRAVEL_CONTEXT -> TravelContextSectionCard(
                    state = state,
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onCopyPublicIp = {
                        state.snapshot.travelContext.publicIp?.let { publicIp ->
                            clipboardManager.setText(AnnotatedString(publicIp))
                        }
                    },
                    onOpenMap = {
                        travelContextMapTarget(state.snapshot)?.let { target ->
                            context.openMapLocation(
                                latitude = target.latitude,
                                longitude = target.longitude,
                                label = target.label,
                            )
                        }
                    },
                )

                DashboardCardId.LOCAL_INFO -> LocalInfoSectionCard(
                    enabled = state.settings.localInfoEnabled,
                    snapshot = state.snapshot.localInfo,
                    startupLocation = state.snapshot.startupLocation,
                    onOpenSettings = onOpenSettings,
                )

                DashboardCardId.FUEL_PRICES -> FuelPricesSectionCard(
                    enabled = state.settings.fuelPricesEnabled,
                    snapshot = state.snapshot.fuelPrices,
                    startupLocation = state.snapshot.startupLocation,
                    onOpenSettings = onOpenSettings,
                    onOpenMap = { station ->
                        context.openMapLocation(
                            latitude = station.latitude,
                            longitude = station.longitude,
                            label = station.stationName,
                        )
                    },
                )

                DashboardCardId.POWER -> PowerSectionCard(
                    state = state,
                )

                DashboardCardId.TIME_TRACKING -> TimeTrackingSectionCard(
                    trackingState = state.timeTracking,
                    detail = state.snapshot.timeTracking.detail,
                    hasNotificationPermission = hasNotificationPermission,
                    autoWindowLabel = "${state.settings.projectTimeTrackingAutoStartMinutes.formatClockMinutes()}-${state.settings.projectTimeTrackingAutoStopMinutes.formatClockMinutes()}",
                    interruptionPulseKey = interruptionPulseKey,
                    onStartTracking = onStartTracking,
                    onStopTracking = onStopTracking,
                    onReportInterruption = onReportInterruption,
                    onAllocateTrackedTime = onAllocateTrackedTime,
                )

                DashboardCardId.EMERGENCY_CARE -> EmergencyCareSectionCard(
                    enabled = state.settings.emergencyCareEnabled,
                    snapshot = state.snapshot.emergencyCare,
                    startupLocation = state.snapshot.startupLocation,
                    onOpenMap = {
                        state.snapshot.emergencyCare.facility?.let { facility ->
                            context.openMapLocation(
                                latitude = facility.latitude,
                                longitude = facility.longitude,
                                label = facility.name,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DashboardLocationAccessCard(
    settings: com.iloapps.nomaddashboard.core.model.AppSettings,
    onRequestLocationPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val unlockedFeatures = buildList {
        add("device-aware travel context")
        add("nearby weather")
        if (settings.visitedPlacesEnabled) add("visited place capture")
        if (settings.fuelPricesEnabled) add("nearby fuel prices")
        if (settings.emergencyCareEnabled) add("nearby hospitals")
    }

    NomadCard(modifier = Modifier.testTag("dashboard_location_access_card")) {
        NomadSectionClusterHeader(
            title = "Grant Location Access",
            subtitle = "Location unlocks the most useful on-the-road signals before you scroll into individual cards.",
            badges = listOf(
                "Recommended" to NomadBadgeTone.Accent,
                "First-run setup" to NomadBadgeTone.Info,
            ),
        )
        Text(
            text = "Enable Android location so Nomad can resolve ${unlockedFeatures.joinToString()} from your actual device position. The dashboard still falls back to public IP context when location stays off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onRequestLocationPermission,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null)
                Text(
                    text = "Grant location",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            NomadActionChip(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                onClick = onOpenSettings,
            )
        }
    }
}

private data class DashboardOverviewTileModel(
    val title: String,
    val headline: String,
    val detail: String,
    val tone: NomadBadgeTone,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardSummaryStrip(
    snapshot: DashboardSnapshot,
) {
    val tiles = listOf(
        weatherOverviewTile(snapshot),
        networkOverviewTile(snapshot),
        powerOverviewTile(snapshot),
    )
    BoxWithConstraints(modifier = Modifier.testTag("dashboard_summary_strip")) {
        val spacing = 8.dp
        val columns = if (maxWidth >= 280.dp) 3 else 2
        val tileWidth = (maxWidth - spacing * (columns - 1)) / columns
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            maxItemsInEachRow = columns,
        ) {
            tiles.forEach { tile ->
                CompactSummaryTile(tile = tile, modifier = Modifier.width(tileWidth))
            }
        }
    }
}

@Composable
private fun CompactSummaryTile(
    tile: DashboardOverviewTileModel,
    modifier: Modifier = Modifier,
) {
    NomadCard(
        modifier = modifier.heightIn(min = 118.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = tile.title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(8.dp)
                    .background(summaryToneColor(tile.tone), CircleShape),
            )
        }
        Text(
            text = tile.headline,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = tile.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeatherSectionCard(
    snapshot: WeatherSnapshot,
    startupLocation: StartupLocationBootstrapState,
    surfSpot: SurfSpotConfiguration,
    marine: MarineSnapshot?,
    forecastExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val waitingForStartupLocation = startupLocation.isChecking && snapshot.currentTemperatureCelsius == null
    val statusBadge = statusBadgeForWeather(snapshot, startupLocation)
    NomadCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "Weather",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            NomadStatusBadge(
                text = statusBadge.first,
                tone = statusBadge.second,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (waitingForStartupLocation) {
                    "Checking device location before loading location-based weather."
                } else {
                    snapshot.summary
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
            )
            Text(
                text = snapshot.sourceName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = weatherIconFor(snapshot.weatherCode),
                contentDescription = snapshot.conditionDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = if (waitingForStartupLocation) {
                    "Waiting for current conditions"
                } else {
                    snapshot.conditionDescription
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (waitingForStartupLocation.not()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WeatherMetricTile(
                    label = "Current",
                    value = snapshot.currentTemperatureCelsius.formatDegrees(),
                    modifier = Modifier.weight(1f),
                )
                WeatherMetricTile(
                    label = "Feels Like",
                    value = snapshot.apparentTemperatureCelsius.formatDegrees(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WeatherMetricTile(
                    label = "Rain Chance",
                    value = snapshot.rainChancePercent?.let { "$it%" } ?: "n/a",
                    modifier = Modifier.weight(1f),
                )
                WeatherMetricTile(
                    label = "Wind",
                    value = snapshot.windSpeedKph.formatWindSummary(snapshot.windDirectionDegrees),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (waitingForStartupLocation.not() && snapshot.hourlyForecast.isNotEmpty()) {
            Text(
                text = "Next checkpoints",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.hourlyForecast.take(3).forEach { slot ->
                    ForecastCheckpoint(
                        slot = slot,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (waitingForStartupLocation.not() && forecastExpanded && snapshot.dailyForecast.isNotEmpty()) {
            Text(
                text = "Forecast",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                snapshot.dailyForecast.take(5).forEach { day ->
                    DailyForecastRow(day = day)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.36f))

        SurfSpotBand(
            surfSpot = surfSpot,
            marine = marine,
        )
    }
}

@Composable
private fun WeatherMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        NomadMetricBlock(
            label = label,
            value = value,
            supportingText = supportingText,
        )
    }
}

@Composable
private fun SurfMetricTile(
    label: String,
    primaryValue: String,
    secondaryValue: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.66f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            )
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                maxLines = 1,
            )
            Text(
                text = secondaryValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DailyForecastRow(
    day: WeatherDayForecast,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = weatherIconFor(day.weatherCode),
            contentDescription = day.summary,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
            Text(
                text = listOfNotNull(
                    day.rainChancePercent?.let { "Rain $it%" },
                    day.windSpeedKph?.let { "${it.roundToInt()} km/h ${windDirectionLabel(day.windDirectionDegrees)}" },
                ).joinToString(" · ").ifBlank { "Daily outlook loading" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
        }
    }
}

@Composable
private fun SurfSpotBand(
    surfSpot: SurfSpotConfiguration,
    marine: MarineSnapshot?,
) {
    val isConfigured = surfSpot.latitude != null && surfSpot.longitude != null
    val hasValidCoordinates = surfSpot.latitude?.let { it in -90.0..90.0 } == true &&
        surfSpot.longitude?.let { it in -180.0..180.0 } == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Surf Spot",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = surfSpot.name.ifBlank {
                            if (isConfigured) "Configured surf spot" else "Add one break in Settings"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
                NomadStatusBadge(
                    text = when {
                        marine != null -> "${marine.sourceName} · ${marine.fetchedAt?.formatDashboardTimestamp().orEmpty()}".trimEnd()
                        isConfigured.not() -> "Not set"
                        hasValidCoordinates.not() -> "Fix spot"
                        else -> "Unavailable"
                    },
                    tone = when {
                        marine != null -> NomadBadgeTone.Good
                        isConfigured.not() -> NomadBadgeTone.Info
                        hasValidCoordinates.not() -> NomadBadgeTone.Warning
                        else -> NomadBadgeTone.Warning
                    },
                )
            }

            if (marine != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SurfMetricTile(
                        label = "Wave",
                        primaryValue = marine.wavePrimaryValue(),
                        secondaryValue = marine.waveSecondaryValue(),
                        modifier = Modifier.weight(1f),
                    )
                    SurfMetricTile(
                        label = "Swell",
                        primaryValue = marine.swellPrimaryValue(),
                        secondaryValue = marine.swellSecondaryValue(),
                        modifier = Modifier.weight(1f),
                    )
                    SurfMetricTile(
                        label = "Wind",
                        primaryValue = marine.windPrimaryValue(),
                        secondaryValue = marine.windSecondaryValue(),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (marine.forecastSlots.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        marine.forecastSlots.take(3).forEach { slot ->
                            SurfForecastCheckpoint(
                                slot = slot,
                                referenceTime = marine.fetchedAt ?: Instant.now(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                marine.seaSurfaceTemperatureCelsius?.let { seaTemp ->
                    Text(
                        text = "Sea ${formatCompactTemperature(seaTemp)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            } else {
                Text(
                    text = when {
                        isConfigured.not() -> "Add a surf spot in Settings to bring marine wave, swell, and coastal wind into this card."
                        hasValidCoordinates.not() -> "Surf spot coordinates are out of range. Fix the saved latitude and longitude in Settings."
                        else -> "Marine conditions are unavailable right now for the configured spot."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectivitySectionCard(
    state: DashboardUiState,
) {
    val connectivity = state.snapshot.connectivity
    NomadCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Connectivity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = connectivityContextLine(connectivity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            NomadStatusBadge(
                text = connectivity.internetState,
                tone = toneForLevel(state.snapshot.networkSummary.level),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            NomadMetricBlock("Down", connectivity.downloadMbps.formatThroughput())
            NomadMetricBlock("Up", connectivity.uploadMbps.formatThroughput())
            NomadMetricBlock("Latency", connectivity.latencyMs.formatMilliseconds())
            NomadMetricBlock("Jitter", connectivity.jitterMs.formatMilliseconds())
        }

        ConnectivityTrendPanels(
            throughputPanel = {
                ConnectivityTrendPanel(
                    title = "Throughput",
                    supporting = "Retained local traffic samples from recent dashboard refreshes.",
                    series = listOf(
                        ConnectivityTrendSeries(
                            label = "Down",
                            values = connectivity.downloadHistoryMbps.values(),
                            currentValue = connectivity.downloadMbps.formatThroughput(),
                            color = MaterialTheme.colorScheme.primary,
                        ),
                        ConnectivityTrendSeries(
                            label = "Up",
                            values = connectivity.uploadHistoryMbps.values(),
                            currentValue = connectivity.uploadMbps.formatThroughput(),
                            color = MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                )
            },
            latencyPanel = {
                ConnectivityTrendPanel(
                    title = "Latency",
                    supporting = connectivity.jitterMs?.let { "Jitter ${it.formatMilliseconds()} across the latest probe window." }
                        ?: "Socket probe history builds locally as the dashboard refreshes.",
                    series = listOf(
                        ConnectivityTrendSeries(
                            label = "Now",
                            values = connectivity.latencyHistoryMs.values(),
                            currentValue = connectivity.latencyMs.formatMilliseconds(),
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
private fun ConnectivityTrendPanels(
    throughputPanel: @Composable () -> Unit,
    latencyPanel: @Composable () -> Unit,
) {
    BoxWithConstraints {
        if (maxWidth < 320.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                throughputPanel()
                latencyPanel()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    throughputPanel()
                }
                Box(modifier = Modifier.weight(1f)) {
                    latencyPanel()
                }
            }
        }
    }
}

private data class ConnectivityTrendSeries(
    val label: String,
    val values: List<Double>,
    val currentValue: String,
    val color: Color,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectivityTrendPanel(
    title: String,
    supporting: String,
    series: List<ConnectivityTrendSeries>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                series.forEach { item ->
                    ConnectivityTrendLegend(series = item)
                }
            }
            ConnectivityMiniChart(series = series)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun ConnectivityTrendLegend(
    series: ConnectivityTrendSeries,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(series.color),
        )
        Text(
            text = "${series.label} ${series.currentValue}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun ConnectivityMiniChart(
    series: List<ConnectivityTrendSeries>,
    modifier: Modifier = Modifier,
) {
    val availableSeries = series.filter { it.values.isNotEmpty() }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        if (availableSeries.isEmpty()) {
            Text(
                text = "Builds as you refresh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            return@Box
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            val allValues = availableSeries.flatMap(ConnectivityTrendSeries::values)
            val maxValue = (allValues.maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it * 1.1 }
            val chartWidth = size.width
            val chartHeight = size.height
            val strokeWidth = 3.dp.toPx()
            val pointRadius = 3.5.dp.toPx()

            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                val y = chartHeight * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(x = 0f, y = y),
                    end = Offset(x = chartWidth, y = y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            availableSeries.forEach { chartSeries ->
                val points = chartSeries.values.mapIndexed { index, value ->
                    val x = if (chartSeries.values.size == 1) {
                        chartWidth / 2f
                    } else {
                        chartWidth * index / (chartSeries.values.lastIndex.toFloat())
                    }
                    val normalized = (value / maxValue).toFloat().coerceIn(0f, 1f)
                    val y = chartHeight - (normalized * chartHeight)
                    Offset(x = x, y = y)
                }

                if (points.size == 1) {
                    drawCircle(
                        color = chartSeries.color,
                        radius = pointRadius,
                        center = points.single(),
                    )
                } else {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    }
                    drawPath(
                        path = path,
                        color = chartSeries.color,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                    drawCircle(
                        color = chartSeries.color,
                        radius = pointRadius,
                        center = points.last(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravelContextSectionCard(
    state: DashboardUiState,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCopyPublicIp: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val travelContext = state.snapshot.travelContext
    val locationAlignment = travelLocationAlignment(travelContext)
    val headerBadges = buildList {
        add(
            if (state.snapshot.connectivity.vpnActive) {
                "VPN On" to NomadBadgeTone.Warning
            } else {
                "VPN Off" to NomadBadgeTone.Info
            },
        )
        locationAlignment?.let { add(it.label to it.tone) }
    }

    NomadCard {
        NomadSectionClusterHeader(
            title = "Travel Context",
            subtitle = travelContextSubtitle(state, hasLocationPermission),
            badges = headerBadges,
            actions = {
                NomadActionChip(
                    label = "Map",
                    icon = Icons.Rounded.Map,
                    onClick = onOpenMap,
                    enabled = travelContextMapTarget(state.snapshot) != null,
                )
            },
        )

        TravelLocationPanels(
            travelContext = travelContext,
            startupLocation = state.snapshot.startupLocation,
            publicIpEnabled = state.settings.publicIpGeolocationEnabled,
            hasLocationPermission = hasLocationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TravelDetailRow(
                label = "Public IP",
                value = when {
                    state.settings.publicIpGeolocationEnabled.not() -> "Off in Settings"
                    travelContext.publicIp != null -> travelContext.publicIp ?: "Unavailable"
                    else -> "Unavailable"
                },
                supporting = when {
                    travelContext.publicIp != null -> travelContext.cityCountryOrRegion()
                    state.settings.publicIpGeolocationEnabled.not() -> "Turn on external IP location to compare network identity."
                    else -> "No public IP resolved on the last refresh."
                },
                actionIcon = Icons.Rounded.ContentCopy,
                actionDescription = "Copy public IP",
                onAction = travelContext.publicIp?.let { { onCopyPublicIp() } },
            )
            TravelDetailRow(
                label = "Wi-Fi",
                value = travelWifiLabel(state.snapshot.connectivity),
                supporting = travelWifiSupportingText(state.snapshot.connectivity),
            )
            TravelDetailRow(
                label = "Signal",
                value = travelWifiSignalSummary(state.snapshot.connectivity),
                supporting = "Android exposes RSSI, band, and link speed when available.",
            )
            TravelDetailRow(
                label = "Time Zone",
                value = travelTimeZoneSummary(state.snapshot),
                supporting = "Device timezone is shown first; IP timezone appears when it differs.",
            )
            TravelDetailRow(
                label = "VPN",
                value = if (state.snapshot.connectivity.vpnActive) "Active" else "Inactive",
                supporting = if (state.snapshot.connectivity.vpnActive) {
                    "Public IP location may differ from your physical device location."
                } else {
                    "Network identity currently matches the device's direct connection."
                },
            )
        }
    }
}

@Composable
private fun TravelLocationPanels(
    travelContext: com.iloapps.nomaddashboard.core.model.TravelContextSnapshot,
    startupLocation: StartupLocationBootstrapState,
    publicIpEnabled: Boolean,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
) {
    BoxWithConstraints {
        val useTwoColumns = maxWidth >= 360.dp
        if (useTwoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TravelLocationPanel(
                        source = "Device",
                        headline = travelContext.deviceLocationLabel(),
                        status = when {
                            startupLocation.isChecking -> "Checking"
                            travelContext.hasDeviceLocation() -> "Ready"
                            hasLocationPermission.not() -> "Permission"
                            else -> "Waiting"
                        },
                        tone = when {
                            startupLocation.isChecking -> NomadBadgeTone.Info
                            travelContext.hasDeviceLocation() -> NomadBadgeTone.Accent
                            hasLocationPermission.not() -> NomadBadgeTone.Warning
                            else -> NomadBadgeTone.Info
                        },
                        supporting = when {
                            startupLocation.isChecking -> "Android is resolving current coordinates and place details."
                            travelContext.hasDeviceLocation() -> travelContext.deviceRegionCountryLine()
                            hasLocationPermission.not() -> "Allow location to compare the device's physical position."
                            else -> "Location permission is granted, but Android has not resolved a place yet."
                        },
                        actionLabel = if (hasLocationPermission.not()) "Allow" else null,
                        actionIcon = if (hasLocationPermission.not()) Icons.Rounded.MyLocation else null,
                        onAction = if (hasLocationPermission.not()) onRequestLocationPermission else null,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TravelLocationPanel(
                        source = "Public IP",
                        headline = when {
                            publicIpEnabled.not() -> "Off in Settings"
                            travelContext.hasIpLocation() -> travelContext.ipLocationLabel()
                            else -> "Unavailable"
                        },
                        status = when {
                            publicIpEnabled.not() -> "Off"
                            travelContext.hasIpLocation() -> "Live"
                            else -> "Unavailable"
                        },
                        tone = when {
                            publicIpEnabled.not() -> NomadBadgeTone.Info
                            travelContext.hasIpLocation() -> NomadBadgeTone.Good
                            else -> NomadBadgeTone.Warning
                        },
                        supporting = when {
                            publicIpEnabled.not() -> "Enable external IP location to compare network identity."
                            travelContext.publicIp != null -> travelContext.publicIp ?: "Unavailable"
                            else -> "No IP-based location was resolved on the last refresh."
                        },
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TravelLocationPanel(
                        source = "Device",
                        headline = travelContext.deviceLocationLabel(),
                        status = when {
                            startupLocation.isChecking -> "Checking"
                            travelContext.hasDeviceLocation() -> "Ready"
                            hasLocationPermission.not() -> "Permission"
                            else -> "Waiting"
                        },
                        tone = when {
                            startupLocation.isChecking -> NomadBadgeTone.Info
                            travelContext.hasDeviceLocation() -> NomadBadgeTone.Accent
                            hasLocationPermission.not() -> NomadBadgeTone.Warning
                            else -> NomadBadgeTone.Info
                        },
                        supporting = when {
                            startupLocation.isChecking -> "Android is resolving current coordinates and place details."
                            travelContext.hasDeviceLocation() -> travelContext.deviceRegionCountryLine()
                            hasLocationPermission.not() -> "Allow location to compare the device's physical position."
                            else -> "Location permission is granted, but Android has not resolved a place yet."
                    },
                    actionLabel = if (hasLocationPermission.not()) "Allow location" else null,
                    actionIcon = if (hasLocationPermission.not()) Icons.Rounded.MyLocation else null,
                    onAction = if (hasLocationPermission.not()) onRequestLocationPermission else null,
                )
                TravelLocationPanel(
                    source = "Public IP",
                    headline = when {
                        publicIpEnabled.not() -> "Off in Settings"
                        travelContext.hasIpLocation() -> travelContext.ipLocationLabel()
                        else -> "Unavailable"
                    },
                    status = when {
                        publicIpEnabled.not() -> "Off"
                        travelContext.hasIpLocation() -> "Live"
                        else -> "Unavailable"
                    },
                    tone = when {
                        publicIpEnabled.not() -> NomadBadgeTone.Info
                        travelContext.hasIpLocation() -> NomadBadgeTone.Good
                        else -> NomadBadgeTone.Warning
                    },
                    supporting = when {
                        publicIpEnabled.not() -> "Enable external IP location to compare network identity."
                        travelContext.publicIp != null -> travelContext.publicIp ?: "Unavailable"
                        else -> "No IP-based location was resolved on the last refresh."
                    },
                )
            }
        }
    }
}

@Composable
private fun TravelLocationPanel(
    source: String,
    headline: String,
    status: String,
    tone: NomadBadgeTone,
    supporting: String,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = source.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                NomadStatusBadge(text = status, tone = tone)
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            if (actionLabel != null && actionIcon != null && onAction != null) {
                NomadActionChip(
                    label = actionLabel,
                    icon = actionIcon,
                    onClick = onAction,
                )
            }
        }
    }
}

@Composable
private fun TravelDetailRow(
    label: String,
    value: String,
    supporting: String? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
        if (actionIcon != null && actionDescription != null && onAction != null) {
            IconButton(onClick = onAction) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionDescription,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PowerSectionCard(
    state: DashboardUiState,
) {
    val power = state.snapshot.power
    NomadCard(modifier = Modifier.testTag("dashboard_power_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Power",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = powerContextLine(power),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            NomadStatusBadge(
                text = power.statusLabel,
                tone = toneForLevel(state.snapshot.powerSummary.level),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            NomadMetricBlock(
                label = "Battery",
                value = power.batteryPercent?.let { "$it%" } ?: "Estimating",
                supportingText = power.voltageVolts.formatVoltage(),
            )
            NomadMetricBlock(
                label = if (power.charging) "Charge Rate" else "Drain",
                value = power.dischargeWatts.formatWatts(),
                supportingText = if (power.dischargeWatts != null) {
                    if (power.charging) "Estimated input power" else "Estimated device draw"
                } else {
                    "This device does not expose power flow"
                },
            )
            NomadMetricBlock(
                label = "Health",
                value = power.batteryHealthSummary,
                supportingText = power.temperatureCelsius.formatTemperature(),
            )
            NomadMetricBlock(
                label = "Source",
                value = power.powerSourceLabel ?: "Unavailable",
                supportingText = powerSourceSupportingLine(power),
            )
        }
        PowerHistoryPanel(
            power = power,
            signalLevel = state.snapshot.powerSummary.level,
        )
    }
}

@Composable
private fun PowerHistoryPanel(
    power: com.iloapps.nomaddashboard.core.model.PowerSnapshot,
    signalLevel: SignalLevel,
    modifier: Modifier = Modifier,
) {
    val history = power.batteryPercentHistory
    val trendColor = when {
        signalLevel == SignalLevel.WARNING || signalLevel == SignalLevel.BAD -> MaterialTheme.colorScheme.secondary
        power.charging -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Battery History",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        text = historyHeadline(power, history),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                NomadStatusBadge(
                    text = if (history.isEmpty()) "Waiting" else "${history.size} Samples",
                    tone = if (history.isEmpty()) NomadBadgeTone.Info else NomadBadgeTone.Accent,
                )
            }
            PowerHistoryChart(
                history = history,
                lineColor = trendColor,
            )
            Text(
                text = historySupportLine(power, history),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun PowerHistoryChart(
    history: List<MetricHistoryPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        if (history.isEmpty()) {
            Text(
                text = "Builds locally as the dashboard refreshes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            return@Box
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            val chartWidth = size.width
            val chartHeight = size.height
            val values = history.map(MetricHistoryPoint::value)
            val strokeWidth = 3.dp.toPx()
            val pointRadius = 3.5.dp.toPx()
            val fillBrush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.24f),
                    lineColor.copy(alpha = 0.04f),
                ),
                startY = 0f,
                endY = chartHeight,
            )

            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                val y = chartHeight * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(x = 0f, y = y),
                    end = Offset(x = chartWidth, y = y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val points = values.mapIndexed { index, value ->
                val x = if (values.size == 1) {
                    chartWidth / 2f
                } else {
                    chartWidth * index / values.lastIndex.toFloat()
                }
                val normalized = (value / 100.0).toFloat().coerceIn(0f, 1f)
                val y = chartHeight - (normalized * chartHeight)
                Offset(x = x, y = y)
            }

            if (points.size == 1) {
                drawCircle(
                    color = lineColor,
                    radius = pointRadius,
                    center = points.single(),
                )
            } else {
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                }
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(points.last().x, chartHeight)
                    lineTo(points.first().x, chartHeight)
                    close()
                }
                drawPath(path = fillPath, brush = fillBrush)
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawCircle(
                    color = lineColor,
                    radius = pointRadius,
                    center = points.last(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeTrackingSectionCard(
    trackingState: DashboardTimeTrackingUiState,
    detail: String,
    hasNotificationPermission: Boolean,
    autoWindowLabel: String,
    interruptionPulseKey: Int,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onReportInterruption: () -> Unit,
    onAllocateTrackedTime: (java.util.UUID) -> Unit,
) {
    val now = rememberDashboardTickerInstant(enabled = true)
    val bufferedDuration = dashboardBufferedDuration(trackingState, now)
    val isRunning = trackingState.activeEntry != null

    NomadCard(modifier = Modifier.testTag("dashboard_time_tracking_card")) {
        NomadSectionClusterHeader(
            title = "Time Tracking",
            subtitle = detail,
            badges = listOf(
                dashboardTrackingStatus(trackingState) to if (isRunning) NomadBadgeTone.Accent else NomadBadgeTone.Info,
                autoWindowLabel to NomadBadgeTone.Info,
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formatElapsedDuration(bufferedDuration),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        isRunning && trackingState.activeEntry?.entry?.isAutomaticallyTracked() == true ->
                            "Auto capture running"
                        isRunning -> "Manual capture running"
                        trackingState.pendingEntries.isNotEmpty() ->
                            "${trackingState.pendingEntries.size} segment${trackingState.pendingEntries.size.pluralSuffix()} waiting for allocation"
                        else -> "Ready for the next allocation"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = if (isRunning) onStopTracking else onStartTracking,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
                Text(
                    if (isRunning) "Pause capture" else "Resume capture",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            DashboardInterruptionButton(
                trackingState = trackingState,
                now = now,
                pulseKey = interruptionPulseKey,
                onClick = onReportInterruption,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            NomadMetricBlock("Interruptions", trackingState.report.interruptionsToday.toString(), "today")
            NomadMetricBlock("Focus Loss", formatDurationCompact(trackingState.report.todaysEstimatedFocusLoss), "23m each")
            NomadMetricBlock("Focused", formatDurationCompact(trackingState.report.todaysEstimatedFocusTime), "today estimate")
            NomadMetricBlock("Allocated", formatDurationCompact(trackingState.report.todaysAllocatedDuration), "today logged")
        }
        if (hasNotificationPermission.not()) {
            Text(
                text = "Android 13+ notification permission is needed for the foreground capture notification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (trackingState.projects.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                trackingState.projects.forEach { project ->
                    FilterChip(
                        selected = false,
                        onClick = { onAllocateTrackedTime(project.id) },
                        enabled = bufferedDuration.seconds > 0,
                        label = {
                            Text(
                                text = compactDashboardProjectLabel(project.name),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
        trackingState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DashboardInterruptionButton(
    trackingState: DashboardTimeTrackingUiState,
    now: Instant,
    pulseKey: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val containerColor = dashboardInterruptionButtonColor(
        lastInterruptionAt = trackingState.report.lastInterruptionAt,
        now = now,
        defaultColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f),
    )
    val contentColor = if (((containerColor.red * 0.299f) + (containerColor.green * 0.587f) + (containerColor.blue * 0.114f)) > 0.45f) {
        Color(0xFF1F2A37)
    } else {
        Color.White
    }
    var flashAlpha by remember { mutableStateOf(0f) }

    LaunchedEffect(pulseKey) {
        if (pulseKey == 0) return@LaunchedEffect
        flashAlpha = 0.9f
        delay(120)
        flashAlpha = 0.34f
        delay(260)
        flashAlpha = 0f
    }

    Box(modifier = Modifier.height(52.dp)) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(52.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PriorityHigh, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = trackingState.report.interruptionsToday.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color(0xFFFF4336).copy(alpha = flashAlpha)),
            )
        }
    }
}

@Composable
private fun rememberDashboardTickerInstant(enabled: Boolean): Instant {
    var now by remember(enabled) { mutableStateOf(Instant.now()) }

    LaunchedEffect(enabled) {
        now = Instant.now()
        while (enabled) {
            delay(1_000)
            now = Instant.now()
        }
    }

    return now
}

private fun dashboardBufferedDuration(
    trackingState: DashboardTimeTrackingUiState,
    now: Instant,
): Duration {
    val closedDuration = trackingState.pendingEntries.fold(Duration.ZERO) { total, record ->
        val endAt = record.entry.endAt ?: return@fold total
        total + Duration.between(record.entry.startAt, endAt)
    }
    val activeDuration = trackingState.activeEntry?.let { active ->
        Duration.between(active.entry.startAt, now)
    } ?: Duration.ZERO
    return closedDuration + activeDuration
}

private fun dashboardTrackingStatus(
    trackingState: DashboardTimeTrackingUiState,
): String = when {
    trackingState.activeEntry != null -> "Running"
    trackingState.pendingEntries.isNotEmpty() -> "Paused"
    else -> "Ready"
}

private fun formatElapsedDuration(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatDurationCompact(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun compactDashboardProjectLabel(name: String): String =
    if (name.length <= 8) name else "${name.take(8)}..."

private fun Int.pluralSuffix(): String = if (this == 1) "" else "s"

private fun Int.formatClockMinutes(): String = "%02d:%02d".format(this / 60, this % 60)

private fun dashboardInterruptionButtonColor(
    lastInterruptionAt: Instant?,
    now: Instant,
    defaultColor: Color,
): Color {
    val last = lastInterruptionAt ?: return defaultColor
    val elapsedMillis = Duration.between(last, now).toMillis().coerceAtLeast(0)
    val cooldownMillis = Duration.ofMinutes(23).toMillis().toFloat()
    val progress = (elapsedMillis / cooldownMillis).coerceIn(0f, 1f)
    return lerp(Color(0xFFC92A2A), defaultColor, progress)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelPricesSectionCard(
    enabled: Boolean,
    snapshot: FuelPriceSnapshot,
    startupLocation: StartupLocationBootstrapState,
    onOpenSettings: () -> Unit,
    onOpenMap: (FuelStationPrice) -> Unit,
) {
    val waitingForStartupLocation = enabled && startupLocation.isChecking && snapshot.status != FuelPriceStatus.READY
    if (enabled.not()) {
        NomadCard {
            NomadSectionClusterHeader(
                title = "Fuel Prices",
                subtitle = "Off",
                actions = {
                    NomadActionChip(
                        label = "Open Settings",
                        icon = Icons.Rounded.Settings,
                        onClick = onOpenSettings,
                    )
                },
            )
            Text(
                text = "Enable fuel prices in Settings to compare nearby diesel and gasoline options on the dashboard.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    NomadCard {
        NomadSectionClusterHeader(
            title = "Fuel Prices",
            subtitle = if (waitingForStartupLocation) {
                "Checking device location before nearby fuel lookup"
            } else {
                fuelPricesSubtitle(enabled = true, snapshot = snapshot)
            },
            badges = listOf(snapshot.sourceName to badgeToneForFuel(snapshot, startupLocation)),
        )
        when {
            waitingForStartupLocation -> Text(
                text = "Checking device location before fuel providers choose the right nearby search.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
            )
            snapshot.status == FuelPriceStatus.READY -> {
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
private fun LocalInfoSectionCard(
    enabled: Boolean,
    snapshot: LocalInfoSnapshot,
    startupLocation: StartupLocationBootstrapState,
    onOpenSettings: () -> Unit,
) {
    val waitingForStartupLocation = enabled &&
        startupLocation.isChecking &&
        snapshot.status !in setOf(LocalInfoStatus.READY, LocalInfoStatus.PARTIAL)
    val badge = localInfoBadge(snapshot, startupLocation)
    val sourceLine = "Sources: " + (
        if (enabled) snapshot.sources.map { it.name }.ifEmpty { LocalInfoCapabilitySources }
        else LocalInfoCapabilitySources
    ).joinToString(" · ")

    NomadCard(modifier = Modifier.testTag(LocalInfoCardTag)) {
        NomadSectionClusterHeader(
            title = "Local Info",
            subtitle = localInfoSubtitle(enabled = enabled, snapshot = snapshot, startupLocation = startupLocation),
            actions = {
                badge?.let {
                    NomadStatusBadge(text = it.first, tone = it.second)
                }
                if (localInfoNeedsSettingsAction(enabled = enabled, snapshot = snapshot)) {
                    NomadActionChip(
                        label = "Open Settings",
                        icon = Icons.Rounded.Settings,
                        onClick = onOpenSettings,
                    )
                }
            },
        )

        if (enabled.not()) {
            Text(
                text = "Local Info is disabled. Enable it in Settings.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = sourceLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
            return@NomadCard
        }

        if (waitingForStartupLocation) {
            Text(
                text = "Checking device location before loading local context, holidays, and price signals.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = sourceLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
            return@NomadCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            localInfoLocationValue(snapshot)?.let { locationValue ->
                LocalInfoStatusRow(
                    title = "Location",
                    value = locationValue,
                    detail = localInfoLocationDetail(snapshot),
                )
            }
            snapshot.publicHoliday?.let { holiday ->
                LocalInfoStatusRow(
                    title = "Public Holiday",
                    value = localHolidayHeadline(holiday, isSchoolHoliday = false),
                    detail = localHolidayDetail(holiday, isSchoolHoliday = false),
                    warningChip = localHolidayTravelerWarningChip(holiday, isSchoolHoliday = false),
                )
            }
            snapshot.schoolHoliday?.let { holiday ->
                LocalInfoStatusRow(
                    title = "School Break",
                    value = localHolidayHeadline(holiday, isSchoolHoliday = true),
                    detail = localHolidayDetail(holiday, isSchoolHoliday = true),
                    warningChip = localHolidayTravelerWarningChip(holiday, isSchoolHoliday = true),
                )
            }
            snapshot.localPriceLevel.rows.take(3).forEach { row ->
                LocalPriceIndicatorRowView(snapshot = row)
            }
        }

        if (
            snapshot.publicHoliday == null &&
            snapshot.schoolHoliday == null &&
            snapshot.localPriceLevel.rows.isEmpty()
        ) {
            Text(
                text = snapshot.detail ?: "Local Info is unavailable right now.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        listOfNotNull(
            snapshot.detail?.takeIf {
                snapshot.publicHoliday != null || snapshot.schoolHoliday != null || snapshot.localPriceLevel.rows.isNotEmpty()
            },
            snapshot.note,
        ).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Text(
            text = sourceLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
        )
    }
}

@Composable
private fun LocalInfoStatusRow(
    title: String,
    value: String,
    detail: String?,
    warningChip: LocalInfoTravelerWarningChip? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(start = 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                warningChip?.let { chip ->
                    NomadStatusBadge(
                        text = chip.label,
                        tone = NomadBadgeTone.Warning,
                        modifier = Modifier.testTag(chip.testTag),
                    )
                } ?: Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LocalPriceIndicatorRowView(
    snapshot: com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = snapshot.kind.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = snapshot.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            Text(
                text = snapshot.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
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
internal fun EmergencyCareSectionCard(
    enabled: Boolean,
    snapshot: EmergencyCareSnapshot,
    startupLocation: StartupLocationBootstrapState = StartupLocationBootstrapState(),
    onOpenMap: () -> Unit = {},
) {
    val waitingForStartupLocation = enabled &&
        startupLocation.isChecking &&
        snapshot.status != EmergencyCareStatus.READY
    val subtitle = when {
        enabled.not() -> "Off"
        waitingForStartupLocation -> "Checking device location before nearby search"
        snapshot.status == EmergencyCareStatus.CONFIGURATION_REQUIRED -> "Configuration"
        snapshot.countryName != null -> "${snapshot.countryName} · within ${snapshot.searchRadiusKilometers.toInt()} km"
        else -> snapshot.detail
    }
    NomadCard(modifier = Modifier.testTag(EmergencyCareCardTag)) {
        NomadSectionClusterHeader(
            title = "Emergency Care",
            subtitle = subtitle,
            badges = listOf(snapshot.sourceName to badgeToneForEmergency(snapshot, startupLocation)),
            actions = {
                val hasFacility = snapshot.facility != null
                NomadActionChip(
                    label = "Open in Maps",
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
        } else if (waitingForStartupLocation) {
            Text(
                text = "Checking device location before nearby hospitals can be searched.",
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
    startupLocation: StartupLocationBootstrapState = StartupLocationBootstrapState(),
    modifier: Modifier = Modifier,
) {
    NomadCard(modifier = modifier.testTag(TravelAlertsCardTag)) {
        NomadSectionClusterHeader(
            title = "Travel Alerts",
            subtitle = travelAlertsSubtitleLine(snapshot, startupLocation),
            actions = {
                NomadStatusBadge(
                    text = travelAlertsSubtitle(snapshot, startupLocation),
                    tone = badgeToneForAlerts(snapshot, startupLocation),
                )
            },
        )
        Text(
            text = travelAlertsContextLine(snapshot, startupLocation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
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
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
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
                text = travelAlertImpactLabel(state).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = summaryToneColor(badgeToneForAlertState(state)).copy(alpha = 0.88f),
            )
            Text(
                text = travelAlertSummary(state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
            )
            travelAlertDetail(state)?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                )
            }
            travelAlertDetailsUrl(state)?.let { url ->
                NomadActionChip(
                    label = "More details",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { uriHandler.openUri(url) },
                )
            }
            Text(
                text = travelAlertFreshnessLine(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
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
    slot: WeatherHourlyForecastSlot,
    modifier: Modifier = Modifier,
) {
    NomadCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = slot.hourOffsetLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = weatherIconFor(slot.weatherCode),
                contentDescription = slot.summary,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = slot.summary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = slot.temperatureCelsius?.let(::formatCompactTemperature) ?: "Unavailable",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = listOfNotNull(
                slot.rainChancePercent?.let { "Rain $it%" },
                slot.windSpeedKph?.let { "${it.roundToInt()} km/h ${windDirectionLabel(slot.windDirectionDegrees)}" },
            ).joinToString(" · ").ifBlank { slot.summary },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SurfForecastCheckpoint(
    slot: MarineForecastSlot,
    referenceTime: Instant,
    modifier: Modifier = Modifier,
) {
    NomadCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = slot.hourOffsetLabel(referenceTime),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = slot.waveHeightMeters?.let { "${String.format(Locale.US, "%.1f", it)} m" } ?: "n/a",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = slot.windSpeedKph?.let { "${it.roundToInt()} km/h ${windDirectionLabel(slot.windDirectionDegrees)}" } ?: "Wind n/a",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

private fun statusBadgeForWeather(
    snapshot: WeatherSnapshot,
    startupLocation: StartupLocationBootstrapState,
): Pair<String, NomadBadgeTone> =
    if (startupLocation.isChecking && snapshot.currentTemperatureCelsius == null) {
        "Checking" to NomadBadgeTone.Info
    } else if (snapshot.currentTemperatureCelsius != null) {
        "Live" to NomadBadgeTone.Good
    } else {
        "Limited" to NomadBadgeTone.Warning
    }

private fun badgeToneForFuel(
    snapshot: FuelPriceSnapshot,
    startupLocation: StartupLocationBootstrapState,
): NomadBadgeTone = when {
    startupLocation.isChecking && snapshot.status != FuelPriceStatus.READY -> NomadBadgeTone.Info
    else -> when (snapshot.status) {
        FuelPriceStatus.READY -> NomadBadgeTone.Good
        FuelPriceStatus.NO_STATIONS_FOUND,
        FuelPriceStatus.CONFIGURATION_REQUIRED,
        FuelPriceStatus.UNAVAILABLE,
        FuelPriceStatus.UNSUPPORTED,
        -> NomadBadgeTone.Warning
    }
}

private fun badgeToneForEmergency(
    snapshot: EmergencyCareSnapshot,
    startupLocation: StartupLocationBootstrapState,
): NomadBadgeTone = when {
    startupLocation.isChecking && snapshot.status != EmergencyCareStatus.READY -> NomadBadgeTone.Info
    else -> when (snapshot.status) {
        EmergencyCareStatus.READY -> NomadBadgeTone.Good
        EmergencyCareStatus.LOADING -> NomadBadgeTone.Info
        EmergencyCareStatus.CONFIGURATION_REQUIRED,
        EmergencyCareStatus.PERMISSION_REQUIRED,
        EmergencyCareStatus.UNAVAILABLE,
        EmergencyCareStatus.ERROR,
        -> NomadBadgeTone.Warning
    }
}

private fun badgeToneForAlerts(
    snapshot: TravelAlertsSnapshot,
    startupLocation: StartupLocationBootstrapState,
): NomadBadgeTone = when {
    startupLocation.isChecking && snapshot.primaryCountryCode == null -> NomadBadgeTone.Info
    snapshot.highestSeverity?.rank ?: 0 >= TravelAlertSeverity.WARNING.rank -> NomadBadgeTone.Warning
    snapshot.hasUnavailableStates -> NomadBadgeTone.Warning
    snapshot.hasStaleStates -> NomadBadgeTone.Info
    snapshot.highestSeverity != null -> NomadBadgeTone.Good
    else -> NomadBadgeTone.Info
}

private fun badgeToneForAlertState(state: TravelAlertSignalState): NomadBadgeTone = when (state.status) {
    TravelAlertSignalStatus.CHECKING,
    -> NomadBadgeTone.Info
    TravelAlertSignalStatus.STALE -> NomadBadgeTone.Info
    TravelAlertSignalStatus.UNAVAILABLE -> NomadBadgeTone.Warning
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

private fun weatherOverviewTile(snapshot: DashboardSnapshot): DashboardOverviewTileModel =
    DashboardOverviewTileModel(
        title = "Weather",
        headline = snapshot.weather.currentTemperatureCelsius?.let { "${it.roundToInt()}°" }
            ?: if (snapshot.startupLocation.isChecking) "Checking" else "Waiting",
        detail = listOfNotNull(
            snapshot.weather.conditionDescription.takeUnless { it.equals("Weather unavailable", ignoreCase = true) },
            travelAlertsCompactLabel(snapshot.travelAlerts, snapshot.startupLocation),
        ).joinToString(" · ").ifBlank { "Refresh to load current conditions" },
        tone = statusBadgeForWeather(snapshot.weather, snapshot.startupLocation).second,
    )

private fun networkOverviewTile(snapshot: DashboardSnapshot): DashboardOverviewTileModel {
    val detail = listOfNotNull(
        snapshot.connectivity.wifiName,
        snapshot.connectivity.latencyMs?.let { "${it.roundToInt()} ms" },
    ).joinToString(" · ")

    return DashboardOverviewTileModel(
        title = "Network",
        headline = snapshot.connectivity.internetState,
        detail = detail.ifBlank { "Checking connection quality" },
        tone = toneForLevel(snapshot.networkSummary.level),
    )
}

private fun powerOverviewTile(snapshot: DashboardSnapshot): DashboardOverviewTileModel {
    val dischargeWatts = snapshot.power.dischargeWatts
    val headline = snapshot.power.batteryPercent?.let { "$it%" }
        ?: snapshot.power.statusLabel.takeUnless { it.equals("Checking", ignoreCase = true) }
        ?: if (snapshot.power.charging) "Charging" else "Waiting"
    val detail = when {
        snapshot.power.charging -> listOfNotNull(
            snapshot.power.powerSourceLabel?.takeUnless { it == "Battery" }?.let { "$it power" },
            snapshot.power.batteryHealthSummary.takeUnless { it.equals("Estimating", ignoreCase = true) },
        ).joinToString(" · ").ifBlank { "Charging now" }
        dischargeWatts != null -> "${dischargeWatts.roundToInt()} W draw"
        else -> snapshot.power.batteryHealthSummary
    }

    return DashboardOverviewTileModel(
        title = "Power",
        headline = headline,
        detail = detail,
        tone = toneForLevel(snapshot.powerSummary.level),
    )
}

private fun connectivityContextLine(connectivity: com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot): String =
    listOfNotNull(
        connectivity.wifiName,
        connectivity.wifiSignalDbm?.let { "$it dBm" },
    ).joinToString(" · ").ifBlank { "Local network context unavailable" }

private fun powerContextLine(power: com.iloapps.nomaddashboard.core.model.PowerSnapshot): String =
    listOfNotNull(
        power.batteryHealthSummary.takeUnless { it.equals("Estimating", ignoreCase = true) },
        power.powerSourceLabel?.let { source ->
            if (source.equals("Battery", ignoreCase = true)) {
                "Running on battery"
            } else {
                "$source power"
            }
        },
        power.temperatureCelsius?.let { String.format(Locale.US, "%.1f°C", it) },
    ).joinToString(" · ").ifBlank { "Device battery telemetry" }

private fun powerSourceSupportingLine(power: com.iloapps.nomaddashboard.core.model.PowerSnapshot): String =
    if (power.powerSourceLabel?.equals("Battery", ignoreCase = true) == true) {
        "Running untethered"
    } else {
        power.statusLabel
    }

private fun historyHeadline(
    power: com.iloapps.nomaddashboard.core.model.PowerSnapshot,
    history: List<MetricHistoryPoint>,
): String = when {
    history.isNotEmpty() && power.batteryPercent != null -> "${power.batteryPercent}% now"
    history.isNotEmpty() -> "Recent charge trend"
    else -> "Waiting for local samples"
}

private fun historySupportLine(
    power: com.iloapps.nomaddashboard.core.model.PowerSnapshot,
    history: List<MetricHistoryPoint>,
): String {
    if (history.isEmpty()) {
        return "Android does not expose a public recent battery timeline, so the app retains local history as refreshes happen."
    }

    val values = history.map(MetricHistoryPoint::value)
    val low = values.minOrNull()?.roundToInt() ?: return "Recent battery history ready."
    val high = values.maxOrNull()?.roundToInt() ?: return "Recent battery history ready."
    val flowText = power.dischargeWatts?.let { watts ->
        if (power.charging) {
            "Estimated charge rate ${watts.formatSingleDecimal()} W."
        } else {
            "Estimated draw ${watts.formatSingleDecimal()} W."
        }
    }
    return listOf(
        "Range $low%-$high% across ${history.size} refreshes.",
        flowText,
    ).filterNotNull().joinToString(" ")
}

private fun List<MetricHistoryPoint>.values(): List<Double> = map(MetricHistoryPoint::value)

private fun Double?.formatThroughput(): String {
    val value = this ?: 0.0
    return if (value < 0.05) {
        "0 Mbps"
    } else {
        String.format(Locale.US, "%.1f Mbps", value)
    }
}

private fun Double?.formatMilliseconds(): String =
    this?.let { "${it.roundToInt()} ms" } ?: "Unavailable"

private fun Double?.formatWatts(): String =
    this?.let { String.format(Locale.US, "%.1f W", it) } ?: "Unavailable"

private fun Double?.formatVoltage(): String =
    this?.let { String.format(Locale.US, "%.2f V", it) } ?: "Voltage unavailable"

private fun Double?.formatTemperature(): String =
    this?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Temperature unavailable"

private fun Double.formatSingleDecimal(): String =
    String.format(Locale.US, "%.1f", this)

private fun dashboardLocationLabel(state: DashboardUiState): String =
    listOfNotNull(
        state.snapshot.travelContext.city,
        state.snapshot.travelContext.country,
    ).joinToString(", ").ifBlank {
        if (state.snapshot.startupLocation.isChecking) {
            "Checking device location..."
        } else {
            "Location unavailable"
        }
    }

private fun travelContextSubtitle(
    state: DashboardUiState,
    hasLocationPermission: Boolean,
): String {
    val travelContext = state.snapshot.travelContext
    val alignment = travelLocationAlignment(travelContext)
    return when {
        state.snapshot.startupLocation.isChecking -> "Checking device location before location-based cards refresh."
        alignment?.label == "Mismatch" -> "Device and network location differ."
        travelContext.hasDeviceLocation() -> "Device-aware travel context."
        hasLocationPermission.not() -> "Allow location to compare your physical position."
        state.settings.publicIpGeolocationEnabled && travelContext.hasIpLocation() -> "Public IP-based travel context."
        else -> "Network identity and environment."
    }
}

private fun dashboardSupportLine(state: DashboardUiState): String {
    val lastRefresh = state.snapshot.lastRefresh
    val refreshText = when {
        state.snapshot.startupLocation.isChecking -> "Checking device location before loading location-based cards..."
        state.snapshot.isRefreshing -> "Refreshing travel signals..."
        lastRefresh != null -> "Updated ${lastRefresh.formatDashboardTimestamp()}"
        else -> "Tap refresh to load live travel signals"
    }
    val liveSignals = listOfNotNull(
        state.snapshot.weather.currentTemperatureCelsius?.let { "${it.roundToInt()}°" },
        state.snapshot.connectivity.internetState.takeIf { it != "Checking" },
        state.snapshot.power.batteryPercent?.let { "$it% battery" },
    )
    return (listOf(refreshText) + liveSignals).joinToString(" · ")
}

private fun travelAlertsCompactLabel(
    snapshot: TravelAlertsSnapshot,
    startupLocation: StartupLocationBootstrapState,
): String {
    val highestSeverity = snapshot.highestSeverity
    return when {
        highestSeverity?.rank ?: 0 >= TravelAlertSeverity.WARNING.rank ->
            "Alerts ${travelAlertsSubtitle(snapshot, startupLocation).lowercase()}"
        highestSeverity != null -> "Alerts ${highestSeverity.badgeTitle().lowercase()}"
        snapshot.hasStaleStates -> "Alerts stale"
        snapshot.hasUnavailableStates -> "Alerts limited"
        else -> "Alerts checking"
    }
}

@Composable
private fun summaryToneColor(tone: NomadBadgeTone) = when (tone) {
    NomadBadgeTone.Good -> MaterialTheme.colorScheme.primary
    NomadBadgeTone.Warning -> MaterialTheme.colorScheme.secondary
    NomadBadgeTone.Accent -> MaterialTheme.colorScheme.tertiary
    NomadBadgeTone.Info -> MaterialTheme.colorScheme.surfaceTint
    NomadBadgeTone.Neutral -> MaterialTheme.colorScheme.outline
}

private fun travelAlertsSubtitle(
    snapshot: TravelAlertsSnapshot,
    startupLocation: StartupLocationBootstrapState,
): String {
    val highestSeverity = snapshot.highestSeverity
    return when {
        startupLocation.isChecking && snapshot.primaryCountryCode == null -> "Checking"
        highestSeverity != null && highestSeverity.rank >= TravelAlertSeverity.WARNING.rank -> highestSeverity.badgeTitle()
        snapshot.hasStaleStates -> "Stale"
        snapshot.hasUnavailableStates -> "Limited"
        highestSeverity != null -> highestSeverity.badgeTitle()
        else -> "Checking"
    }
}

private fun travelAlertsSubtitleLine(
    snapshot: TravelAlertsSnapshot,
    startupLocation: StartupLocationBootstrapState,
): String = when {
    startupLocation.isChecking && snapshot.primaryCountryCode == null ->
        "Checking device location before country-based alerts"
    snapshot.primaryCountryName != null ->
        travelAlertsCoverageText(snapshot, snapshot.primaryCountryName ?: "")
    else -> "Monitoring travel signals"
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

private fun travelAlertsContextLine(
    snapshot: TravelAlertsSnapshot,
    startupLocation: StartupLocationBootstrapState,
): String =
    if (startupLocation.isChecking && snapshot.primaryCountryCode == null) {
        "The app waits for startup location resolution before advisory and regional security checks fall back to network identity."
    } else if (snapshot.primaryCountryCode == null) {
        "Checks advisory and regional security as soon as the app can resolve your current country."
    } else {
        "Checks your current country plus bordering countries so nearby changes show up before you cross the next border."
    }

private fun travelAlertStatusLabel(state: TravelAlertSignalState): String =
    when (state.status) {
        TravelAlertSignalStatus.CHECKING -> "Checking"
        TravelAlertSignalStatus.READY -> state.signal?.severity?.badgeTitle() ?: "Ready"
        TravelAlertSignalStatus.STALE -> "Stale"
        TravelAlertSignalStatus.UNAVAILABLE -> "Unavailable"
    }

private fun travelAlertImpactLabel(state: TravelAlertSignalState): String =
    when (state.status) {
        TravelAlertSignalStatus.CHECKING -> "Live check"
        TravelAlertSignalStatus.READY -> when (state.signal?.severity) {
            TravelAlertSeverity.CLEAR -> "No elevated signal"
            TravelAlertSeverity.INFO -> "Nearby watch"
            TravelAlertSeverity.CAUTION -> "Exercise caution"
            TravelAlertSeverity.WARNING -> "Review before travel"
            TravelAlertSeverity.CRITICAL -> "Immediate attention"
            null -> "Signal ready"
        }
        TravelAlertSignalStatus.STALE -> "Last known signal"
        TravelAlertSignalStatus.UNAVAILABLE -> "Source issue"
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

private fun travelAlertDetail(state: TravelAlertSignalState): String? =
    when (state.status) {
        TravelAlertSignalStatus.READY,
        TravelAlertSignalStatus.STALE,
        -> state.signal?.detailSummary
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { detail ->
                val summary = state.signal?.summary.orEmpty()
                detail.equals(summary, ignoreCase = true)
            }
        TravelAlertSignalStatus.CHECKING,
        TravelAlertSignalStatus.UNAVAILABLE,
        -> null
    }

private fun travelAlertDetailsUrl(state: TravelAlertSignalState): String? =
    when (state.status) {
        TravelAlertSignalStatus.READY,
        TravelAlertSignalStatus.STALE,
        -> state.signal?.sourceUrl?.takeIf(String::isNotBlank)
        TravelAlertSignalStatus.CHECKING,
        TravelAlertSignalStatus.UNAVAILABLE,
        -> null
    }

private fun travelAlertFreshnessLine(state: TravelAlertSignalState): String {
    val sourceName = state.signal?.sourceName ?: state.sourceName
    val dateText = when (state.status) {
        TravelAlertSignalStatus.READY -> state.signal?.updatedAt?.formatTravelAlertDate()
            ?.let { "Updated $it" }
        TravelAlertSignalStatus.STALE -> state.lastSuccessAt?.formatTravelAlertDate()
            ?.let { "Last good refresh $it" }
        TravelAlertSignalStatus.UNAVAILABLE -> state.lastAttemptedAt?.formatTravelAlertDate()
            ?.let { "Checked $it" }
        TravelAlertSignalStatus.CHECKING -> "Refreshing now"
    }
    return listOfNotNull(sourceName, dateText).joinToString(" · ").ifBlank { sourceName }
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

private fun Double?.formatDegrees(): String = this?.let { formatCompactTemperature(it) } ?: "Unavailable"

private fun Double?.formatWindSpeed(): String = this?.let { "${it.roundToInt()} km/h" } ?: "Unavailable"

private fun Double?.formatWindSummary(directionDegrees: Double?): String {
    val speed = this?.let { "${it.roundToInt()} km/h" }
    val direction = directionDegrees?.let(::windDirectionLabel)?.takeUnless { it == "Unavailable" }
    return listOfNotNull(speed, direction).joinToString(" ").ifBlank { "Unavailable" }
}

private fun formatCompactTemperature(value: Double): String = "${value.roundToInt()} C"

private fun windDirectionLabel(directionDegrees: Double?): String {
    if (directionDegrees == null) return "Unavailable"
    val index = (((directionDegrees % 360) / 45.0).toInt()).mod(8)
    val labels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return labels[index]
}

private fun weatherIconFor(code: Int?): ImageVector = when (code) {
    0 -> Icons.Rounded.WbSunny
    1, 2, 3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.BlurOn
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Rounded.WaterDrop
    71, 73, 75 -> Icons.Rounded.AcUnit
    95, 96, 99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

private fun WeatherHourlyForecastSlot.hourOffsetLabel(): String {
    val hours = ((at.epochSecond - Instant.now().epochSecond) / 3_600.0).roundToInt().coerceAtLeast(0)
    return "+${hours}h"
}

private fun MarineForecastSlot.hourOffsetLabel(referenceTime: Instant): String {
    val hours = ((at.epochSecond - referenceTime.epochSecond) / 3_600.0).roundToInt().coerceAtLeast(0)
    return "+${hours}h"
}

private fun MarineSnapshot.wavePrimaryValue(): String =
    waveHeightMeters?.let { "${String.format(Locale.US, "%.1f", it)} m" } ?: "n/a"

private fun MarineSnapshot.waveSecondaryValue(): String =
    wavePeriodSeconds?.let { "${it.roundToInt()} s" } ?: "n/a"

private fun MarineSnapshot.swellPrimaryValue(): String =
    swellHeightMeters?.let { "${String.format(Locale.US, "%.1f", it)} m" } ?: "n/a"

private fun MarineSnapshot.swellSecondaryValue(): String =
    swellDirectionDegrees?.let(::windDirectionLabel)?.takeUnless { it == "Unavailable" } ?: "n/a"

private fun MarineSnapshot.windPrimaryValue(): String =
    windSpeedKph?.let { "${it.roundToInt()} km/h" } ?: "n/a"

private fun MarineSnapshot.windSecondaryValue(): String =
    windDirectionDegrees?.let(::windDirectionLabel)?.takeUnless { it == "Unavailable" } ?: "n/a"

private data class TravelMapTarget(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

private data class TravelLocationAlignment(
    val label: String,
    val tone: NomadBadgeTone,
)

private fun travelContextMapTarget(snapshot: DashboardSnapshot): TravelMapTarget? {
    val travelContext = snapshot.travelContext
    val latitude = travelContext.deviceLatitude ?: travelContext.latitude ?: return null
    val longitude = travelContext.deviceLongitude ?: travelContext.longitude ?: return null
    val label = travelContext.deviceLocationLabel().takeUnless { it == "Location unavailable" }
        ?: travelContext.ipLocationLabel()
    return TravelMapTarget(
        latitude = latitude,
        longitude = longitude,
        label = label,
    )
}

private fun travelLocationAlignment(
    travelContext: com.iloapps.nomaddashboard.core.model.TravelContextSnapshot,
): TravelLocationAlignment? {
    if (travelContext.hasDeviceLocation().not() || travelContext.hasIpLocation().not()) {
        return null
    }

    val sameCountry = travelContext.deviceCountryCode?.equals(travelContext.countryCode, ignoreCase = true) == true
    val sameCity = travelContext.deviceCity?.equals(travelContext.city, ignoreCase = true) == true
    return when {
        sameCountry && (sameCity || travelContext.deviceCity == null || travelContext.city == null) ->
            TravelLocationAlignment(label = "Aligned", tone = NomadBadgeTone.Good)
        sameCountry ->
            TravelLocationAlignment(label = "Nearby", tone = NomadBadgeTone.Info)
        else ->
            TravelLocationAlignment(label = "Mismatch", tone = NomadBadgeTone.Warning)
    }
}

private fun travelWifiLabel(
    connectivity: com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot,
): String = connectivity.wifiName
    ?: if (connectivity.isOnline) {
        "Not on Wi-Fi"
    } else {
        "Offline"
    }

private fun travelWifiSupportingText(
    connectivity: com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot,
): String {
    val wifiFrequencyMhz = connectivity.wifiFrequencyMhz
    return when {
        connectivity.wifiName != null && wifiFrequencyMhz != null ->
            "Band ${wifiBandLabel(wifiFrequencyMhz)}"
        connectivity.wifiName != null ->
            "Connected network identity"
        connectivity.isOnline ->
            "The active connection is online, but Android is not exposing a Wi-Fi SSID."
        else ->
            "Reconnect and refresh to capture Wi-Fi context."
    }
}

private fun travelWifiSignalSummary(
    connectivity: com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot,
): String = listOfNotNull(
    connectivity.wifiSignalDbm?.let { "RSSI $it" },
    connectivity.wifiLinkSpeedMbps?.let { "$it Mbps" },
    connectivity.wifiFrequencyMhz?.let(::wifiBandLabel),
).joinToString(" · ").ifBlank {
    if (connectivity.wifiName != null) {
        "Connected"
    } else {
        "Unavailable"
    }
}

private fun travelTimeZoneSummary(
    snapshot: DashboardSnapshot,
): String {
    val deviceZone = snapshot.connectivity.timeZoneId
    val ipZone = snapshot.travelContext.timeZoneId
    return when {
        ipZone.isNullOrBlank() || ipZone == deviceZone -> deviceZone
        else -> "Device $deviceZone · IP $ipZone"
    }
}

private fun com.iloapps.nomaddashboard.core.model.TravelContextSnapshot.hasDeviceLocation(): Boolean =
    deviceCountry != null || deviceLatitude != null || deviceLongitude != null

private fun com.iloapps.nomaddashboard.core.model.TravelContextSnapshot.hasIpLocation(): Boolean =
    country != null || latitude != null || longitude != null

private fun com.iloapps.nomaddashboard.core.model.TravelContextSnapshot.deviceLocationLabel(): String =
    listOfNotNull(deviceCity, deviceCountry).joinToString(", ").ifBlank {
        deviceCountry ?: if (deviceLatitude != null && deviceLongitude != null) {
            String.format(Locale.US, "%.3f, %.3f", deviceLatitude, deviceLongitude)
        } else {
            "Location unavailable"
        }
    }

private fun com.iloapps.nomaddashboard.core.model.TravelContextSnapshot.ipLocationLabel(): String =
    listOfNotNull(city, country).joinToString(", ").ifBlank {
        country ?: "Location unavailable"
    }

private fun com.iloapps.nomaddashboard.core.model.TravelContextSnapshot.deviceRegionCountryLine(): String =
    listOfNotNull(deviceRegion, deviceCountry).joinToString(" · ").ifBlank {
        "Resolved from Android location services."
    }

private fun com.iloapps.nomaddashboard.core.model.TravelContextSnapshot.cityCountryOrRegion(): String =
    listOfNotNull(city, region, country).joinToString(" · ").ifBlank { "Network-derived location" }

private fun wifiBandLabel(frequencyMhz: Int): String = when (frequencyMhz) {
    in 2400..2500 -> "2.4 GHz"
    in 4900..5900 -> "5 GHz"
    in 5925..7125 -> "6 GHz"
    else -> "${frequencyMhz} MHz"
}

private fun Context.openMapLocation(
    latitude: Double,
    longitude: Double,
    label: String,
) {
    val geoUri = Uri.parse(
        "geo:0,0?q=${latitude},${longitude}(${Uri.encode(label)})",
    )
    val browserUri = Uri.parse(
        "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude",
    )

    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        when {
            geoIntent.resolveActivity(packageManager) != null -> startActivity(geoIntent)
            browserIntent.resolveActivity(packageManager) != null -> startActivity(browserIntent)
        }
    } catch (_: ActivityNotFoundException) {
        // Ignore missing map handlers and leave the user on the dashboard.
    }
}

private fun Context.hasDashboardLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasDashboardNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Instant.formatDashboardTimestamp(): String =
    DateTimeFormatter.ofPattern("HH.mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

private fun Instant.formatTravelAlertDate(): String =
    DateTimeFormatter.ofPattern("dd MMM")
        .withZone(ZoneId.systemDefault())
        .format(this)

private fun localInfoSubtitle(
    enabled: Boolean,
    snapshot: LocalInfoSnapshot,
    startupLocation: StartupLocationBootstrapState,
): String {
    if (enabled.not()) {
        return "Location context, holidays, and price signals"
    }

    if (startupLocation.isChecking && snapshot.status == LocalInfoStatus.OFF) {
        return "Checking device location before local context"
    }

    val location = listOfNotNull(snapshot.locality, snapshot.region, snapshot.countryName).joinToString(" · ")
    return when (snapshot.status) {
        LocalInfoStatus.OFF -> "Off"
        LocalInfoStatus.CHECKING -> "Looking up local context and holiday calendar"
        LocalInfoStatus.LOCATION_REQUIRED -> "Location required"
        LocalInfoStatus.UNSUPPORTED,
        LocalInfoStatus.UNAVAILABLE,
        LocalInfoStatus.PARTIAL,
        LocalInfoStatus.READY,
        -> location.ifBlank { snapshot.countryName ?: "Current place" }
    }
}

private fun localInfoNeedsSettingsAction(
    enabled: Boolean,
    snapshot: LocalInfoSnapshot,
): Boolean =
    enabled.not() ||
        snapshot.status == LocalInfoStatus.LOCATION_REQUIRED ||
        snapshot.localPriceLevel.status == com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus.CONFIGURATION_REQUIRED

private fun localInfoBadge(
    snapshot: LocalInfoSnapshot,
    startupLocation: StartupLocationBootstrapState,
): Pair<String, NomadBadgeTone>? = when {
    startupLocation.isChecking &&
        snapshot.status !in setOf(LocalInfoStatus.READY, LocalInfoStatus.PARTIAL) ->
        "Checking" to NomadBadgeTone.Info
    else -> when (snapshot.status) {
        LocalInfoStatus.OFF -> "Off" to NomadBadgeTone.Info
        LocalInfoStatus.CHECKING -> "Checking" to NomadBadgeTone.Info
        LocalInfoStatus.READY -> "Ready" to NomadBadgeTone.Good
        LocalInfoStatus.PARTIAL -> "Partial" to NomadBadgeTone.Info
        LocalInfoStatus.LOCATION_REQUIRED -> "Location Needed" to NomadBadgeTone.Warning
        LocalInfoStatus.UNSUPPORTED -> "Unsupported" to NomadBadgeTone.Info
        LocalInfoStatus.UNAVAILABLE -> "Unavailable" to NomadBadgeTone.Warning
    }
}

private fun LocalPriceIndicatorKind.displayName(): String = when (this) {
    LocalPriceIndicatorKind.MEAL_OUT -> "Meal Out"
    LocalPriceIndicatorKind.GROCERIES -> "Groceries"
    LocalPriceIndicatorKind.RENT_ONE_BEDROOM -> "1BR Rent"
    LocalPriceIndicatorKind.OVERALL -> "Overall"
}

private fun LocalPricePrecision.displayName(): String = when (this) {
    LocalPricePrecision.COUNTRY_FALLBACK -> "Country fallback"
    LocalPricePrecision.COUNTY_BENCHMARK -> "County benchmark"
    LocalPricePrecision.METRO_BENCHMARK -> "Metro benchmark"
}

private fun localInfoLocationValue(snapshot: LocalInfoSnapshot): String? =
    listOfNotNull(snapshot.locality, snapshot.region, snapshot.countryName)
        .joinToString(" / ")
        .takeIf(String::isNotBlank)

private fun localInfoLocationDetail(snapshot: LocalInfoSnapshot): String? =
    listOfNotNull(
        snapshot.matchedSubdivisionName?.let { "School region $it" },
        snapshot.timezone?.let { "TZ $it" },
    ).joinToString(" · ").takeIf(String::isNotBlank)

private fun localHolidayHeadline(
    holiday: LocalHolidayStatus,
    isSchoolHoliday: Boolean,
): String = when (holiday.phase) {
    LocalHolidayPhase.TODAY -> "Today"
    LocalHolidayPhase.TOMORROW -> "Tomorrow"
    LocalHolidayPhase.NEXT -> if (isSchoolHoliday) {
        "Next break: ${holiday.period.name}"
    } else {
        "Next: ${holiday.period.name}"
    }
    LocalHolidayPhase.ON_BREAK -> "On break"
}

private fun localHolidayDetail(
    holiday: LocalHolidayStatus,
    isSchoolHoliday: Boolean,
): String =
    when (holiday.phase) {
        LocalHolidayPhase.TODAY,
        LocalHolidayPhase.TOMORROW,
        -> listOfNotNull(
            holiday.period.name,
            if (isSchoolHoliday) holiday.period.formatRange() else holiday.period.startDate.formatCompactDate(),
        ).joinToString(" · ")
        LocalHolidayPhase.NEXT -> if (isSchoolHoliday) {
            holiday.period.formatRange()
        } else {
            holiday.period.startDate.formatCompactDate()
        }
        LocalHolidayPhase.ON_BREAK -> "${holiday.period.name} · ${holiday.period.formatRange()}"
    }

private fun localHolidayTravelerWarningChip(
    holiday: LocalHolidayStatus,
    isSchoolHoliday: Boolean,
): LocalInfoTravelerWarningChip? =
    when {
        isSchoolHoliday && holiday.phase == LocalHolidayPhase.ON_BREAK ->
            LocalInfoTravelerWarningChip(
                label = "Busy Period",
                testTag = LocalInfoSchoolBreakWarningChipTag,
            )
        isSchoolHoliday.not() && holiday.phase == LocalHolidayPhase.TODAY ->
            LocalInfoTravelerWarningChip(
                label = "Busy Today",
                testTag = LocalInfoPublicHolidayWarningChipTag,
            )
        else -> null
    }

private fun com.iloapps.nomaddashboard.core.model.HolidayPeriod.formatRange(): String =
    if (startDate == endDate) {
        startDate.formatCompactDate()
    } else {
        "${startDate.formatCompactDate()}-${endDate.formatCompactDate()}"
    }

private fun java.time.LocalDate.formatCompactDate(): String =
    DateTimeFormatter.ofPattern("dd MMM").format(this)

private val LocalInfoCapabilitySources = listOf("Nager.Date", "OpenHolidays", "Eurostat", "HUD USER", "US Census Geocoder")

private data class LocalInfoTravelerWarningChip(
    val label: String,
    val testTag: String,
)

internal const val TravelAlertsCardTag = "travel-alerts-card"
internal const val EmergencyCareCardTag = "emergency-care-card"
internal const val LocalInfoCardTag = "local-info-card"
internal const val LocalInfoPublicHolidayWarningChipTag = "local-info-public-holiday-warning-chip"
internal const val LocalInfoSchoolBreakWarningChipTag = "local-info-school-break-warning-chip"
