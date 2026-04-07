package com.iloapps.nomaddashboard.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.Coral
import com.iloapps.nomaddashboard.core.designsystem.theme.Sea
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.SummaryTile

enum class NomadBadgeTone {
    Neutral,
    Good,
    Warning,
    Accent,
    Info,
}

@Composable
fun NomadCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun NomadTopBar(
    title: String,
    subtitle: String,
    supportingText: String? = null,
    badgeText: String? = null,
    badgeTone: NomadBadgeTone = NomadBadgeTone.Neutral,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    badgeText?.let {
                        NomadStatusBadge(text = it, tone = badgeTone)
                    }
                }
                supportingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

@Composable
fun NomadSectionHeader(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun NomadSectionClusterHeader(
    title: String,
    subtitle: String,
    badges: List<Pair<String, NomadBadgeTone>> = emptyList(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        if (badges.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                badges.forEach { (text, tone) ->
                    NomadStatusBadge(text = text, tone = tone)
                }
            }
        }
    }
}

@Composable
fun NomadSummaryTile(
    tile: SummaryTile,
    modifier: Modifier = Modifier,
) {
    NomadCard(modifier = modifier) {
        Text(
            text = tile.title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
        NomadStatusBadge(
            text = tile.headline,
            tone = toneForLevel(tile.level),
        )
        Text(
            text = tile.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
        )
    }
}

@Composable
fun NomadStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: NomadBadgeTone = NomadBadgeTone.Neutral,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(badgeContainerColor(tone))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = badgeContentColor(tone),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NomadPill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NomadActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = label, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun NomadMetricBlock(
    label: String,
    value: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
        )
        supportingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
fun NomadChartShell(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun badgeContainerColor(tone: NomadBadgeTone): Color = when (tone) {
    NomadBadgeTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    NomadBadgeTone.Good -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    NomadBadgeTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
    NomadBadgeTone.Accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    NomadBadgeTone.Info -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
}

@Composable
private fun badgeContentColor(tone: NomadBadgeTone): Color = when (tone) {
    NomadBadgeTone.Neutral,
    NomadBadgeTone.Info,
    -> MaterialTheme.colorScheme.onSurface
    NomadBadgeTone.Good,
    NomadBadgeTone.Accent,
    -> MaterialTheme.colorScheme.primary
    NomadBadgeTone.Warning -> MaterialTheme.colorScheme.secondary
}

private fun toneForLevel(level: SignalLevel): NomadBadgeTone = when (level) {
    SignalLevel.GOOD -> NomadBadgeTone.Good
    SignalLevel.WARNING,
    SignalLevel.BAD,
    -> NomadBadgeTone.Warning
    SignalLevel.NEUTRAL -> NomadBadgeTone.Neutral
}

private fun levelColor(level: SignalLevel): Color = when (level) {
    SignalLevel.GOOD -> Sea
    SignalLevel.WARNING -> Coral
    SignalLevel.BAD -> Coral
    SignalLevel.NEUTRAL -> Color(0xFF9CA3AF)
}
