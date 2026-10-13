package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadStatusBadge
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.iloapps.nomaddashboard.core.travel.cards.PowerCardPresentation
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthEvaluator

/**
 * Battery (tinted by the power health), Drain on battery, Time left, To full or Full, with a sparkline
 * of the charge. The diagnostics (health, source, temperature, voltage) sit in the "Details" row.
 */
@Composable
internal fun PowerSectionCard(
    state: DashboardUiState,
    frame: DashboardCardFrame = DashboardCardFrame(),
) {
    val power = state.snapshot.power
    val powerLevel = remember(state.snapshot.connectivity, power) {
        DashboardHealthEvaluator.evaluate(state.snapshot.connectivity, power).power.level
    }
    val presentation = remember(power, powerLevel) { PowerCardPresentation.of(power, powerLevel) }
    val statusBadge = presentation.statusText to CardTone.of(powerLevel).badgeTone()
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }

    DashboardCard(
        card = DashboardCardId.POWER,
        frame = frame,
        modifier = Modifier.testTag("dashboard_power_card"),
        subtitle = presentation.subtitle,
        collapsedBadge = statusBadge,
        actions = { NomadStatusBadge(text = statusBadge.first, tone = statusBadge.second) },
    ) {
        AdaptiveMetricRow(
            items = listOfNotNull<@Composable () -> Unit>(
                { TrendMetricBlock(presentation.battery, formatTrendValue = ::formatChargeSample) },
                presentation.drain?.let { drain -> { TrendMetricBlock(drain) } },
                { TrendMetricBlock(presentation.timeLeft) },
            ),
        )
        CardDetailsToggle(
            label = "Details",
            expanded = detailsExpanded,
            onToggle = { detailsExpanded = !detailsExpanded },
            modifier = Modifier.testTag("power_details_toggle"),
        )
        if (detailsExpanded) {
            Column(
                modifier = Modifier.testTag("power_details"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presentation.details.forEach { detail ->
                    CardDetailRow(label = detail.label, value = detail.value)
                }
            }
        }
    }
}

private fun formatChargeSample(value: Double): String = TravelFormatters.percentage(value)
