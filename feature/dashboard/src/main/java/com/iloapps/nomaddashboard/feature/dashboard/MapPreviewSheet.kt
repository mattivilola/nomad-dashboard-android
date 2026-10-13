package com.iloapps.nomaddashboard.feature.dashboard

import com.iloapps.nomaddashboard.core.designsystem.component.hasConfiguredMapsApiKey
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction

/** A place a row can show on a map: a station, a hospital. */
internal data class MapTarget(
    val title: String,
    val latitude: Double,
    val longitude: Double,
) {
    val isCoordinateValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

internal const val MapPreviewSheetTag = "map_preview_sheet"
internal const val MapPreviewOpenExternalTag = "map_preview_open_external"

internal const val MapMenuShowPreviewLabel = "Show map preview"
internal const val MapMenuOpenExternalLabel = "Open in Google Maps"

private const val PreviewZoom = 14f

/**
 * What happens when a row is tapped: the in-app map preview when the build has a Google Maps key and
 * the coordinates are usable, else the external maps app, so a row never does nothing.
 *
 * @param hasMapsApiKey whether the Maps SDK can draw a map in this build.
 * @param onOpenExternal opens the place in Google Maps (or any maps app).
 */
@Stable
internal class MapPreviewController(
    private val hasMapsApiKey: Boolean,
    private val onOpenExternal: (MapTarget) -> Unit,
) {
    /** The place whose preview is open; null while the sheet is closed. */
    var target by mutableStateOf<MapTarget?>(null)
        private set

    /** Whether [target] can be drawn on the in-app map. */
    fun canPreview(target: MapTarget): Boolean = hasMapsApiKey && target.isCoordinateValid

    /** Opens the preview, or the external maps app when the preview is not possible. */
    fun preview(target: MapTarget) {
        if (canPreview(target)) this.target = target else openExternal(target)
    }

    fun openExternal(target: MapTarget) {
        if (target.isCoordinateValid) onOpenExternal(target)
    }

    fun dismiss() {
        target = null
    }
}

@Composable
internal fun rememberMapPreviewController(
    onOpenExternal: (MapTarget) -> Unit,
    hasMapsApiKey: Boolean = rememberHasMapsApiKey(),
): MapPreviewController {
    val currentOpenExternal by rememberUpdatedState(onOpenExternal)
    return remember(hasMapsApiKey) { MapPreviewController(hasMapsApiKey) { currentOpenExternal(it) } }
}

@Composable
private fun rememberHasMapsApiKey(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.hasConfiguredMapsApiKey() }
}

/** Draws the open preview of [controller], if any. Place it once in the card that owns the controller. */
@Composable
internal fun MapPreviewHost(controller: MapPreviewController) {
    val shown = controller.target ?: return
    MapPreviewSheet(
        title = shown.title,
        latitude = shown.latitude,
        longitude = shown.longitude,
        onOpenExternal = { controller.openExternal(shown) },
        onDismiss = controller::dismiss,
    )
}

/**
 * A bottom sheet with a Google map centred on one marker, and the way out to the Google Maps app. It
 * opens fully expanded; it closes by tapping outside it, swiping it down from its handle or going back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapPreviewSheet(
    title: String,
    latitude: Double,
    longitude: Double,
    onOpenExternal: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(MapPreviewSheetTag),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val position = remember(latitude, longitude) { LatLng(latitude, longitude) }
            val cameraPositionState = rememberCameraPositionState {
                this.position = CameraPosition.fromLatLngZoom(position, PreviewZoom)
            }
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
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
            ) {
                Marker(state = remember(position) { MarkerState(position = position) }, title = title)
            }
            NomadInlineAction(
                label = MapMenuOpenExternalLabel,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = onOpenExternal,
                modifier = Modifier.testTag(MapPreviewOpenExternalTag),
            )
        }
    }
}

/**
 * A row of a card that points at a place. Tapping the row is the primary action ([onClick], normally the
 * map preview); the trailing ⋯ menu holds the map actions, so Google Maps stays one tap away.
 *
 * @param description what the row is, for accessibility ("Diesel, Repsol Tarifa, 1.429 per liter").
 * @param canPreview whether the menu offers the in-app preview next to Google Maps.
 */
@Composable
internal fun MapActionRow(
    description: String,
    canPreview: Boolean,
    onClick: () -> Unit,
    onShowPreview: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    modifier: Modifier = Modifier,
    menuTag: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .dashboardRowTile()
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    onClickLabel = if (canPreview) MapMenuShowPreviewLabel else MapMenuOpenExternalLabel,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
            content = content,
        )
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = menuTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More actions for $description",
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (canPreview) {
                    DropdownMenuItem(
                        text = { Text(MapMenuShowPreviewLabel) },
                        leadingIcon = { Icon(Icons.Rounded.Map, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShowPreview()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(MapMenuOpenExternalLabel) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenGoogleMaps()
                    },
                )
            }
        }
    }
}

