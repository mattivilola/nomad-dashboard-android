package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricValue
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSparkline
import com.iloapps.nomaddashboard.core.designsystem.component.statusColor
import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.iloapps.nomaddashboard.core.travel.cards.TrendMetricPresentation

/** The design-system tone a [CardTone] is drawn with. */
internal fun CardTone.badgeTone(): NomadBadgeTone =
    when (this) {
        CardTone.NEUTRAL -> NomadBadgeTone.Neutral
        CardTone.GOOD -> NomadBadgeTone.Good
        CardTone.CAUTION -> NomadBadgeTone.Caution
        CardTone.ATTENTION -> NomadBadgeTone.Attention
    }

/** The narrowest a metric column gets before the row wraps onto a second line. */
private val MinimumMetricColumnWidth = 84.dp
private val MetricColumnGap = 12.dp

/**
 * Lays [items] out in one row of equal columns when each keeps at least [MinimumMetricColumnWidth],
 * and in rows of two otherwise: a value is never cut to fit, a metric moves down instead.
 */
@Composable
internal fun AdaptiveMetricRow(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val needed = MinimumMetricColumnWidth * items.size + MetricColumnGap * (items.size - 1)
        val rows = if (maxWidth >= needed) listOf(items) else items.chunked(2)
        val columns = rows.maxOf { it.size }
        Column(verticalArrangement = Arrangement.spacedBy(MetricColumnGap)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MetricColumnGap),
                ) {
                    row.forEach { item ->
                        Column(modifier = Modifier.weight(1f)) { item() }
                    }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One metric of a card: its title, the value with the unit drawn smaller, and a sparkline under it when
 * [formatTrendValue] is given. The value and the line take the metric's status tone.
 */
@Composable
internal fun TrendMetricBlock(
    metric: TrendMetricPresentation,
    modifier: Modifier = Modifier,
    formatTrendValue: ((Double) -> String)? = null,
) {
    val tone = metric.tone.badgeTone()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = metric.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
        )
        NomadMetricValue(
            number = metric.reading.number,
            unit = metric.reading.unit,
            color = if (metric.tone == CardTone.NEUTRAL) Color.Unspecified else tone.statusColor().text,
        )
        if (formatTrendValue != null) {
            NomadSparkline(
                values = metric.trend?.values.orEmpty(),
                spanLabel = metric.trend?.spanLabel.orEmpty(),
                formatValue = formatTrendValue,
                tone = tone,
            )
        }
    }
}

/** A disclosure row ("Details", "Device vs network") with a chevron; [trailing] sits before the chevron. */
@Composable
internal fun CardDetailsToggle(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClickLabel = if (expanded) "Hide $label" else "Show $label", role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        trailing?.invoke()
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

private val DetailLabelWidth: Dp = 72.dp

/**
 * One label and value line: the label in a fixed column, the value beside it wrapping instead of
 * cutting, with an optional [supporting] caption and a [trailing] control (a badge or an action).
 */
@Composable
internal fun CardDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(DetailLabelWidth),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = value, style = valueStyle, fontWeight = FontWeight.SemiBold)
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                )
            }
        }
        trailing?.invoke()
    }
}
