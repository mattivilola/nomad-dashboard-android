package com.iloapps.nomaddashboard.feature.dashboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCollapsibleCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.theme.nomadStatusColors
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import com.iloapps.nomaddashboard.core.model.SectionRefreshState
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.travel.glance.displayTitle
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardSummaryBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What the dashboard tells a card about its frame. A card draws itself through [DashboardCard], which
 * applies this: whether the card is folded, the one-line summary it shows then, and the "last updated"
 * row with its refresh action.
 *
 * The default is an expanded card without a refresh row, which is how a card renders on its own.
 */
internal data class DashboardCardFrame(
    /** The collapsed one-line summary, from `DashboardCardSummaryBuilder`. */
    val summary: String = "",
    val isCollapsed: Boolean = false,
    val onCollapsedChange: (Boolean) -> Unit = {},
    val refresh: DashboardCardRefresh? = null,
)

/** The per-card "Updated 12 Oct 11:30 · Refresh" row, shown at the top of an expanded card. */
internal data class DashboardCardRefresh(
    val state: SectionRefreshState?,
    val onRefresh: (DashboardCardId) -> Unit,
)

internal fun dashboardCardToggleTag(card: DashboardCardId): String = "dashboard_card_toggle_${card.name}"

/**
 * The card chrome every dashboard card renders through: a [NomadCollapsibleCard] titled with the card's
 * display title. [actions] is the header slot that keeps the card's refresh indicator and buttons.
 */
@Composable
internal fun DashboardCard(
    card: DashboardCardId,
    frame: DashboardCardFrame,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badges: List<Pair<String, NomadBadgeTone>> = emptyList(),
    collapsedBadge: Pair<String, NomadBadgeTone>? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    NomadCollapsibleCard(
        title = card.displayTitle,
        summary = frame.summary,
        collapsed = frame.isCollapsed,
        onCollapsedChange = frame.onCollapsedChange,
        modifier = modifier,
        subtitle = subtitle,
        badges = badges,
        collapsedBadge = collapsedBadge,
        toggleModifier = Modifier.testTag(dashboardCardToggleTag(card)),
        actions = actions,
    ) {
        frame.refresh?.let { DashboardCardRefreshRow(card = card, refresh = it) }
        content()
    }
}

@Composable
private fun DashboardCardRefreshRow(
    card: DashboardCardId,
    refresh: DashboardCardRefresh,
) {
    val state = refresh.state
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val timestamp = state?.lastSuccessAt?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(timestamp?.let { "Updated $it" }, state?.locationLabel?.takeIf(String::isNotBlank))
                    .joinToString(" · ").ifBlank { "No saved update" },
                style = MaterialTheme.typography.labelSmall,
            )
            state?.message?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        NomadInlineAction(
            label = if (state?.isRefreshing == true) "Updating…" else "Refresh",
            onClick = { refresh.onRefresh(card) },
            enabled = state?.isRefreshing != true,
            modifier = Modifier.testTag("card_refresh_${card.name}"),
        )
    }
}

internal fun toneForLevel(level: SignalLevel): NomadBadgeTone = when (level) {
    SignalLevel.GOOD -> NomadBadgeTone.Good
    SignalLevel.WARNING,
    SignalLevel.BAD,
    -> NomadBadgeTone.Warning
    SignalLevel.NEUTRAL -> NomadBadgeTone.Info
}

internal fun List<MetricHistoryPoint>.values(): List<Double> = map(MetricHistoryPoint::value)

@Composable
internal fun summaryToneColor(tone: NomadBadgeTone) = when (tone) {
    NomadBadgeTone.Good -> MaterialTheme.colorScheme.primary
    NomadBadgeTone.Warning -> MaterialTheme.colorScheme.secondary
    NomadBadgeTone.Caution -> nomadStatusColors().caution.fill
    NomadBadgeTone.Attention -> nomadStatusColors().attention.fill
    NomadBadgeTone.Accent -> MaterialTheme.colorScheme.tertiary
    NomadBadgeTone.Info -> MaterialTheme.colorScheme.surfaceTint
    NomadBadgeTone.Neutral -> MaterialTheme.colorScheme.outline
}

@Composable
internal fun SectionRefreshIndicator(tag: String) {
    CircularProgressIndicator(
        modifier = Modifier
            .size(16.dp)
            .testTag(tag),
        strokeWidth = 2.dp,
    )
}

internal fun Context.openMapLocation(
    latitude: Double,
    longitude: Double,
    label: String,
) {
    val geoUri = Uri.parse(
        "geo:0,0?q=${latitude},${longitude}(${Uri.encode(label)})",
    )
    val browserUri = Uri.parse(
        "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude",
    )

    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        when {
            geoIntent.resolveActivity(packageManager) != null -> startActivity(geoIntent)
            browserIntent.resolveActivity(packageManager) != null -> startActivity(browserIntent)
        }
    } catch (_: ActivityNotFoundException) {
        // Ignore missing map handlers and leave the user on the dashboard.
    }
}

internal fun Instant.formatDashboardTimestamp(): String =
    DateTimeFormatter.ofPattern("HH.mm")
        .withZone(ZoneId.systemDefault())
        .format(this)
