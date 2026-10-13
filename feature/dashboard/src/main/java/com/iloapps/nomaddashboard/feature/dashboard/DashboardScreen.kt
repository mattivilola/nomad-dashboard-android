package com.iloapps.nomaddashboard.feature.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.ResourceMode
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.model.customizeOrder
import com.iloapps.nomaddashboard.core.model.withCardMoved
import com.iloapps.nomaddashboard.core.travel.glance.GlanceTimeTracking
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardRow
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardSummaryBuilder
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardSummaryInput
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun DashboardRoute(
    onStartForegroundTracking: () -> Unit,
    onStopForegroundTracking: () -> Unit,
    onOpenSettings: (SettingsDestination?) -> Unit,
    onOpenVisited: () -> Unit,
    onOpenCompanion: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    companionOverview: @Composable () -> Unit = {},
    isWideLayout: Boolean = false,
    scrollTarget: DashboardScrollTarget? = null,
    onScrollTargetHandled: () -> Unit = {},
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
                viewModel.onResume()
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
        onRefreshCard = viewModel::refreshCard,
        companionOverview = companionOverview,
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
        onOpenVisited = onOpenVisited,
        onOpenCompanion = onOpenCompanion,
        onSelectResourceMode = viewModel::setResourceMode,
        onAcknowledgeArrival = viewModel::acknowledgeArrival,
        onShowArrivalBriefing = viewModel::showArrivalBriefing,
        layoutActions = DashboardLayoutActions(
            onCollapsedChange = viewModel::setCardCollapsed,
            onReorder = viewModel::setCardOrder,
            onToggleWidth = viewModel::toggleCardWidth,
            onResetLayout = viewModel::resetDashboardLayout,
            onSelectTravelProfile = viewModel::applyTravelProfile,
        ),
        isWideLayout = isWideLayout,
        scrollTarget = scrollTarget,
        onScrollTargetHandled = onScrollTargetHandled,
    )
}

/**
 * The dashboard.
 *
 * @param onOpenSettings Opens Settings on a destination's group; null opens Settings as it is.
 * @param onOpenVisited Opens the Visited tab, where the recorded days can be reviewed.
 * @param onOpenCompanion Opens the Companion screen, which holds Workplaces and Saved essentials.
 * @param onSelectResourceMode Saves the data-usage choice made in the verdict strip.
 * @param onAcknowledgeArrival Hides the arrival briefing of a country ("Got it").
 * @param onShowArrivalBriefing Re-opens the arrival briefing of the current country from the overflow menu.
 * @param layoutActions What Customize Layout, the card chevrons and the travel profile switch change in
 * the saved settings.
 * @param isWideLayout Lets two adjacent half-width cards share a row; a phone stays one column.
 * @param scrollTarget A card to bring into view: it is expanded if collapsed, then scrolled to. Clear it
 * in [onScrollTargetHandled].
 */
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
    onOpenSettings: (SettingsDestination?) -> Unit = {},
    onOpenVisited: () -> Unit = {},
    onOpenCompanion: () -> Unit = {},
    onSelectResourceMode: (ResourceMode) -> Unit = {},
    onAcknowledgeArrival: (String) -> Unit = {},
    onShowArrivalBriefing: () -> Unit = {},
    modifier: Modifier = Modifier,
    onRefreshCard: (DashboardCardId) -> Unit = {},
    companionOverview: @Composable () -> Unit = {},
    layoutActions: DashboardLayoutActions = DashboardLayoutActions(),
    isWideLayout: Boolean = false,
    scrollTarget: DashboardScrollTarget? = null,
    onScrollTargetHandled: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val itemSpacingPx = with(LocalDensity.current) { DashboardItemSpacing.toPx() }
    var isCustomizingLayout by rememberSaveable { mutableStateOf(false) }

    val currentState by rememberUpdatedState(state)
    val currentHasLocationPermission by rememberUpdatedState(hasLocationPermission)
    val currentIsWideLayout by rememberUpdatedState(isWideLayout)
    val currentIsCustomizing by rememberUpdatedState(isCustomizingLayout)
    val currentLayoutActions by rememberUpdatedState(layoutActions)

    val reorder = remember(listState, scope, itemSpacingPx) {
        DashboardReorderState(listState, scope, itemSpacingPx) { order -> currentLayoutActions.onReorder(order) }
    }
    val customizeOrder = reorder.draftOrder ?: DashboardCardId.customizeOrder(state.settings.dashboardCardOrder)
    val hasArrivalBriefing = state.arrival != null
    val listItems = remember(
        state.settings,
        hasLocationPermission,
        isCustomizingLayout,
        isWideLayout,
        hasArrivalBriefing,
        customizeOrder,
    ) {
        dashboardListItems(
            settings = state.settings,
            hasLocationPermission = hasLocationPermission,
            isCustomizingLayout = isCustomizingLayout,
            isWideLayout = isWideLayout,
            hasArrivalBriefing = hasArrivalBriefing,
            customizeOrder = customizeOrder,
        )
    }

    // A card to show comes from the caller (a deep link) or from a tapped tile or heads-up item. Either
    // way it is expanded first and then scrolled to.
    var requestedCard by remember { mutableStateOf<DashboardScrollTarget?>(null) }
    LaunchedEffect(scrollTarget) {
        if (scrollTarget != null) requestedCard = scrollTarget
    }
    LaunchedEffect(requestedCard) {
        val target = requestedCard ?: return@LaunchedEffect
        revealDashboardCard(
            card = target.card,
            isCollapsed = { target.card in currentState.settings.dashboardCollapsedCards },
            expand = { currentLayoutActions.onCollapsedChange(target.card, false) },
            indexOfCard = {
                dashboardListItems(
                    settings = currentState.settings,
                    hasLocationPermission = currentHasLocationPermission,
                    isCustomizingLayout = currentIsCustomizing,
                    isWideLayout = currentIsWideLayout,
                    hasArrivalBriefing = currentState.arrival != null,
                ).indexOfCard(target.card)
            },
            scrollToItem = { index -> listState.animateScrollToItem(index) },
        )
        requestedCard = null
        if (target == scrollTarget) onScrollTargetHandled()
    }

    val summaryBuilder = remember { DashboardCardSummaryBuilder() }
    val summaryNow = rememberSummaryInstant(tickEveryMinute = state.timeTracking.activeEntry != null, snapshot = state.snapshot)
    val summaryInput = remember(state.snapshot, state.settings, state.countryDays, state.timeTracking, summaryNow) {
        DashboardCardSummaryInput(
            snapshot = state.snapshot,
            settings = state.settings,
            now = summaryNow,
            zone = ZoneId.systemDefault(),
            countryDays = state.countryDays,
            pendingTimeTracking = dashboardBufferedDuration(state.timeTracking, summaryNow),
        )
    }
    val glanceActions = DashboardGlanceActions(
        onShowCard = { requestedCard = it },
        onOpenHomeTimeZoneSettings = { onOpenSettings(SettingsDestination.HOME_TIME_ZONE) },
        onSelectResourceMode = onSelectResourceMode,
        onOpenCompanion = onOpenCompanion,
    )
    val cardActions = DashboardCardActions(
        hasLocationPermission = hasLocationPermission,
        hasNotificationPermission = hasNotificationPermission,
        interruptionPulseKey = interruptionPulseKey,
        onRequestLocationPermission = onRequestLocationPermission,
        onStartTracking = onStartTracking,
        onStopTracking = onStopTracking,
        onReportInterruption = onReportInterruption,
        onAllocateTrackedTime = onAllocateTrackedTime,
        onOpenSettings = onOpenSettings,
        onOpenVisited = onOpenVisited,
    )

    @Composable
    fun CardSlot(card: DashboardCardId) {
        val summary = remember(summaryInput, card) { summaryBuilder.summarize(card, summaryInput).text }
        DashboardCardSlot(
            card = card,
            frame = DashboardCardFrame(
                summary = summary,
                isCollapsed = card in state.settings.dashboardCollapsedCards,
                onCollapsedChange = { collapsed -> layoutActions.onCollapsedChange(card, collapsed) },
                refresh = if (card in RefreshableCards) {
                    DashboardCardRefresh(state.snapshot.sectionStates[card], onRefreshCard)
                } else {
                    null
                },
            ),
            state = state,
            actions = cardActions,
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_list"),
        verticalArrangement = Arrangement.spacedBy(DashboardItemSpacing),
    ) {
        items(listItems, key = { it.key }) { item ->
            when (item) {
                DashboardListItem.TopBar -> DashboardHeader(
                    header = state.glance.header,
                    refresh = state.glance.refresh,
                    isCheckingLocation = state.snapshot.startupLocation.isChecking,
                    onRefresh = onRefresh,
                    overflowMenu = {
                        DashboardOverflowMenu(
                            isCustomizingLayout = isCustomizingLayout,
                            travelProfile = state.settings.travelProfile,
                            onToggleCustomizing = { isCustomizingLayout = !isCustomizingLayout },
                            onResetLayout = layoutActions.onResetLayout,
                            onSelectTravelProfile = layoutActions.onSelectTravelProfile,
                            onShowArrivalBriefing = onShowArrivalBriefing.takeIf {
                                state.reopenableArrival != null && state.arrival == null
                            },
                        )
                    },
                    timeTrackingRow = {
                        if (state.settings.projectTimeTrackingEnabled) {
                            DashboardTimeTrackingRow(
                                tracking = remember(state.timeTracking, summaryNow) {
                                    GlanceTimeTracking.of(
                                        projects = state.timeTracking.projects,
                                        isRunning = state.timeTracking.activeEntry != null,
                                        hasPendingEntries = state.timeTracking.pendingEntries.isNotEmpty(),
                                        pending = dashboardBufferedDuration(state.timeTracking, summaryNow),
                                        interruptionCount = state.timeTracking.report.interruptionsToday,
                                    )
                                },
                                interruptionPulseKey = interruptionPulseKey,
                                onPrimaryControl = {
                                    if (state.timeTracking.activeEntry != null) onStopTracking() else onStartTracking()
                                },
                                onReportInterruption = onReportInterruption,
                                onAllocate = onAllocateTrackedTime,
                            )
                        }
                    },
                    belowRow = {
                        if (isCustomizingLayout) {
                            DashboardCustomizeBanner(onDone = { isCustomizingLayout = false })
                        }
                    },
                )

                DashboardListItem.Glance -> DashboardGlance(
                    glance = state.glance,
                    resourceMode = state.settings.resourceMode,
                    isLowDataActive = state.snapshot.lowImpactMode,
                    actions = glanceActions,
                )

                DashboardListItem.ArrivalBriefing -> state.arrival?.let { arrival ->
                    ArrivalBriefingCard(
                        presentation = arrival,
                        onAcknowledge = onAcknowledgeArrival,
                        onOpenSettings = { onOpenSettings(SettingsDestination.ARRIVAL_BRIEFING) },
                    )
                }

                DashboardListItem.CompanionOverview -> companionOverview()

                DashboardListItem.LocationAccess -> DashboardLocationAccessCard(
                    settings = state.settings,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenSettings = { onOpenSettings(SettingsDestination.WEATHER) },
                )

                is DashboardListItem.CardRow -> DashboardCardRowContent(
                    row = item.row,
                    modifier = Modifier.animateItem(),
                ) { card -> CardSlot(card) }

                is DashboardListItem.CustomizeCard -> {
                    val card = item.card
                    val index = customizeOrder.indexOf(card)
                    val isDragged = reorder.draggedCard == card
                    DashboardCustomizeFrame(
                        card = card,
                        widthMode = state.settings.dashboardCardWidthModes[card] ?: DashboardCardWidthMode.WIDE,
                        canMoveUp = index > 0,
                        canMoveDown = index < customizeOrder.lastIndex,
                        onMove = { delta ->
                            layoutActions.onReorder(customizeOrder.withCardMoved(card, delta))
                        },
                        onToggleWidth = { layoutActions.onToggleWidth(card) },
                        dragHandleModifier = Modifier.dashboardDragHandle(card, reorder) { customizeOrder },
                        modifier = if (isDragged) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = reorder.dragOffset }
                        } else {
                            Modifier.animateItem()
                        },
                    ) { CardSlot(card) }
                }

                is DashboardListItem.TurnedOff -> DashboardTurnedOffRow(
                    text = item.text,
                    onOpenSettings = { onOpenSettings(item.destination) },
                )
            }
        }
    }
}

private val DashboardItemSpacing = 16.dp

/** The cards that have a per-card "Updated … Refresh" row. */
private val RefreshableCards = setOf(
    DashboardCardId.WEATHER,
    DashboardCardId.TRAVEL_ALERTS,
    DashboardCardId.FUEL_PRICES,
    DashboardCardId.LOCAL_INFO,
    DashboardCardId.EMERGENCY_CARE,
    DashboardCardId.TRAVEL_CONTEXT,
)

/** What the dashboard changes in the saved settings when the user folds, orders or resets cards. */
data class DashboardLayoutActions(
    val onCollapsedChange: (DashboardCardId, Boolean) -> Unit = { _, _ -> },
    /** Saves a new card order, from a drag or a move action. */
    val onReorder: (List<DashboardCardId>) -> Unit = {},
    val onToggleWidth: (DashboardCardId) -> Unit = {},
    /** Restores the current travel profile's recommended order, with wide, expanded cards. */
    val onResetLayout: () -> Unit = {},
    val onSelectTravelProfile: (TravelProfile) -> Unit = {},
)

/** The callbacks the cards use, bundled so a card slot takes them as one parameter. */
internal class DashboardCardActions(
    val hasLocationPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val interruptionPulseKey: Int,
    val onRequestLocationPermission: () -> Unit,
    val onStartTracking: () -> Unit,
    val onStopTracking: () -> Unit,
    val onReportInterruption: () -> Unit,
    val onAllocateTrackedTime: (java.util.UUID) -> Unit,
    val onOpenSettings: (SettingsDestination?) -> Unit,
    val onOpenVisited: () -> Unit,
)

/** The time the collapsed summaries are decided at; it moves once a minute only while time is being tracked. */
@Composable
private fun rememberSummaryInstant(tickEveryMinute: Boolean, snapshot: DashboardSnapshot): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(tickEveryMinute, snapshot) {
        now = Instant.now()
        while (tickEveryMinute) {
            delay(SummaryTickMillis)
            now = Instant.now()
        }
    }
    return now
}

private const val SummaryTickMillis = 60_000L

@Composable
private fun DashboardCustomizeBanner(onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_customize_banner"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Customize layout: hold a handle to drag a card, or use the arrows. Every card is listed, including turned-off ones.",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        NomadInlineAction(
            label = "Done",
            onClick = onDone,
            modifier = Modifier.testTag("dashboard_customize_done"),
        )
    }
}

/** One row of the card list: a single card at full width, or two half-width cards side by side. */
@Composable
private fun DashboardCardRowContent(
    row: DashboardCardRow,
    modifier: Modifier = Modifier,
    card: @Composable (DashboardCardId) -> Unit,
) {
    val items = row.items
    if (items.size == 1) {
        Box(modifier = modifier.fillMaxWidth()) { card(items.single().card) }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DashboardItemSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            items.forEach { item ->
                Box(modifier = Modifier.weight(1f)) { card(item.card) }
            }
        }
    }
}

@Composable
private fun DashboardTurnedOffRow(
    text: String,
    onOpenSettings: () -> Unit,
) {
    NomadCard(
        modifier = Modifier.testTag("dashboard_turned_off_row"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            NomadInlineAction(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                onClick = onOpenSettings,
                modifier = Modifier.testTag("dashboard_turned_off_settings"),
            )
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
            NomadInlineAction(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                onClick = onOpenSettings,
            )
        }
    }
}

private fun Context.hasDashboardLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasDashboardNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
