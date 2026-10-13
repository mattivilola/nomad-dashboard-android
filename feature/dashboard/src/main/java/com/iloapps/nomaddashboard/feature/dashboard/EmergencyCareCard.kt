package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricValue
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import com.iloapps.nomaddashboard.core.travel.cardcontent.EmergencyCarePresentation
import com.iloapps.nomaddashboard.core.travel.cardcontent.EmergencyHospitalRow
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumbers

internal fun hospitalRowTag(id: String): String = "emergency-hospital-$id"

internal fun hospitalRowMenuTag(id: String): String = "emergency-hospital-menu-$id"

/**
 * The Emergency Care card: the hospitals nearby, nearest first, under a subtitle that gives the search
 * radius and the offline emergency number of the current country ("Within 25 km · Emergency 112").
 * Public and Private are neutral chips. A row opens an in-app map preview; Google Maps sits behind the
 * row's ⋯ menu.
 *
 * @param emergencyNumbers the numbers of the current country, from the bundled directory; null when the
 * country is not listed.
 * @param onOpenMap opens a hospital in Google Maps.
 * @param mapPreview what a tap on a row does; the default previews in-app when the build has a Maps key.
 */
@Composable
internal fun EmergencyCareSectionCard(
    enabled: Boolean,
    snapshot: EmergencyCareSnapshot,
    startupLocation: StartupLocationBootstrapState = StartupLocationBootstrapState(),
    emergencyNumbers: EmergencyNumbers? = null,
    onOpenMap: (MapTarget) -> Unit = {},
    frame: DashboardCardFrame = DashboardCardFrame(),
    mapPreview: MapPreviewController = rememberMapPreviewController(onOpenMap),
) {
    val waitingForStartupLocation = enabled &&
        startupLocation.isChecking &&
        snapshot.status != EmergencyCareStatus.READY
    val statusBadge = snapshot.sourceName to badgeToneForEmergency(snapshot, startupLocation)
    val hospitals = remember(snapshot) { EmergencyCarePresentation.hospitals(snapshot) }

    DashboardCard(
        card = DashboardCardId.EMERGENCY_CARE,
        frame = frame,
        modifier = Modifier.testTag(EmergencyCareCardTag),
        subtitle = EmergencyCarePresentation.subtitle(enabled, waitingForStartupLocation, snapshot, emergencyNumbers),
        badges = listOf(statusBadge),
        collapsedBadge = statusBadge.takeIf { enabled },
        actions = {
            if (snapshot.isRefreshing) {
                SectionRefreshIndicator(tag = "dashboard_emergency_refresh_indicator")
            }
        },
    ) {
        when {
            enabled.not() -> Text(
                text = "Enable emergency care in Settings to keep nearby hospitals visible from the main dashboard.",
                style = MaterialTheme.typography.bodyLarge,
            )

            waitingForStartupLocation -> Text(
                text = "Checking device location before nearby hospitals can be searched.",
                style = MaterialTheme.typography.bodyLarge,
            )

            hospitals.isEmpty() -> Text(
                text = snapshot.detail,
                style = MaterialTheme.typography.bodyLarge,
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                hospitals.forEach { hospital -> HospitalRowView(row = hospital, mapPreview = mapPreview) }
            }
        }
        if (enabled && waitingForStartupLocation.not()) {
            snapshot.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
        MapPreviewHost(mapPreview)
    }
}

@Composable
private fun HospitalRowView(
    row: EmergencyHospitalRow,
    mapPreview: MapPreviewController,
) {
    val target = MapTarget(title = row.name, latitude = row.latitude, longitude = row.longitude)
    val description = "${row.name}, ${row.distance.text}"
    MapActionRow(
        description = description,
        canPreview = mapPreview.canPreview(target),
        onClick = { mapPreview.preview(target) },
        onShowPreview = { mapPreview.preview(target) },
        onOpenGoogleMaps = { mapPreview.openExternal(target) },
        modifier = Modifier.testTag(hospitalRowTag(row.id)),
        menuTag = hospitalRowMenuTag(row.id),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (row.detail.isNotBlank()) {
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            row.ownership?.let { OwnershipChip(it) }
        }
        NomadMetricValue(
            number = row.distance.number,
            unit = row.distance.unit,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Top),
        )
    }
}

/** A fact about the hospital (Public, Private), not a status, so it stays neutral. */
@Composable
private fun OwnershipChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
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
        -> NomadBadgeTone.Caution
    }
}

internal const val EmergencyCareCardTag = "emergency-care-card"
