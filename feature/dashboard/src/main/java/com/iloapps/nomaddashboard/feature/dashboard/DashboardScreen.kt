package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import com.iloapps.nomaddashboard.core.model.SignalLevel
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
import kotlin.math.roundToInt

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = uiState,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
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
internal fun EmergencyCareSectionCard(
    enabled: Boolean,
    snapshot: EmergencyCareSnapshot,
    onOpenMap: () -> Unit = {},
) {
    val subtitle = when {
        enabled.not() -> "Off"
        snapshot.status == EmergencyCareStatus.CONFIGURATION_REQUIRED -> "Configuration"
        snapshot.countryName != null -> "${snapshot.countryName} · within ${snapshot.searchRadiusKilometers.toInt()} km"
        else -> snapshot.detail
    }
    NomadCard(modifier = Modifier.testTag(EmergencyCareCardTag)) {
        NomadSectionClusterHeader(
            title = "Emergency Care",
            subtitle = subtitle,
            badges = listOf(snapshot.sourceName to badgeToneForEmergency(snapshot)),
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

private fun weatherOverviewTile(snapshot: DashboardSnapshot): DashboardOverviewTileModel =
    DashboardOverviewTileModel(
        title = "Weather",
        headline = snapshot.weather.currentTemperatureCelsius?.let { "${it.roundToInt()}°" } ?: "Waiting",
        detail = listOfNotNull(
            snapshot.weather.summary.takeUnless { it.equals("Weather unavailable", ignoreCase = true) },
            travelAlertsCompactLabel(snapshot.travelAlerts),
        ).joinToString(" · ").ifBlank { "Refresh to load current conditions" },
        tone = statusBadgeForWeather(snapshot.weather).second,
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
        ?: if (snapshot.power.charging) "Charging" else "Waiting"
    val detail = when {
        snapshot.power.charging -> "Charging now"
        dischargeWatts != null -> "${dischargeWatts.roundToInt()} W drain"
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

private fun dashboardLocationLabel(state: DashboardUiState): String =
    listOfNotNull(
        state.snapshot.travelContext.city,
        state.snapshot.travelContext.country,
    ).joinToString(", ").ifBlank { "Location unavailable" }

private fun dashboardSupportLine(state: DashboardUiState): String {
    val lastRefresh = state.snapshot.lastRefresh
    val refreshText = when {
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

private fun travelAlertsCompactLabel(snapshot: TravelAlertsSnapshot): String {
    val highestSeverity = snapshot.highestSeverity
    return when {
        highestSeverity?.rank ?: 0 >= TravelAlertSeverity.WARNING.rank ->
            "Alerts ${travelAlertsSubtitle(snapshot).lowercase()}"
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
internal const val EmergencyCareCardTag = "emergency-care-card"
