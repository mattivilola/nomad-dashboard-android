package com.iloapps.nomaddashboard.feature.dashboard

import com.iloapps.nomaddashboard.core.model.customizeOrder
import com.iloapps.nomaddashboard.core.model.withCardMoved
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.R
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardLocation
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardRow
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardSummaryBuilder
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardSummaryInput
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Renders one dashboard card's content for [card]. Each card file owns its own branch here. */
@Composable
internal fun DashboardCardSlot(
    card: DashboardCardId,
    frame: DashboardCardFrame,
    state: DashboardUiState,
    actions: DashboardCardActions,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    when (card) {
        DashboardCardId.WEATHER -> WeatherSectionCard(
            snapshot = state.snapshot.weather,
            startupLocation = state.snapshot.startupLocation,
            surfSpot = state.settings.surfSpot,
            marine = state.snapshot.marine,
            modifier = Modifier.testTag("dashboard_weather_card"),
            frame = frame,
            airQuality = state.snapshot.airQuality,
            settings = state.settings,
            daylight = remember(state.snapshot.travelContext) {
                CardLocation.daylightReference(state.snapshot, ZoneId.systemDefault())
            },
            onOpenSurfSettings = { actions.onOpenSettings(SettingsDestination.SURF_SPOT) },
        )

        DashboardCardId.TRAVEL_ALERTS -> TravelAlertsSectionCard(
            snapshot = state.snapshot.travelAlerts,
            startupLocation = state.snapshot.startupLocation,
            onOpenSettings = { actions.onOpenSettings(SettingsDestination.TRAVEL_ALERTS) },
            frame = frame,
        )

        DashboardCardId.CONNECTIVITY -> ConnectivitySectionCard(
            state = state,
            frame = frame,
        )

        DashboardCardId.TRAVEL_CONTEXT -> TravelContextSectionCard(
            state = state,
            hasLocationPermission = actions.hasLocationPermission,
            onRequestLocationPermission = actions.onRequestLocationPermission,
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
            frame = frame,
        )

        DashboardCardId.LOCAL_INFO -> LocalInfoSectionCard(
            enabled = state.settings.localInfoEnabled,
            snapshot = state.snapshot,
            onOpenSettings = { actions.onOpenSettings(SettingsDestination.LOCAL_INFO) },
            frame = frame,
        )

        DashboardCardId.FUEL_PRICES -> FuelPricesSectionCard(
            enabled = state.settings.fuelPricesEnabled,
            snapshot = state.snapshot.fuelPrices,
            startupLocation = state.snapshot.startupLocation,
            onOpenSettings = { actions.onOpenSettings(SettingsDestination.FUEL_PRICES) },
            onOpenMap = { target ->
                context.openMapLocation(
                    latitude = target.latitude,
                    longitude = target.longitude,
                    label = target.title,
                )
            },
            frame = frame,
        )

        DashboardCardId.POWER -> PowerSectionCard(
            state = state,
            frame = frame,
        )

        DashboardCardId.TIME_TRACKING -> TimeTrackingSectionCard(
            trackingState = state.timeTracking,
            hasNotificationPermission = actions.hasNotificationPermission,
            autoWindowLabel = "${state.settings.projectTimeTrackingAutoStartMinutes.formatClockMinutes()}-${state.settings.projectTimeTrackingAutoStopMinutes.formatClockMinutes()}",
            interruptionPulseKey = actions.interruptionPulseKey,
            onStartTracking = actions.onStartTracking,
            onStopTracking = actions.onStopTracking,
            onReportInterruption = actions.onReportInterruption,
            onAllocateTrackedTime = actions.onAllocateTrackedTime,
            frame = frame,
        )

        DashboardCardId.EMERGENCY_CARE -> EmergencyCareSectionCard(
            enabled = state.settings.emergencyCareEnabled,
            snapshot = state.snapshot.emergencyCare,
            startupLocation = state.snapshot.startupLocation,
            emergencyNumbers = EmergencyNumberDirectory.numbersFor(CardLocation.countryCode(state.snapshot)),
            onOpenMap = { target ->
                context.openMapLocation(
                    latitude = target.latitude,
                    longitude = target.longitude,
                    label = target.title,
                )
            },
            frame = frame,
        )

        DashboardCardId.EMBASSY -> EmbassyCardHost(
            settings = state.settings,
            snapshot = state.snapshot,
            onOpenSettings = actions.onOpenSettings,
            onOpenUri = context::openExternalUri,
            onDial = context::dialPhoneNumber,
            onCopy = { text -> clipboardManager.setText(AnnotatedString(text)) },
            frame = frame,
        )

        DashboardCardId.STAYS -> StaysCardHost(
            settings = state.settings,
            countryDays = state.countryDays,
            snapshot = state.snapshot,
            onOpenSettings = { destination -> actions.onOpenSettings(destination) },
            onOpenVisited = actions.onOpenVisited,
            frame = frame,
        )
    }
}
