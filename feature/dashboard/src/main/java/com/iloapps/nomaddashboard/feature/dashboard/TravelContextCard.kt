package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.travel.cards.TravelContextCardPresentation
import com.iloapps.nomaddashboard.core.travel.cards.TravelContextLabels

/**
 * Four rows: Public IP (copy), Wi-Fi, VPN and Location (map). The comparison of the device location
 * with the network's, with the time zone and signal, sits behind the "Device vs network" row.
 */
@Composable
internal fun TravelContextSectionCard(
    state: DashboardUiState,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCopyPublicIp: () -> Unit,
    onOpenMap: () -> Unit,
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val snapshot = state.snapshot
    val isChecking = snapshot.startupLocation.isChecking
    val presentation = remember(
        snapshot.connectivity,
        snapshot.travelContext,
        state.settings.publicIpGeolocationEnabled,
        hasLocationPermission,
        isChecking,
    ) {
        TravelContextCardPresentation.of(
            connectivity = snapshot.connectivity,
            travelContext = snapshot.travelContext,
            isPublicIpLocationEnabled = state.settings.publicIpGeolocationEnabled,
            hasLocationPermission = hasLocationPermission,
            isCheckingDeviceLocation = isChecking,
        )
    }
    var comparisonExpanded by rememberSaveable { mutableStateOf(false) }
    val vpnBadge = (if (snapshot.connectivity.vpnActive) "VPN On" else "VPN Off") to NomadBadgeTone.Neutral

    DashboardCard(
        card = DashboardCardId.TRAVEL_CONTEXT,
        frame = frame,
        modifier = Modifier.testTag("dashboard_travel_context_card"),
        subtitle = "Checking device location before location-based cards refresh.".takeIf { isChecking },
        collapsedBadge = vpnBadge,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TravelContextRow(
                label = "Public IP",
                value = presentation.publicIp,
                tag = "travel_context_public_ip",
            ) {
                IconButton(onClick = onCopyPublicIp, enabled = presentation.hasPublicIp) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy public IP")
                }
            }
            TravelContextRow(
                label = "Wi-Fi",
                value = presentation.wifi,
                tag = "travel_context_wifi",
                help = presentation.wifiHelp,
            )
            TravelContextRow(label = "VPN", value = presentation.vpn, tag = "travel_context_vpn")
            TravelContextRow(
                label = "Location",
                value = presentation.location,
                tag = "travel_context_location",
            ) {
                IconButton(onClick = onOpenMap, enabled = travelContextMapTarget(snapshot) != null) {
                    Icon(imageVector = Icons.Rounded.Map, contentDescription = "Open map")
                }
            }
        }

        if (!hasLocationPermission) {
            NomadInlineAction(
                label = "Allow location",
                icon = Icons.Rounded.MyLocation,
                onClick = onRequestLocationPermission,
                modifier = Modifier.testTag("travel_context_allow_location"),
            )
        }

        CardDetailsToggle(
            label = "Device vs network",
            expanded = comparisonExpanded,
            onToggle = { comparisonExpanded = !comparisonExpanded },
            modifier = Modifier.testTag("travel_context_comparison_toggle"),
            trailing = presentation.alignment?.let { alignment ->
                {
                    NomadStatusBadge(
                        text = alignment.label,
                        tone = alignment.tone.badgeTone(),
                        modifier = Modifier.testTag("travel_context_alignment"),
                    )
                }
            },
        )
        if (comparisonExpanded) {
            Column(
                modifier = Modifier.testTag("travel_context_comparison"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presentation.comparison.forEach { row ->
                    CardDetailRow(
                        label = row.label,
                        value = row.value,
                        supporting = row.supporting,
                        trailing = row.status?.let { status ->
                            { NomadStatusBadge(text = status, tone = row.statusTone.badgeTone()) }
                        },
                    )
                }
            }
        }
    }
}

/** One of the four rows: the label, the value (wrapping rather than cut) and an optional action. */
@Composable
private fun TravelContextRow(
    label: String,
    value: String,
    tag: String,
    help: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    CardDetailRow(
        label = label,
        value = value,
        modifier = Modifier
            .testTag(tag)
            .then(
                if (help != null) {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $value. $help" }
                } else {
                    Modifier
                },
            ),
        trailing = action,
    )
}

internal data class TravelMapTarget(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

internal fun travelContextMapTarget(snapshot: DashboardSnapshot): TravelMapTarget? {
    val travelContext = snapshot.travelContext
    val latitude = travelContext.deviceLatitude ?: travelContext.latitude ?: return null
    val longitude = travelContext.deviceLongitude ?: travelContext.longitude ?: return null
    val label = travelContext.deviceLocationLabel().takeUnless { it == TravelContextCardPresentation.NO_LOCATION }
        ?: travelContext.ipLocationLabel()
    return TravelMapTarget(
        latitude = latitude,
        longitude = longitude,
        label = label,
    )
}

internal fun TravelContextSnapshot.hasDeviceLocation(): Boolean = TravelContextLabels.hasDeviceLocation(this)

internal fun TravelContextSnapshot.hasIpLocation(): Boolean = TravelContextLabels.hasIpLocation(this)

internal fun TravelContextSnapshot.deviceLocationLabel(): String = TravelContextLabels.deviceLocationLabel(this)

internal fun TravelContextSnapshot.ipLocationLabel(): String = TravelContextLabels.ipLocationLabel(this)
