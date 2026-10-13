package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.component.statusColor
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.StartupLocationBootstrapState
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.iloapps.nomaddashboard.core.travel.cards.TravelAlertRow
import com.iloapps.nomaddashboard.core.travel.cards.TravelAlertsCardPresentation
import com.iloapps.nomaddashboard.core.travel.cards.TravelAlertsNotice

/**
 * The highest alert level as the badge, stated once; staleness and unavailable sources as captions
 * ("Updated 12 Oct · may be outdated"); one compact row per signal with its summary, detail, source and
 * "More details" link.
 */
@Composable
internal fun TravelAlertsSectionCard(
    snapshot: TravelAlertsSnapshot,
    startupLocation: StartupLocationBootstrapState = StartupLocationBootstrapState(),
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val presentation = remember(snapshot) { TravelAlertsCardPresentation.of(snapshot) }
    val statusBadge = presentation.badge.text to presentation.badge.tone.badgeTone()

    DashboardCard(
        card = DashboardCardId.TRAVEL_ALERTS,
        frame = frame,
        modifier = modifier.testTag(TravelAlertsCardTag),
        subtitle = TravelAlertsCardPresentation.subtitle(snapshot, startupLocation.isChecking),
        collapsedBadge = statusBadge,
        actions = {
            if (snapshot.isRefreshing) {
                SectionRefreshIndicator(tag = "dashboard_travel_alerts_refresh_indicator")
            }
            NomadStatusBadge(text = statusBadge.first, tone = statusBadge.second)
        },
    ) {
        presentation.notices.forEach { notice -> TravelAlertsNoticeLine(notice) }

        if (presentation.isOff) {
            Text(
                text = TravelAlertsCardPresentation.OFF_MESSAGE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            onOpenSettings?.let { open ->
                NomadInlineAction(label = "Open Settings", icon = Icons.Rounded.Settings, onClick = open)
            }
        }

        presentation.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            TravelAlertRowContent(
                row = row,
                showsChip = presentation.showsChip(row),
                modifier = Modifier.testTag("travel-alert-row-${row.kind.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun TravelAlertsNoticeLine(notice: TravelAlertsNotice) {
    val caution = CardTone.CAUTION.badgeTone().statusColor()
    Row(
        modifier = Modifier.fillMaxWidth().testTag("travel_alerts_notice"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (notice.kind) {
                TravelAlertsNotice.Kind.STALE -> Icons.Rounded.Schedule
                TravelAlertsNotice.Kind.LIMITED -> Icons.Rounded.WarningAmber
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = caution.text,
        )
        Text(
            text = notice.text,
            style = MaterialTheme.typography.bodySmall,
            color = caution.text,
        )
    }
}

@Composable
private fun TravelAlertRowContent(
    row: TravelAlertRow,
    showsChip: Boolean,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val tone = row.tone.badgeTone()
    val impactColor = if (row.tone == CardTone.NEUTRAL) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    } else {
        tone.statusColor().text
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (showsChip) NomadStatusBadge(text = row.statusLabel, tone = tone)
        }
        Text(
            text = row.impactText.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = impactColor,
        )
        Text(
            text = row.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
        )
        row.detailSummary?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = row.metadataText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
            row.sourceUrl?.let { url ->
                NomadInlineAction(
                    label = "More details",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { uriHandler.openUri(url) },
                )
            }
        }
    }
}

/** The one-word label the glance chips use for the alerts card: "Alerts caution", "Alerts stale". */
internal fun travelAlertsCompactLabel(
    snapshot: TravelAlertsSnapshot,
    @Suppress("UNUSED_PARAMETER") startupLocation: StartupLocationBootstrapState,
): String = "Alerts ${TravelAlertsCardPresentation.of(snapshot).badge.text.lowercase()}"

internal const val TravelAlertsCardTag = "travel-alerts-card"
