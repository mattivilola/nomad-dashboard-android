package com.iloapps.nomaddashboard.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.Coral
import com.iloapps.nomaddashboard.core.designsystem.theme.Sea
import com.iloapps.nomaddashboard.core.model.SignalLevel
import com.iloapps.nomaddashboard.core.model.SummaryTile

@Composable
fun NomadCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
fun NomadSummaryTile(
    tile: SummaryTile,
    modifier: Modifier = Modifier,
) {
    NomadCard(modifier = modifier) {
        Text(
            text = tile.title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(levelColor(tile.level)),
            )
            Text(
                text = tile.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = tile.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
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
        Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

private fun levelColor(level: SignalLevel): Color = when (level) {
    SignalLevel.GOOD -> Sea
    SignalLevel.WARNING -> Coral
    SignalLevel.BAD -> Coral
    SignalLevel.NEUTRAL -> Color(0xFF9CA3AF)
}
