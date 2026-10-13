package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.theme.nomadStatusColors
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.TimeTrackingFocusLossPerInterruption
import com.iloapps.nomaddashboard.core.model.isAutomaticallyTracked
import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.iloapps.nomaddashboard.core.travel.cards.TimeTrackingAllocationPresentation
import com.iloapps.nomaddashboard.core.travel.cards.TimeTrackingAllocationPresentation.SegmentKind
import com.iloapps.nomaddashboard.core.travel.cards.TimeTrackingCardPresentation
import com.iloapps.nomaddashboard.core.travel.cards.TrendMetricPresentation
import com.iloapps.nomaddashboard.core.travel.format.FormattedMetric
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/** How often the card re-reads the clock while time is being captured: the figures are whole minutes. */
private const val CaptureTickMillis = 15_000L

/** How often the interruption control cools down; it fades over 23 minutes, so this is smooth enough. */
private const val CooldownTickMillis = 10_000L

/**
 * Today, Focus and Pending, an allocation bar by project with its legend, and one interruption control
 * with the focus it has cost. Pausing or resuming the capture and allocating the pending time stay in
 * the card; the dashboard header carries the same allocation as a shortcut.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeTrackingSectionCard(
    trackingState: DashboardTimeTrackingUiState,
    hasNotificationPermission: Boolean,
    autoWindowLabel: String,
    interruptionPulseKey: Int,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onReportInterruption: () -> Unit,
    onAllocateTrackedTime: (java.util.UUID) -> Unit,
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val isRunning = trackingState.activeEntry != null
    val now = rememberDashboardTickerInstant(enabled = isRunning, intervalMillis = CaptureTickMillis)
    val bufferedDuration = dashboardBufferedDuration(trackingState, now)
    val presentation = remember(trackingState.report, bufferedDuration.toMinutes()) {
        TimeTrackingCardPresentation.of(trackingState.report, bufferedDuration)
    }
    val statusBadge = dashboardTrackingStatus(trackingState) to if (isRunning) NomadBadgeTone.Good else NomadBadgeTone.Neutral

    DashboardCard(
        card = DashboardCardId.TIME_TRACKING,
        frame = frame,
        modifier = Modifier.testTag("dashboard_time_tracking_card"),
        collapsedBadge = statusBadge,
        actions = { NomadStatusBadge(text = statusBadge.first, tone = statusBadge.second) },
    ) {
        AdaptiveMetricRow(
            items = listOf(
                { TrendMetricBlock(TrendMetricPresentation("Today", FormattedMetric(presentation.today))) },
                { TrendMetricBlock(TrendMetricPresentation("Focus", FormattedMetric(presentation.focus))) },
                { TrendMetricBlock(TrendMetricPresentation("Pending", FormattedMetric(presentation.pending))) },
            ),
        )

        TimeTrackingAllocationBar(allocation = presentation.allocation)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DashboardInterruptionControl(
                count = presentation.interruptionCount,
                lastInterruptionAt = trackingState.report.lastInterruptionAt,
                helpText = presentation.interruptionHelp,
                pulseKey = interruptionPulseKey,
                onClick = onReportInterruption,
            )
            presentation.allocation.focusLossCaption?.let { caption ->
                Text(
                    text = caption,
                    modifier = Modifier.testTag("time_tracking_focus_loss"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NomadInlineAction(
                label = if (isRunning) "Pause capture" else "Resume capture",
                icon = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                onClick = if (isRunning) onStopTracking else onStartTracking,
            )
            AllocateAction(
                trackingState = trackingState,
                enabled = isRunning || bufferedDuration.seconds > 0,
                onAllocate = onAllocateTrackedTime,
            )
        }

        Text(
            text = captureStatusLine(trackingState, autoWindowLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
        )
        if (hasNotificationPermission.not()) {
            Text(
                text = "Android 13+ notification permission is needed for the foreground capture notification.",
                style = MaterialTheme.typography.bodySmall,
                color = nomadStatusColors().caution.text,
            )
        }
        trackingState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
            )
        }
    }
}

/** Allocates the captured time to a project; every project is listed in full so no name is cut. */
@Composable
private fun AllocateAction(
    trackingState: DashboardTimeTrackingUiState,
    enabled: Boolean,
    onAllocate: (java.util.UUID) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        NomadInlineAction(
            label = "Allocate",
            icon = Icons.Rounded.Checklist,
            onClick = { menuOpen = true },
            enabled = enabled && trackingState.projects.isNotEmpty(),
            modifier = Modifier.testTag("time_tracking_allocate"),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            trackingState.projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.name) },
                    onClick = {
                        menuOpen = false
                        onAllocate(project.id)
                    },
                    modifier = Modifier.testTag("time_tracking_allocate_item_${project.id}"),
                )
            }
        }
    }
}

private val AllocationBarShape = CircleShape

/** The thin bar of today's time by project, pending time in sand, and the legend under it. */
@Composable
private fun TimeTrackingAllocationBar(allocation: TimeTrackingAllocationPresentation) {
    val colors = nomadStatusColors()
    val ramp = listOf(
        colors.good.fill,
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        colors.good.fill.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
    )
    fun tint(kind: SegmentKind): Color =
        when (kind) {
            SegmentKind.Pending -> colors.caution.fill
            is SegmentKind.Allocated -> ramp[kind.toneIndex % ramp.size]
        }

    Column(
        modifier = Modifier.testTag("time_tracking_allocation"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(AllocationBarShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                .semantics { contentDescription = allocation.barHelpText }
                .testTag("time_tracking_allocation_bar"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            allocation.segments.forEach { segment ->
                Box(
                    modifier = Modifier
                        .weight(segment.fraction.toFloat().coerceAtLeast(MinimumSegmentWeight))
                        .fillMaxSize()
                        .clip(AllocationBarShape)
                        .background(tint(segment.kind)),
                )
            }
        }
        if (allocation.legend.isNotEmpty()) {
            AllocationLegend(allocation = allocation, tint = ::tint)
        }
    }
}

private const val MinimumSegmentWeight = 0.015f

@Composable
private fun AllocationLegend(
    allocation: TimeTrackingAllocationPresentation,
    tint: (SegmentKind) -> Color,
) {
    BoxWithConstraints {
        val columns = if (maxWidth >= 280.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            allocation.legend.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    row.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) { contentDescription = entry.helpText },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(tint(entry.kind)))
                            Text(
                                text = entry.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            )
                            Text(
                                text = entry.durationText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            allocation.hiddenLegendText?.let { more ->
                Text(
                    text = more,
                    modifier = Modifier.testTag("time_tracking_legend_more"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
        }
    }
}

/**
 * The one interruption control: sand at rest, warming to coral right after a report and cooling over the
 * 23 minutes an interruption is assumed to cost, with the day's count beside it.
 */
@Composable
private fun DashboardInterruptionControl(
    count: Int,
    lastInterruptionAt: Instant?,
    helpText: String,
    pulseKey: Int,
    onClick: () -> Unit,
) {
    val colors = nomadStatusColors()
    val cooling = lastInterruptionAt?.plus(TimeTrackingFocusLossPerInterruption)?.isAfter(Instant.now()) == true
    val now = rememberDashboardTickerInstant(enabled = cooling, intervalMillis = CooldownTickMillis)
    val heat = interruptionHeat(lastInterruptionAt, now)
    var flashAlpha by remember { mutableStateOf(0f) }

    LaunchedEffect(pulseKey) {
        if (pulseKey == 0) return@LaunchedEffect
        flashAlpha = 0.9f
        delay(120)
        flashAlpha = 0.34f
        delay(260)
        flashAlpha = 0f
    }

    val shape = RoundedCornerShape(18.dp)
    val borderColor = lerp(colors.caution.fill.copy(alpha = 0.34f), colors.attention.fill.copy(alpha = 0.5f), heat)
    val contentColor = lerp(colors.caution.text, colors.attention.text, heat)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.caution.fill.copy(alpha = 0.18f))
            .background(colors.attention.fill.copy(alpha = 0.4f * heat))
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = helpText }
            .testTag("time_tracking_interruption"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PriorityHigh,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
            Text(
                text = "Report interruption",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = count.toString(),
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.attention.fill.copy(alpha = flashAlpha * 0.5f)),
            )
        }
    }
}

/** 1 right after an interruption, fading to 0 over the time an interruption is assumed to cost. */
internal fun interruptionHeat(lastInterruptionAt: Instant?, now: Instant): Float {
    val last = lastInterruptionAt ?: return 0f
    val elapsedMillis = Duration.between(last, now).toMillis().coerceAtLeast(0)
    val cooldownMillis = TimeTrackingFocusLossPerInterruption.toMillis().toFloat()
    return 1f - (elapsedMillis / cooldownMillis).coerceIn(0f, 1f)
}

@Composable
private fun rememberDashboardTickerInstant(enabled: Boolean, intervalMillis: Long): Instant {
    var now by remember(enabled) { mutableStateOf(Instant.now()) }

    LaunchedEffect(enabled) {
        now = Instant.now()
        while (enabled) {
            delay(intervalMillis)
            now = Instant.now()
        }
    }

    return now
}

internal fun dashboardBufferedDuration(
    trackingState: DashboardTimeTrackingUiState,
    now: Instant,
): Duration {
    val closedDuration = trackingState.pendingEntries.fold(Duration.ZERO) { total, record ->
        val endAt = record.entry.endAt ?: return@fold total
        total + Duration.between(record.entry.startAt, endAt)
    }
    val activeDuration = trackingState.activeEntry?.let { active ->
        Duration.between(active.entry.startAt, now)
    } ?: Duration.ZERO
    return closedDuration + activeDuration
}

private fun dashboardTrackingStatus(
    trackingState: DashboardTimeTrackingUiState,
): String = when {
    trackingState.activeEntry != null -> "Running"
    trackingState.pendingEntries.isNotEmpty() -> "Paused"
    else -> "Ready"
}

/** What the capture is doing, with the window the automatic capture runs in. */
private fun captureStatusLine(trackingState: DashboardTimeTrackingUiState, autoWindowLabel: String): String {
    val status = when {
        trackingState.activeEntry?.entry?.isAutomaticallyTracked() == true -> "Auto capture running"
        trackingState.activeEntry != null -> "Manual capture running"
        trackingState.pendingEntries.isNotEmpty() -> {
            val segments = trackingState.pendingEntries.size
            "$segments segment${if (segments == 1) "" else "s"} waiting for allocation"
        }
        else -> "Ready for the next allocation"
    }
    return "$status · Auto window $autoWindowLabel"
}

internal fun Int.formatClockMinutes(): String = "%02d:%02d".format(this / 60, this % 60)
