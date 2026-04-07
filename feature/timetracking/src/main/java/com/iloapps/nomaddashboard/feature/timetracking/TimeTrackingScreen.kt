package com.iloapps.nomaddashboard.feature.timetracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadPill
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader
import com.iloapps.nomaddashboard.core.model.TimeTrackingRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

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
        onProjectSelected = viewModel::selectProject,
        onProjectNameChanged = viewModel::updateDraftProjectName,
        onCreateProject = viewModel::createProject,
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
    onProjectSelected: (java.util.UUID) -> Unit,
    onProjectNameChanged: (String) -> Unit,
    onCreateProject: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeEntry = state.activeEntry
    val now = rememberTickerInstant(activeEntry != null)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NomadCard {
                NomadSectionHeader(
                    title = "Time Tracking",
                    subtitle = if (state.settings.projectTimeTrackingEnabled) {
                        "Local-first project tracking with a foreground notification."
                    } else {
                        "Project time tracking is currently disabled."
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NomadPill(text = "Projects: ${state.projects.size}")
                    NomadPill(text = if (hasNotificationPermission) "Notifications ready" else "Notification permission needed")
                }
            }
        }

        if (state.settings.projectTimeTrackingEnabled.not()) {
            item {
                StatusCard(
                    title = "Project time tracking is off",
                    body = "Enable project time tracking in Settings to unlock local project capture, foreground tracking, and the persistent notification.",
                )
            }
            return@LazyColumn
        }

        item {
            NomadCard {
                NomadSectionHeader(
                    title = "Projects",
                    subtitle = if (state.projects.isEmpty()) {
                        "Add your first project to start a local time-tracking ledger."
                    } else {
                        "Select the project that should receive the next tracked session."
                    },
                )
                if (state.projects.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.projects.forEach { project ->
                            FilterChip(
                                selected = state.selectedProjectId == project.id,
                                onClick = { onProjectSelected(project.id) },
                                label = { Text(project.name) },
                                enabled = activeEntry == null,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No projects saved yet.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                OutlinedTextField(
                    value = state.draftProjectName,
                    onValueChange = onProjectNameChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    label = { Text("New project") },
                    singleLine = true,
                )
                Button(
                    onClick = onCreateProject,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Add project", modifier = Modifier.padding(start = 8.dp))
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            NomadCard {
                if (activeEntry != null) {
                    NomadSectionHeader(
                        title = "Active Session",
                        subtitle = activeEntry.project.name,
                    )
                    Text(
                        text = "Started ${activeEntry.entry.startAt.formatTimestamp()}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Elapsed ${formatDuration(Duration.between(activeEntry.entry.startAt, now))}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                    )
                    Button(onClick = onStopTracking) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Text("Stop tracking", modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    NomadSectionHeader(
                        title = "Start Tracking",
                        subtitle = state.selectedProjectId?.let { "Ready to start a session." }
                            ?: "Create or select a project before starting.",
                    )
                    Text(
                        text = if (hasNotificationPermission) {
                            "Starting a session writes an open entry locally and launches the foreground notification."
                        } else {
                            "Android 13+ notification permission is required before the foreground tracking service can start."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = onStartTracking,
                        enabled = state.selectedProjectId != null,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Text("Start tracking", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            NomadCard {
                NomadSectionHeader(
                    title = "Recent Sessions",
                    subtitle = if (state.recentEntries.isEmpty()) {
                        "Completed sessions will appear here after you stop a timer."
                    } else {
                        "${state.recentEntries.size} completed session${if (state.recentEntries.size == 1) "" else "s"} saved locally."
                    },
                )
                if (state.recentEntries.isEmpty()) {
                    Text(
                        text = "No completed time entries yet.",
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
private fun RecentEntryRow(entry: TimeTrackingRecord) {
    val endAt = entry.entry.endAt ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.project.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            NomadPill(text = formatDuration(Duration.between(entry.entry.startAt, endAt)))
        }
        Text(
            text = "Start ${entry.entry.startAt.formatTimestamp()} · Stop ${endAt.formatTimestamp()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
        )
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
            Icon(Icons.Rounded.Timer, contentDescription = null)
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

private fun Instant.formatTimestamp(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(atZone(ZoneId.systemDefault()))

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun Context.hasTrackingNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
