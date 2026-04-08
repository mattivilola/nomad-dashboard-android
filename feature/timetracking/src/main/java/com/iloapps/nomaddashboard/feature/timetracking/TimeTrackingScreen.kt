package com.iloapps.nomaddashboard.feature.timetracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricBlock
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import com.iloapps.nomaddashboard.core.model.isAutomaticallyTracked
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun TimeTrackingRoute(
    onStartForegroundTracking: () -> Unit,
    onStopForegroundTracking: () -> Unit,
    viewModel: TimeTrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationPermission by remember { mutableStateOf(context.hasTrackingNotificationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = context.hasTrackingNotificationPermission()
        if (granted) {
            viewModel.startTracking()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = context.hasTrackingNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TimeTrackingEffect.StartService -> onStartForegroundTracking()
                TimeTrackingEffect.StopService -> onStopForegroundTracking()
            }
        }
    }

    TimeTrackingScreen(
        state = state,
        hasNotificationPermission = hasNotificationPermission,
        onProjectNameChanged = viewModel::updateDraftProjectName,
        onCreateProject = viewModel::createProject,
        onAllocateTrackedTime = viewModel::allocateTrackedTime,
        onStartTracking = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasNotificationPermission.not()) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.startTracking()
            }
        },
        onStopTracking = viewModel::stopTracking,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeTrackingScreen(
    state: TimeTrackingUiState,
    hasNotificationPermission: Boolean,
    onProjectNameChanged: (String) -> Unit,
    onCreateProject: () -> Unit,
    onAllocateTrackedTime: (java.util.UUID) -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = state.activeEntry != null
    val now = rememberTickerInstant(enabled = isRunning)
    val bufferedDuration = bufferedDuration(state = state, now = now)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NomadCard(modifier = Modifier.testTag("timetracking_overview")) {
                NomadTopBar(
                    title = "Time Tracking",
                    subtitle = trackingStatusLabel(state),
                    supportingText = trackingSupportLine(state),
                    badgeText = autoWindowLabel(state),
                    badgeTone = if (state.activeEntry != null) NomadBadgeTone.Accent else NomadBadgeTone.Info,
                    trailing = {
                        Icon(Icons.Rounded.Timer, contentDescription = null)
                    },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2,
                ) {
                    NomadMetricBlock("Projects", state.projects.size.toString(), "quick allocation lanes")
                    NomadMetricBlock("Buffer", formatDuration(bufferedDuration), bufferSummary(state))
                    NomadMetricBlock("Mode", if (isRunning) trackingModeLabel(state) else "Paused", "capture state")
                    NomadMetricBlock("Recent", state.recentEntries.size.toString(), "allocated sessions")
                }
            }
        }

        if (state.settings.projectTimeTrackingEnabled.not()) {
            item {
                StatusCard(
                    title = "Project time tracking is off",
                    body = "Enable time tracking in Settings to start continuous local capture and dashboard allocation.",
                )
            }
            return@LazyColumn
        }

        item {
            NomadCard(modifier = Modifier.testTag("timetracking_capture_card")) {
                NomadSectionClusterHeader(
                    title = if (isRunning) "Capture Running" else "Capture Buffer",
                    subtitle = when {
                        isRunning -> "The timer keeps collecting unallocated time until you assign it."
                        state.pendingEntries.isNotEmpty() -> "The timer is paused. Allocate this buffer or press play to continue capturing."
                        else -> "Within your auto window the timer starts on its own. Outside it you can resume manually."
                    },
                    badges = listOf(
                        trackingStatusLabel(state) to if (isRunning) NomadBadgeTone.Accent else NomadBadgeTone.Info,
                        (if (hasNotificationPermission) "Notifications ready" else "Notification permission needed") to
                            if (hasNotificationPermission) NomadBadgeTone.Good else NomadBadgeTone.Warning,
                    ),
                )
                TimeBufferHero(
                    duration = bufferedDuration,
                    caption = when {
                        isRunning -> "Started ${state.activeEntry?.entry?.startAt?.formatClock().orEmpty()} · ${trackingModeLabel(state)} capture"
                        state.pendingEntries.isNotEmpty() -> "${state.pendingEntries.size} closed segment${state.pendingEntries.size.pluralSuffix()} waiting"
                        else -> "Ready for the next allocation cycle"
                    },
                )
                Button(
                    onClick = if (isRunning) onStopTracking else onStartTracking,
                    modifier = Modifier.fillMaxWidth(),
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
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            NomadCard(modifier = Modifier.testTag("timetracking_quick_allocate")) {
                NomadSectionClusterHeader(
                    title = "Quick Allocate",
                    subtitle = if (state.projects.isEmpty()) {
                        "Create at least one project. The built-in Other lane is always available."
                    } else {
                        "Tap a project to file the current buffer and immediately reset the counter."
                    },
                )
                if (state.projects.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.projects.forEach { project ->
                            FilterChip(
                                selected = false,
                                onClick = { onAllocateTrackedTime(project.id) },
                                enabled = bufferedDuration.seconds > 0,
                                label = {
                                    Text(
                                        text = compactProjectLabel(project.name),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
                Text(
                    text = if (bufferedDuration.seconds > 0) {
                        "Allocation converts every waiting segment into one chosen project and then restarts capture when appropriate."
                    } else {
                        "The buffer is empty right now."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }

        item {
            NomadCard {
                NomadSectionClusterHeader(
                    title = "Projects",
                    subtitle = "Keep project labels compact for the dashboard quick-allocate chips.",
                )
                if (state.projects.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.projects.forEach { project ->
                            NomadStatusBadge(
                                text = project.name,
                                tone = NomadBadgeTone.Info,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.draftProjectName,
                    onValueChange = onProjectNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New project") },
                    supportingText = { Text("Aim for short names so the dashboard chips stay readable.") },
                    singleLine = true,
                )
                Button(onClick = onCreateProject) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Add project", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            NomadCard {
                NomadSectionClusterHeader(
                    title = "Recent Allocations",
                    subtitle = if (state.recentEntries.isEmpty()) {
                        "Allocated sessions show up here after you file the buffer."
                    } else {
                        "${state.recentEntries.size} completed allocations saved locally."
                    },
                )
                if (state.recentEntries.isEmpty()) {
                    Text(
                        text = "No allocated entries yet.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.recentEntries.forEach { entry ->
                            RecentEntryRow(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeBufferHero(
    duration: Duration,
    caption: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun RecentEntryRow(entry: TimeTrackingRecord) {
    val endAt = entry.entry.endAt ?: return
    NomadCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 12.dp),
            ) {
                Text(
                    text = entry.project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${entry.entry.startAt.formatTimestamp()} to ${endAt.formatTimestamp()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                )
            }
            NomadStatusBadge(
                text = formatDuration(Duration.between(entry.entry.startAt, endAt)),
                tone = NomadBadgeTone.Info,
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
) {
    NomadCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun rememberTickerInstant(enabled: Boolean): Instant {
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

private fun bufferedDuration(
    state: TimeTrackingUiState,
    now: Instant,
): Duration {
    val closedDuration = state.pendingEntries.fold(Duration.ZERO) { total, record ->
        val endAt = record.entry.endAt ?: return@fold total
        total + Duration.between(record.entry.startAt, endAt)
    }
    val activeDuration = state.activeEntry?.let { active ->
        Duration.between(active.entry.startAt, now)
    } ?: Duration.ZERO
    return closedDuration + activeDuration
}

private fun trackingStatusLabel(state: TimeTrackingUiState): String = when {
    state.activeEntry != null -> "Running"
    state.pendingEntries.isNotEmpty() -> "Paused with buffer"
    else -> "Ready"
}

private fun trackingModeLabel(state: TimeTrackingUiState): String = when {
    state.activeEntry?.entry?.isAutomaticallyTracked() == true -> "Auto"
    state.activeEntry != null -> "Manual"
    else -> "Stopped"
}

private fun trackingSupportLine(state: TimeTrackingUiState): String = when {
    state.activeEntry?.entry?.isAutomaticallyTracked() == true ->
        "Continuous local capture is running inside the configured auto window."
    state.activeEntry != null ->
        "Manual capture is running. Allocate whenever you want to file the current buffer."
    state.pendingEntries.isNotEmpty() ->
        "The timer is paused and the current buffer is waiting for allocation."
    else ->
        "Nomad Dashboard now tracks into an unallocated buffer first, then you file it to a project."
}

private fun autoWindowLabel(state: TimeTrackingUiState): String =
    "${state.settings.projectTimeTrackingAutoStartMinutes.formatClockMinutes()}-${state.settings.projectTimeTrackingAutoStopMinutes.formatClockMinutes()}"

private fun bufferSummary(state: TimeTrackingUiState): String = when {
    state.activeEntry != null && state.pendingEntries.isNotEmpty() ->
        "${state.pendingEntries.size + 1} live + waiting segments"
    state.activeEntry != null -> "currently counting"
    state.pendingEntries.isNotEmpty() -> "${state.pendingEntries.size} waiting segment${state.pendingEntries.size.pluralSuffix()}"
    else -> "nothing queued"
}

private fun compactProjectLabel(name: String): String =
    if (name.length <= 8) name else "${name.take(8)}..."

private fun Instant.formatTimestamp(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(atZone(ZoneId.systemDefault()))

private fun Instant.formatClock(): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .format(atZone(ZoneId.systemDefault()))

private fun Int.formatClockMinutes(): String = "%02d:%02d".format(this / 60, this % 60)

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun Int.pluralSuffix(): String = if (this == 1) "" else "s"

private fun Context.hasTrackingNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
