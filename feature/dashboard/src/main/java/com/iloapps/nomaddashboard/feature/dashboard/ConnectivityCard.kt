package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.iloapps.nomaddashboard.core.travel.cards.ConnectivityCardPresentation
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthEvaluator

/**
 * Down, Up and Latency in one row, each with a sparkline from the retained samples; the latency is
 * tinted by the network health. One caption carries the jitter.
 */
@Composable
internal fun ConnectivitySectionCard(
    state: DashboardUiState,
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val connectivity = state.snapshot.connectivity
    val networkLevel = remember(connectivity, state.snapshot.power) {
        DashboardHealthEvaluator.evaluate(connectivity, state.snapshot.power).network.level
    }
    val presentation = remember(connectivity, networkLevel) {
        ConnectivityCardPresentation.of(connectivity, networkLevel, probeHost = connectivity.latencyProbeHost)
    }
    val statusBadge = connectivity.internetState.label to CardTone.of(networkLevel).badgeTone()

    DashboardCard(
        card = DashboardCardId.CONNECTIVITY,
        frame = frame,
        modifier = Modifier.testTag("dashboard_connectivity_card"),
        subtitle = connectivity.wifiName?.trim()?.takeIf(String::isNotEmpty),
        collapsedBadge = statusBadge,
        actions = { NomadStatusBadge(text = statusBadge.first, tone = statusBadge.second) },
    ) {
        AdaptiveMetricRow(
            items = listOf(
                { TrendMetricBlock(presentation.download, formatTrendValue = ::formatThroughputSample) },
                { TrendMetricBlock(presentation.upload, formatTrendValue = ::formatThroughputSample) },
                { TrendMetricBlock(presentation.latency, formatTrendValue = ::formatLatencySample) },
            ),
        )
        presentation.jitterCaption?.let { caption ->
            Text(
                text = caption,
                modifier = Modifier.testTag("connectivity_jitter_caption"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

private fun formatThroughputSample(value: Double): String = TravelFormatters.megabitsPerSecond(value).text

private fun formatLatencySample(value: Double): String = TravelFormatters.latency(value).text
