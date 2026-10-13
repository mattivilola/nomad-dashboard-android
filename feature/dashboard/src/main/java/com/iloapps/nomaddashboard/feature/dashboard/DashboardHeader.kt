package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.theme.nomadStatusColors
import com.iloapps.nomaddashboard.core.travel.glance.GlanceHeader
import com.iloapps.nomaddashboard.core.travel.glance.GlanceHeader.CaptionLayout
import com.iloapps.nomaddashboard.core.travel.glance.GlanceRefresh
import com.iloapps.nomaddashboard.core.travel.glance.GlanceTimeTracking
import kotlinx.coroutines.delay
import java.util.UUID

internal const val DashboardCheckingHeadline = "Checking device location..."
internal const val DashboardCheckingCaption = "Checking device location before loading location-based cards..."

internal const val TimeTrackingRowTag = "dashboard_time_tracking_row"
internal const val TimeTrackingRowStatusTag = "dashboard_time_tracking_status"
internal const val TimeTrackingRowPrimaryTag = "dashboard_time_tracking_primary"
internal const val TimeTrackingRowInterruptionTag = "dashboard_time_tracking_interruption"
internal const val TimeTrackingRowAllocateTag = "dashboard_time_tracking_allocate"

internal fun timeTrackingAllocateItemTag(entry: GlanceTimeTracking.AllocateEntry, index: Int): String =
    if (entry.isOther) "dashboard_time_tracking_allocate_other" else "dashboard_time_tracking_allocate_$index"

/**
 * The first element of the glance: the place the user is in, a caption that compares the device with
 * the public IP, the refresh button and overflow menu, and "Last refresh 11.30" under them. The
 * time-tracking row (the second element) hangs below it through [timeTrackingRow].
 */
@Composable
internal fun DashboardHeader(
    header: GlanceHeader,
    refresh: GlanceRefresh,
    isCheckingLocation: Boolean,
    onRefresh: () -> Unit,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    timeTrackingRow: @Composable () -> Unit = {},
    belowRow: @Composable () -> Unit = {},
) {
    // Until the first location arrives there is no place to name, so say what is happening instead.
    val isChecking = isCheckingLocation && header.headline == GlanceHeader.FALLBACK_HEADLINE
    Column(
        modifier = modifier
            .testTag("dashboard_top_bar")
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isChecking) DashboardCheckingHeadline else header.headline,
                    modifier = Modifier.testTag("dashboard_header_headline"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isChecking) {
                    Text(
                        text = DashboardCheckingCaption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                } else {
                    HeaderCaption(header)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (refresh.isButtonEnabled) onRefresh() },
                        modifier = Modifier.testTag("dashboard_refresh_action"),
                    ) {
                        if (refresh.isButtonEnabled) {
                            Icon(Icons.Rounded.Refresh, contentDescription = refresh.buttonTitle)
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag("dashboard_refresh_progress"),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    overflowMenu()
                }
                Text(
                    text = refresh.statusText,
                    modifier = Modifier
                        .testTag("dashboard_refresh_status")
                        .padding(end = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
        }
        timeTrackingRow()
        belowRow()
    }
}

// region Caption

private val CaptionIconSize = 14.dp
private val CaptionIconGap = 4.dp
private val CaptionSeparatorGap = 8.dp

/**
 * "region, country · IP city, country", shortened in steps until it fits the width left of the buttons,
 * measured with the real text style so the IP part is never the part that gets cut. When even the
 * shortest variant needs two lines, the device and IP parts stack instead of being ellipsized.
 */
@Composable
private fun HeaderCaption(header: GlanceHeader) {
    if (header.detailVariants.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyMedium
    BoxWithConstraints(
        modifier = Modifier
            .testTag("dashboard_header_location_comparison")
            .fillMaxWidth(),
    ) {
        val availablePx = constraints.maxWidth
        val layout = remember(header, availablePx, style, density, measurer) {
            header.captionLayout { variant ->
                captionWidthPx(variant, measurer, style, density) <= availablePx
            }
        } ?: return@BoxWithConstraints
        val variant = layout.variant

        when (layout) {
            is CaptionLayout.SingleLine -> Row(
                horizontalArrangement = Arrangement.spacedBy(CaptionSeparatorGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                variant.device?.let { CaptionDevicePart(it, style) }
                if (variant.device != null && variant.ip != null) {
                    Text(
                        text = "·",
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
                variant.ip?.let { CaptionIpPart(it, style) }
            }

            is CaptionLayout.Stacked -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                variant.device?.let { CaptionDevicePart(it, style, maxLines = 2) }
                variant.ip?.let { CaptionIpPart(it, style, maxLines = 2) }
            }
        }
    }
}

@Composable
private fun CaptionDevicePart(device: String, style: TextStyle, maxLines: Int = 1) {
    Row(
        modifier = Modifier.testTag("dashboard_header_location_device"),
        horizontalArrangement = Arrangement.spacedBy(CaptionIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.MyLocation,
            contentDescription = "Device location",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.size(CaptionIconSize),
        )
        Text(
            text = device,
            style = style,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CaptionIpPart(ip: String, style: TextStyle, maxLines: Int = 1) {
    Row(
        modifier = Modifier.testTag("dashboard_header_location_ip"),
        horizontalArrangement = Arrangement.spacedBy(CaptionIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = "Public IP location",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.size(CaptionIconSize),
        )
        Text(
            text = ipCaptionText(ip),
            style = style,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "IP Valencia, Spain": the label is semibold so the part reads as the public IP's place. */
private fun ipCaptionText(ip: String): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("IP") }
        append(' ')
        append(ip)
    }

private fun captionWidthPx(
    variant: GlanceHeader.DetailVariant,
    measurer: TextMeasurer,
    style: TextStyle,
    density: Density,
): Float {
    fun textWidth(text: AnnotatedString): Float =
        measurer.measure(text = text, style = style, maxLines = 1, softWrap = false).size.width.toFloat()

    val part = with(density) { (CaptionIconSize + CaptionIconGap).toPx() }
    val gap = with(density) { CaptionSeparatorGap.toPx() }
    var total = 0f
    variant.device?.let { total += part + textWidth(AnnotatedString(it)) }
    variant.ip?.let { total += part + textWidth(ipCaptionText(it)) }
    if (variant.device != null && variant.ip != null) {
        total += 2 * gap + textWidth(AnnotatedString("·"))
    }
    return total
}

// endregion

// region Time tracking row

/**
 * The second element of the glance, shown only while time tracking is on: a status dot with the pending
 * time, one pause or resume control, the interruption counter and the Allocate menu. It wraps rather
 * than truncating, and project names in the menu are never cut.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardTimeTrackingRow(
    tracking: GlanceTimeTracking,
    interruptionPulseKey: Int,
    onPrimaryControl: () -> Unit,
    onReportInterruption: () -> Unit,
    onAllocate: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    FlowRow(
        modifier = modifier
            .testTag(TimeTrackingRowTag)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val centered = Modifier.align(Alignment.CenterVertically)
        TimeTrackingStatus(tracking, centered)
        IconButton(
            onClick = onPrimaryControl,
            modifier = centered.testTag(TimeTrackingRowPrimaryTag),
        ) {
            Icon(
                imageVector = when (tracking.primaryControl) {
                    GlanceTimeTracking.PrimaryControl.PAUSE -> Icons.Rounded.Pause
                    GlanceTimeTracking.PrimaryControl.RESUME, GlanceTimeTracking.PrimaryControl.PLAY -> Icons.Rounded.PlayArrow
                },
                contentDescription = tracking.primaryControl.title,
            )
        }
        TimeTrackingInterruption(
            count = tracking.interruptionCount,
            pulseKey = interruptionPulseKey,
            onClick = onReportInterruption,
            modifier = centered,
        )
        // Allocate takes the rest of the line and sits at its end; it only wraps to a second line when
        // the controls before it already fill this one (large font scale, narrow window).
        Box(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.CenterEnd,
        ) {
            TimeTrackingAllocateMenu(tracking = tracking, onAllocate = onAllocate)
        }
    }
}

@Composable
private fun TimeTrackingStatus(tracking: GlanceTimeTracking, modifier: Modifier = Modifier) {
    val colors = nomadStatusColors()
    val dot = when (tracking.activity) {
        GlanceTimeTracking.Activity.RUNNING -> colors.good.fill
        GlanceTimeTracking.Activity.PAUSED -> colors.caution.fill
        GlanceTimeTracking.Activity.STOPPED -> colors.neutral.fill
    }
    Row(
        modifier = modifier
            .testTag(TimeTrackingRowStatusTag)
            .semantics(mergeDescendants = true) { contentDescription = tracking.accessibilityLabel },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dot, CircleShape),
        )
        Text(
            text = tracking.pendingText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The counter reports an interruption on tap and flashes coral for a moment to confirm it. */
@Composable
private fun TimeTrackingInterruption(count: Int, pulseKey: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(pulseKey) {
        if (pulseKey == 0) return@LaunchedEffect
        flashing = true
        delay(FlashMillis)
        flashing = false
    }
    val flash by animateFloatAsState(targetValue = if (flashing) 1f else 0f, label = "interruptionFlash")
    val attention = nomadStatusColors().attention
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(attention.tint(0.35f * flash)),
    ) {
        NomadInlineAction(
            label = count.toString(),
            icon = Icons.Rounded.PriorityHigh,
            onClick = onClick,
            modifier = Modifier
                .testTag(TimeTrackingRowInterruptionTag)
                .semantics { contentDescription = "Report interruption, $count today" },
        )
    }
}

private const val FlashMillis = 420L

@Composable
private fun TimeTrackingAllocateMenu(
    tracking: GlanceTimeTracking,
    onAllocate: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        NomadInlineAction(
            label = "Allocate",
            icon = Icons.Rounded.ArrowDropDown,
            onClick = { expanded = true },
            modifier = Modifier
                .testTag(TimeTrackingRowAllocateTag)
                .semantics { contentDescription = "Allocate pending time" },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tracking.allocateEntries.forEachIndexed { index, entry ->
                DropdownMenuItem(
                    // No line limit: a project name is never cut off.
                    text = { Text(entry.title) },
                    enabled = tracking.canAllocate,
                    onClick = {
                        expanded = false
                        onAllocate(entry.projectId)
                    },
                    modifier = Modifier.testTag(timeTrackingAllocateItemTag(entry, index)),
                )
            }
        }
    }
}

// endregion
