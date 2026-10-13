package com.iloapps.nomaddashboard.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme

@Preview(name = "Status tones", showBackground = true)
@Preview(name = "Status tones dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusTonesPreview() {
    NomadPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NomadStatusBadge(text = "Good", tone = NomadBadgeTone.Good)
            NomadStatusBadge(text = "Caution", tone = NomadBadgeTone.Caution)
            NomadStatusBadge(text = "Attention", tone = NomadBadgeTone.Attention)
            NomadStatusBadge(text = "Neutral", tone = NomadBadgeTone.Neutral)
        }
        NomadUsageBar(fraction = 0.42f, tone = NomadBadgeTone.Good)
        NomadUsageBar(fraction = 0.85f, tone = NomadBadgeTone.Caution)
        NomadUsageBar(fraction = 1f, tone = NomadBadgeTone.Attention)
    }
}

@Preview(name = "Inline action", showBackground = true)
@Preview(name = "Inline action dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InlineActionPreview() {
    NomadPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NomadInlineAction(label = "Refresh", onClick = {})
            NomadInlineAction(label = "Settings", onClick = {}, icon = Icons.Rounded.Settings)
            NomadInlineAction(label = "Disabled", onClick = {}, enabled = false)
        }
    }
}

@Preview(name = "Metric value and sparkline", showBackground = true)
@Preview(name = "Metric value and sparkline dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MetricValuePreview() {
    NomadPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(modifier = Modifier.padding(end = 8.dp)) {
                NomadMetricValue(number = "96.0", unit = "Mbps")
                NomadSparkline(
                    values = listOf(80.0, 92.0, 88.0, 96.0, 90.0, 99.0, 96.0),
                    spanLabel = "Last 30 min",
                    formatValue = { "%.0f Mbps".format(it) },
                )
            }
            Column {
                NomadMetricValue(number = "1,429 €", unit = "/L")
                NomadSparkline(
                    values = listOf(28.0, 28.0, 28.0),
                    spanLabel = "Last 30 min",
                    formatValue = { "%.0f ms".format(it) },
                    tone = NomadBadgeTone.Good,
                )
            }
        }
    }
}

@Preview(name = "Collapsible card", showBackground = true)
@Preview(name = "Collapsible card dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CollapsibleCardPreview() {
    NomadPreviewSurface {
        var collapsed by remember { mutableStateOf(false) }
        NomadCollapsibleCard(
            title = "Connectivity",
            summary = "28 ms · 96 ↓ 19 ↑",
            collapsed = collapsed,
            onCollapsedChange = { collapsed = it },
            subtitle = "Nomad Hub · Wi-Fi",
            collapsedBadge = "Stable" to NomadBadgeTone.Good,
            actions = { NomadStatusBadge(text = "Stable", tone = NomadBadgeTone.Good) },
        ) {
            Text("Card content", style = MaterialTheme.typography.bodyMedium)
        }
        NomadCollapsibleCard(
            title = "Power",
            summary = "72% · 3h 28m",
            collapsed = true,
            onCollapsedChange = {},
            collapsedBadge = "On battery" to NomadBadgeTone.Caution,
        ) {}
    }
}

@Composable
private fun NomadPreviewSurface(content: @Composable () -> Unit) {
    NomadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}
