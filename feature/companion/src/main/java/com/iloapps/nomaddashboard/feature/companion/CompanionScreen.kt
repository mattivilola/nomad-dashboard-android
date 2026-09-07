package com.iloapps.nomaddashboard.feature.companion

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iloapps.nomaddashboard.core.data.companion.CompanionAlertPolicy
import com.iloapps.nomaddashboard.core.data.companion.ForegroundCompanionSignal
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar
import com.iloapps.nomaddashboard.core.model.CompanionImportPreview
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import com.iloapps.nomaddashboard.core.model.OfflineEssentialKind
import com.iloapps.nomaddashboard.core.model.TimezoneOverlapPlanner
import com.iloapps.nomaddashboard.core.model.WorkReadiness
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Integration contract: pass the existing dashboard snapshot; this module never refreshes providers. */
@Composable
fun CompanionRoute(
    snapshot: DashboardSnapshot,
    onBack: (() -> Unit)? = null,
    viewModel: CompanionViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val storageError by viewModel.storageError.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    CompanionScreen(
        snapshot = snapshot,
        data = data,
        onUpdate = viewModel::update,
        onAddEssential = viewModel::addEssential,
        onUpdateEssential = viewModel::updateEssential,
        onAddPlanner = viewModel::addPlanner,
        onUpdatePlanner = viewModel::updatePlanner,
        onRecordWorkplace = { label -> viewModel.recordWorkplace(snapshot, label) },
        onUpdateVisit = viewModel::updateVisit,
        onExport = viewModel::exportBackup,
        onPreviewImport = viewModel::previewImport,
        onMergeImport = viewModel::mergeImport,
        storageError = storageError,
        operationError = actionError,
    )
}

/** Put this once above NavHost. It is lifecycle-gated and never starts a service or poll. */
@android.annotation.SuppressLint("MissingPermission") // canPostCompanionNotifications is checked immediately before posting.
@Composable
fun CompanionRuntime(
    snapshot: DashboardSnapshot,
    viewModel: CompanionViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isForeground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    var previous by remember { mutableStateOf<ForegroundCompanionSignal?>(null) }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            isForeground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val latestSnapshot by rememberUpdatedState(snapshot)
    val latestPreferences by rememberUpdatedState(data.preferences)
    LaunchedEffect(isForeground) {
        previous = null
        if (!isForeground) return@LaunchedEffect
        var candidate: ForegroundCompanionSignal? = null
        var candidateSince = 0L
        while (isActive) {
            val currentSnapshot = latestSnapshot
            val preferences = latestPreferences
            val now = Clock.systemUTC().millis()
            val age = (currentSnapshot.localSignalsAt ?: currentSnapshot.lastRefresh)?.toEpochMilli()?.let { now - it }
            val fresh = age != null && age in 0..120_000
            val current = ForegroundCompanionSignal(
                online = currentSnapshot.connectivity.isOnline.takeIf { fresh && !currentSnapshot.connectivity.internetState.equals("checking", true) },
                vpnActive = currentSnapshot.connectivity.vpnActive.takeIf { fresh },
                charging = currentSnapshot.power.charging.takeIf { fresh && currentSnapshot.power.batteryPercent != null },
            )
            if (candidate != current) { candidate = current; candidateSince = now }
            if (now - candidateSince >= 30_000) {
                val alert = CompanionAlertPolicy.decide(previous, current, preferences, now)
                if (alert != null && context.canPostCompanionNotifications()) {
                    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                        NotificationChannel("nomad_companion", "Nomad Companion", NotificationManager.IMPORTANCE_LOW))
                    NotificationManagerCompat.from(context).notify(9401,
                        NotificationCompat.Builder(context, "nomad_companion")
                            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(alert.title).setContentText(alert.body)
                            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true)
                            .setPriority(NotificationCompat.PRIORITY_LOW).build())
                    viewModel.update { it.copy(preferences = it.preferences.copy(lastNotificationAtEpochMillis = now)) }
                }
                previous = current
            }
            delay(30_000)
        }
    }

}

@Composable
fun CompanionDashboardOverview(
    snapshot: DashboardSnapshot,
    onOpenCompanion: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompanionViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val homeTime = runCatching { ZonedDateTime.now(ZoneId.of(data.preferences.homeTimeZoneId)) }.getOrElse { ZonedDateTime.now() }
    val currentZone = snapshot.connectivity.timeZoneId.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: ZoneId.systemDefault().id
    val currentTime = ZonedDateTime.now(ZoneId.of(currentZone))
    val readiness = workReadiness(snapshot)
    NomadCard(modifier = modifier.testTag("companion_dashboard_overview")) {
        NomadSectionHeader("Nomad overview", "Home ${homeTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))} · Current ${currentTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}") {
            NomadStatusBadge(text = when (readiness.isReady) { true -> "Ready"; false -> "Check"; null -> "Unknown" }, tone = when (readiness.isReady) {
                true -> NomadBadgeTone.Good
                false -> NomadBadgeTone.Warning
                null -> NomadBadgeTone.Neutral
            })
        }
        TextButton(onClick = onOpenCompanion) { Text("Open Companion") }
    }
}

/** Compact card intended for DashboardRoute; it has no side effects and reads no network state. */
@Composable
fun CompanionWorkReadinessCard(
    snapshot: DashboardSnapshot,
    onOpenCompanion: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val readiness = workReadiness(snapshot)
    NomadCard(modifier = modifier.testTag("companion_work_readiness_card")) {
        NomadSectionHeader(title = "Work readiness", subtitle = readiness.title) {
            NomadStatusBadge(
                text = when (readiness.isReady) { true -> "Ready"; false -> "Needs attention"; null -> "Unknown" },
                tone = when (readiness.isReady) { true -> NomadBadgeTone.Good; false -> NomadBadgeTone.Warning; null -> NomadBadgeTone.Neutral },
            )
        }
        Text(readiness.detail)
        onOpenCompanion?.let { action -> TextButton(onClick = action) { Text("Open Companion") } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionScreen(
    snapshot: DashboardSnapshot,
    data: NomadCompanionData,
    onUpdate: ((NomadCompanionData) -> NomadCompanionData) -> Unit,
    onAddEssential: (OfflineEssentialKind, String, String, String?) -> Unit,
    onUpdateEssential: (com.iloapps.nomaddashboard.core.model.OfflineEssential) -> Unit,
    onAddPlanner: (String, String, String) -> Unit,
    onUpdatePlanner: (com.iloapps.nomaddashboard.core.model.TimezonePlanner) -> Unit,
    onRecordWorkplace: (String) -> Unit,
    onUpdateVisit: (com.iloapps.nomaddashboard.core.model.WorkplaceVisit) -> Unit,
    onExport: suspend () -> String,
    onPreviewImport: suspend (String) -> CompanionImportPreview,
    onMergeImport: suspend (String) -> CompanionImportPreview,
    storageError: String? = null,
    operationError: String? = null,
    modifier: Modifier = Modifier,
) {
    var essentialTitle by remember { mutableStateOf("") }
    var essentialKind by remember { mutableStateOf(OfflineEssentialKind.NOTE) }
    var essentialContent by remember { mutableStateOf("") }
    var essentialMapUrl by remember { mutableStateOf("") }
    var workplaceLabel by remember { mutableStateOf("") }
    var plannerLabel by remember { mutableStateOf("") }
    var homeZone by remember(data.preferences.homeTimeZoneId) { mutableStateOf(data.preferences.homeTimeZoneId) }
    var clientZone by remember { mutableStateOf("America/New_York") }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<CompanionImportPreview?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onUpdate { current -> current.copy(preferences = current.preferences.copy(quietNotificationsEnabled = granted)) }
        if (!granted) actionError = "Notification permission was not granted."
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val backup = onExport()
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)) { "Could not open export destination." }.use { it.write(backup.toByteArray()) } }
            }.onFailure { actionError = it.message ?: "Could not export Companion backup." }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val readResult = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(::readBoundedUtf8) ?: error("Could not open backup.")
                }
            }
            val serialized = readResult.getOrNull()
            if (serialized != null) {
                pendingImport = serialized
                importPreview = onPreviewImport(serialized)
            } else {
                actionError = readResult.exceptionOrNull()?.message ?: "Could not read Companion backup."
            }
        }
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            NomadCard {
                NomadTopBar(
                    title = "Companion", subtitle = "Local travel and work tools",
                    supportingText = "Pins, diary entries, planning, and backups stay on this device unless you explicitly export them.",
                    badgeText = "Offline first", badgeTone = NomadBadgeTone.Good,
                    trailing = { Icon(Icons.Rounded.People, contentDescription = null) },
                )
                (actionError ?: operationError ?: storageError)?.let { Text(it) }
            }
        }
        item { CompanionWorkReadinessCard(snapshot) }
        item {
            NomadCard {
                NomadSectionHeader("Offline essentials", "Addresses, contacts, notes, and map links you choose.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OfflineEssentialKind.entries.forEach { kind -> TextButton(onClick = { essentialKind = kind }) { Text(kind.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) } }
                }
                OutlinedTextField(essentialTitle, { essentialTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(essentialContent, { essentialContent = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(essentialMapUrl, { essentialMapUrl = it }, label = { Text("Maps link (optional)") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = essentialTitle.isNotBlank() && essentialContent.isNotBlank(), onClick = { onAddEssential(essentialKind, essentialTitle, essentialContent, essentialMapUrl); essentialTitle = ""; essentialContent = ""; essentialMapUrl = "" }) {
                    Icon(Icons.Rounded.Add, null); Text("Save essential")
                }
                data.essentials.forEach { item ->
                    EssentialRow(item, onUpdateEssential, uriHandler::openUri)
                }
            }
        }
        item {
            NomadCard {
                NomadSectionHeader("Workplace diary", "Opt in, then save a current foreground observation when you choose. Place labels stay editable.")
                ToggleRow("Save workplace observations", data.preferences.workplaceDiaryEnabled) { checked -> onUpdate { it.copy(preferences = it.preferences.copy(workplaceDiaryEnabled = checked)) } }
                ToggleRow("Retain connection name", data.preferences.retainConnectionName) { checked -> onUpdate { it.copy(preferences = it.preferences.copy(retainConnectionName = checked)) } }
                ToggleRow("Retain location-based Maps link", data.preferences.retainWorkplaceMapLink) { checked -> onUpdate { it.copy(preferences = it.preferences.copy(retainWorkplaceMapLink = checked)) } }
                if (data.preferences.workplaceDiaryEnabled) {
                    OutlinedTextField(workplaceLabel, { workplaceLabel = it }, label = { Text("Place label you confirm") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onRecordWorkplace(workplaceLabel); workplaceLabel = "" }) { Text("Save current observation") }
                }
                data.workplaceVisits.sortedByDescending { it.observedAtEpochMillis }.take(8).forEach { visit ->
                    WorkplaceRow(visit, onUpdateVisit, uriHandler::openUri)
                }
            }
        }
        item {
            NomadCard {
                NomadSectionHeader("Timezone planner", "IANA zones calculate overlap offline and remain DST aware.")
                OutlinedTextField(plannerLabel, { plannerLabel = it }, label = { Text("Client or project") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(homeZone, { homeZone = it }, label = { Text("Home IANA zone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(clientZone, { clientZone = it }, label = { Text("Client IANA zone") }, modifier = Modifier.fillMaxWidth())
                Text("Default working hours: 09:00–17:00 in each zone.")
                Button(onClick = { onAddPlanner(plannerLabel, homeZone, clientZone); plannerLabel = "" }) { Text("Save planner") }
                data.planners.forEach { planner ->
                    val overlap = TimezoneOverlapPlanner.nextOverlap(planner)
                    PlannerRow(planner, overlap?.homeLabel, onUpdatePlanner)
                }
            }
        }
        item {
            NomadCard {
                NomadSectionHeader("Quiet contextual notifications", "Foreground observations only; no service or polling is added.")
                ToggleRow("Allow quiet notifications", data.preferences.quietNotificationsEnabled) { checked ->
                    if (checked && !context.canPostCompanionNotifications() && Build.VERSION.SDK_INT >= 33) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onUpdate { it.copy(preferences = it.preferences.copy(quietNotificationsEnabled = checked)) }
                    }
                }
                Text("Unknown or stale readings never create a failure alert. Android notification permission is checked when an alert is about to be posted.")
            }
        }
        item {
            NomadCard {
                NomadSectionHeader("Portable backup", "Versioned JSON with an explicit preview and merge. Credentials and dashboard history are excluded.")
                Button(onClick = { exportLauncher.launch("nomad-companion-backup.json") }) { Icon(Icons.Rounded.ContentCopy, null); Text("Export backup") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Choose backup") }
                    Button(onClick = { scope.launch { pendingImport?.let { importPreview = onMergeImport(it) } } }, enabled = importPreview?.accepted == true && pendingImport != null) { Text("Merge preview") }
                }
                importPreview?.let { preview -> Text(preview.message + " Add: ${preview.essentialsToAdd + preview.visitsToAdd + preview.plannersToAdd}; conflicts: ${preview.conflicts}.") }
            }
        }
    }
}

@Composable private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Switch(checked, onCheckedChange) }
}

@Composable private fun WorkplaceRow(visit: com.iloapps.nomaddashboard.core.model.WorkplaceVisit, onUpdate: (com.iloapps.nomaddashboard.core.model.WorkplaceVisit) -> Unit, openUri: (String) -> Unit) {
    var label by remember(visit.id, visit.placeLabel) { mutableStateOf(visit.placeLabel) }
    Column {
        OutlinedTextField(label, { label = it }, label = { Text("Correct place label") }, modifier = Modifier.fillMaxWidth())
        Text("${visit.internetState} · ${if (visit.vpnActive) "VPN" else "No VPN"} · ${visit.powerPercent?.let { "$it%" } ?: "power unknown"}")
        Row { TextButton(onClick = { onUpdate(visit.copy(placeLabel = label, placeLabelConfirmed = true)) }) { Text("Save label") }; visit.mapsUrl?.let { TextButton(onClick = { openUri(it) }) { Text("Maps") } } }
    }
}

@Composable private fun EssentialRow(item: com.iloapps.nomaddashboard.core.model.OfflineEssential, onUpdate: (com.iloapps.nomaddashboard.core.model.OfflineEssential) -> Unit, openUri: (String) -> Unit) {
    var title by remember(item.id, item.title) { mutableStateOf(item.title) }
    var content by remember(item.id, item.content) { mutableStateOf(item.content) }
    var mapUrl by remember(item.id, item.mapUrl) { mutableStateOf(item.mapUrl.orEmpty()) }
    Column {
        OutlinedTextField(title, { title = it }, label = { Text(item.kind.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(content, { content = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mapUrl, { mapUrl = it }, label = { Text("Maps link") }, modifier = Modifier.fillMaxWidth())
        Row { TextButton(onClick = { onUpdate(item.copy(title = title.trim(), content = content.trim(), mapUrl = mapUrl.trim().takeIf { it.isNotBlank() })) }) { Text("Save") }; item.mapUrl?.let { TextButton(onClick = { openUri(it) }) { Text("Map") } } }
    }
}

@Composable private fun PlannerRow(planner: com.iloapps.nomaddashboard.core.model.TimezonePlanner, overlap: String?, onUpdate: (com.iloapps.nomaddashboard.core.model.TimezonePlanner) -> Unit) {
    var label by remember(planner.id, planner.label) { mutableStateOf(planner.label) }
    var home by remember(planner.id, planner.homeZoneId) { mutableStateOf(planner.homeZoneId) }
    var client by remember(planner.id, planner.clientZoneId) { mutableStateOf(planner.clientZoneId) }
    var homeStart by remember(planner.id, planner.homeStartMinute) { mutableStateOf(formatPlannerMinute(planner.homeStartMinute)) }
    var homeEnd by remember(planner.id, planner.homeEndMinute) { mutableStateOf(formatPlannerMinute(planner.homeEndMinute)) }
    var clientStart by remember(planner.id, planner.clientStartMinute) { mutableStateOf(formatPlannerMinute(planner.clientStartMinute)) }
    var clientEnd by remember(planner.id, planner.clientEndMinute) { mutableStateOf(formatPlannerMinute(planner.clientEndMinute)) }
    var validationError by remember { mutableStateOf<String?>(null) }
    Column {
        OutlinedTextField(label, { label = it }, label = { Text("Planner label") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(home, { home = it }, label = { Text("Home IANA zone") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(client, { client = it }, label = { Text("Client IANA zone") }, modifier = Modifier.fillMaxWidth())
        Text("Working hours (HH:mm, same-day range)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(homeStart, { homeStart = it }, label = { Text("Home start") }, modifier = Modifier.weight(1f))
            OutlinedTextField(homeEnd, { homeEnd = it }, label = { Text("Home end") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(clientStart, { clientStart = it }, label = { Text("Client start") }, modifier = Modifier.weight(1f))
            OutlinedTextField(clientEnd, { clientEnd = it }, label = { Text("Client end") }, modifier = Modifier.weight(1f))
        }
        Text(overlap ?: "No valid overlap")
        validationError?.let { Text(it) }
        TextButton(onClick = {
            val values = listOf(homeStart, homeEnd, clientStart, clientEnd).map(::parsePlannerMinute)
            if (values.any { it == null } || values[0]!! >= values[1]!! || values[2]!! >= values[3]!!) {
                validationError = "Use HH:mm and an end time later than its same-day start time."
            } else {
                validationError = null
                onUpdate(planner.copy(label = label.trim(), homeZoneId = home.trim(), clientZoneId = client.trim(), homeStartMinute = values[0]!!, homeEndMinute = values[1]!!, clientStartMinute = values[2]!!, clientEndMinute = values[3]!!))
            }
        }) { Text("Save planner") }
    }
}

private fun formatPlannerMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)

private fun parsePlannerMinute(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
}

private fun Context.canPostCompanionNotifications(): Boolean = Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun readBoundedUtf8(stream: java.io.InputStream): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        require(output.size() + read <= 1_000_000) { "Backup is larger than 1 MB." }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}
