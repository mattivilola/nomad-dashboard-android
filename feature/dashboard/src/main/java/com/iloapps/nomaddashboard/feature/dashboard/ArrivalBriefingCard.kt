package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCollapsibleCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadInlineAction
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.designsystem.theme.nomadStatusColors
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingPresentation
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingPresentation.Kind
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingPresentation.Tone

internal const val ArrivalCardTag = "dashboard_arrival_card"
internal const val ArrivalToggleTag = "dashboard_arrival_toggle"
internal const val ArrivalAcknowledgeTag = "dashboard_arrival_acknowledge"
internal const val ArrivalSettingsTag = "dashboard_arrival_settings"

internal fun arrivalRowTag(id: String): String = "dashboard_arrival_row_$id"

/**
 * The one-time "Welcome to Morocco" card shown on arriving in a new country. It folds to one line and
 * is dismissed with "Got it"; briefings can be turned off from the card's own link.
 *
 * @param onAcknowledge Hides the briefing for the given country code until the next border.
 * @param onOpenSettings Opens the setting that turns arrival briefings off.
 */
@Composable
internal fun ArrivalBriefingCard(
    presentation: ArrivalBriefingPresentation,
    onAcknowledge: (countryCode: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A new country is a new briefing, which starts unfolded.
    var collapsed by rememberSaveable(presentation.countryCode) { mutableStateOf(false) }
    NomadCollapsibleCard(
        title = presentation.title,
        summary = presentation.collapsedSummary,
        collapsed = collapsed,
        onCollapsedChange = { collapsed = it },
        modifier = modifier.testTag(ArrivalCardTag),
        subtitle = presentation.subtitle,
        toggleModifier = Modifier.testTag(ArrivalToggleTag),
        actions = {
            NomadInlineAction(
                label = ArrivalBriefingPresentation.ACKNOWLEDGE_TITLE,
                onClick = { onAcknowledge(presentation.countryCode) },
                modifier = Modifier.testTag(ArrivalAcknowledgeTag),
            )
        },
    ) {
        presentation.rows.forEach { row -> ArrivalBriefingRow(row) }
        NomadInlineAction(
            label = "Turn off arrival briefings",
            icon = Icons.Rounded.Settings,
            onClick = onOpenSettings,
            modifier = Modifier.testTag(ArrivalSettingsTag),
        )
    }
}

@Composable
private fun ArrivalBriefingRow(row: ArrivalBriefingPresentation.Row) {
    val colors = nomadStatusColors()
    val tone = when (row.tone) {
        Tone.NEUTRAL -> null
        Tone.CAUTION -> colors.caution
        Tone.ATTENTION -> colors.attention
    }
    val textColor: Color = tone?.text ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(arrivalRowTag(row.id))
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = row.kind.icon(),
            contentDescription = null,
            tint = tone?.text ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                row.flag?.let { NomadStatusBadge(text = it, tone = NomadBadgeTone.Neutral) }
            }
            Selectable(row.isSelectable) {
                Text(
                    text = row.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
            }
            row.detail?.let {
                Selectable(row.isSelectable) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

/** Phone numbers can be selected and copied; everything else is plain text. */
@Composable
private fun Selectable(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) SelectionContainer(content = content) else content()
}

private fun Kind.icon(): ImageVector =
    when (this) {
        Kind.TIME -> Icons.Rounded.AccessTime
        Kind.EMERGENCY -> Icons.Rounded.Phone
        Kind.EMBASSY -> Icons.Rounded.AccountBalance
        Kind.ROAD -> Icons.Rounded.DirectionsCar
        Kind.POWER -> Icons.Rounded.Power
        Kind.MONEY -> Icons.Rounded.Payments
        Kind.HOLIDAY -> Icons.Rounded.Event
        Kind.ADVISORY -> Icons.Rounded.WarningAmber
        Kind.RHYTHM -> Icons.Rounded.EventRepeat
    }
