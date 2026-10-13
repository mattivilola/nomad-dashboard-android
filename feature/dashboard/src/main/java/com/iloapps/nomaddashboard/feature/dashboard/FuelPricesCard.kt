package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricValue
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import com.iloapps.nomaddashboard.core.travel.cardcontent.FuelPricePresentation
import com.iloapps.nomaddashboard.core.travel.cardcontent.FuelPriceRow
import java.time.ZoneId

internal const val FuelPricesCardTag = "fuel-prices-card"

internal fun fuelRowTag(fuelType: FuelType): String = "fuel-row-${fuelType.name}"

internal fun fuelRowMenuTag(fuelType: FuelType): String = "fuel-row-menu-${fuelType.name}"

/**
 * The Fuel Prices card: the cheapest diesel and gasoline station nearby. A row opens an in-app map
 * preview of the station; Google Maps sits behind the row's ⋯ menu. Prices are neutral text, never
 * coloured by fuel type, with the "/L" unit drawn small.
 *
 * @param onOpenMap opens a station in Google Maps.
 * @param mapPreview what a tap on a row does; the default previews in-app when the build has a Maps key.
 */
@Composable
internal fun FuelPricesSectionCard(
    enabled: Boolean,
    snapshot: FuelPriceSnapshot,
    startupLocation: StartupLocationBootstrapState,
    onOpenSettings: () -> Unit,
    onOpenMap: (MapTarget) -> Unit,
    frame: DashboardCardFrame = DashboardCardFrame(),
    mapPreview: MapPreviewController = rememberMapPreviewController(onOpenMap),
) {
    val waitingForStartupLocation = enabled && startupLocation.isChecking && snapshot.status != FuelPriceStatus.READY
    if (enabled.not()) {
        DashboardCard(
            card = DashboardCardId.FUEL_PRICES,
            frame = frame,
            modifier = Modifier.testTag(FuelPricesCardTag),
            subtitle = "Off",
            actions = {
                NomadInlineAction(
                    label = "Open Settings",
                    icon = Icons.Rounded.Settings,
                    onClick = onOpenSettings,
                )
            },
        ) {
            Text(
                text = "Enable fuel prices in Settings to compare nearby diesel and gasoline options on the dashboard.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val statusBadge = snapshot.sourceName to badgeToneForFuel(snapshot, startupLocation)
    val time = rememberCardTime(ZoneId.systemDefault())
    val rows = remember(snapshot, time) { FuelPricePresentation.rows(snapshot, time) }

    DashboardCard(
        card = DashboardCardId.FUEL_PRICES,
        frame = frame,
        modifier = Modifier.testTag(FuelPricesCardTag),
        subtitle = if (waitingForStartupLocation) {
            "Checking device location before nearby fuel lookup"
        } else {
            fuelPricesSubtitle(snapshot = snapshot)
        },
        badges = listOf(statusBadge),
        collapsedBadge = statusBadge,
        actions = {
            if (snapshot.isRefreshing) {
                SectionRefreshIndicator(tag = "dashboard_fuel_refresh_indicator")
            }
        },
    ) {
        when {
            waitingForStartupLocation -> Text(
                text = "Checking device location before fuel providers choose the right nearby search.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
            )

            snapshot.status == FuelPriceStatus.READY -> {
                rows.forEach { row -> FuelPriceRowView(row = row, mapPreview = mapPreview) }
                listOfNotNull(snapshot.detail.takeIf(String::isNotBlank), snapshot.note).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            }

            else -> fuelPriceMessages(snapshot).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                )
            }
        }
        MapPreviewHost(mapPreview)
    }
}

@Composable
private fun FuelPriceRowView(
    row: FuelPriceRow,
    mapPreview: MapPreviewController,
) {
    val target = MapTarget(title = "${row.title} · ${row.stationName}", latitude = row.latitude, longitude = row.longitude)
    MapActionRow(
        description = row.accessibilityLabel,
        canPreview = mapPreview.canPreview(target),
        onClick = { mapPreview.preview(target) },
        onShowPreview = { mapPreview.preview(target) },
        onOpenGoogleMaps = { mapPreview.openExternal(target) },
        modifier = Modifier.testTag(fuelRowTag(row.fuelType)),
        menuTag = fuelRowMenuTag(row.fuelType),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            Text(
                text = row.stationName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (row.detail.isNotBlank()) {
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NomadMetricValue(
                number = row.price,
                unit = row.priceUnit,
                style = MaterialTheme.typography.titleLarge,
            )
            row.updated?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
        }
    }
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
        -> NomadBadgeTone.Caution
    }
}

private fun fuelPricesSubtitle(snapshot: FuelPriceSnapshot): String =
    when (snapshot.status) {
        FuelPriceStatus.READY,
        FuelPriceStatus.NO_STATIONS_FOUND,
        -> snapshot.countryName?.let { "$it · within ${snapshot.searchRadiusKilometers.toInt()} km" }
            ?: "Within ${snapshot.searchRadiusKilometers.toInt()} km"
        FuelPriceStatus.CONFIGURATION_REQUIRED,
        FuelPriceStatus.UNAVAILABLE,
        FuelPriceStatus.UNSUPPORTED,
        -> snapshot.sourceName
    }

/** What the card says while there are no prices to list. */
private fun fuelPriceMessages(snapshot: FuelPriceSnapshot): List<String> =
    listOfNotNull(snapshot.detail.takeIf(String::isNotBlank), snapshot.note)
        .ifEmpty { listOf("Fuel prices unavailable") }
