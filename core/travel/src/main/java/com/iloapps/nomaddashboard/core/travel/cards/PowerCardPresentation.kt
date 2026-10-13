package com.iloapps.nomaddashboard.core.travel.cards

import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.travel.format.FormattedMetric
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.PowerMetricsPresentation
import com.iloapps.nomaddashboard.core.travel.health.PowerState
import com.iloapps.nomaddashboard.core.travel.health.powerState
import java.util.Locale

/** One line of the Power card's "Details" row. */
data class PowerDetail(
    val label: String,
    val value: String,
)

/**
 * The Power card's body: Battery (health-tinted), Drain on battery or Adapter on power, and Time left,
 * To full or Full, with a charge sparkline. The diagnostics that used to fill the card (health, source,
 * temperature, voltage) are [details], shown on request.
 */
data class PowerCardPresentation(
    val battery: TrendMetricPresentation,
    /** Null when there is nothing to show, for example the adapter wattage Android cannot read. */
    val drain: TrendMetricPresentation?,
    val timeLeft: TrendMetricPresentation,
    /**
     * The header badge: "On Battery", "Charging" or "Plugged In". A full battery reads "Plugged In" here
     * because the time-left metric already says "Full".
     */
    val statusText: String,
    /** The power source when the device is plugged in ("AC power"); null on battery, where the badge says it. */
    val subtitle: String?,
    val details: List<PowerDetail>,
) {
    companion object {
        const val BATTERY_ESTIMATING = PowerMetricsPresentation.ESTIMATING

        fun of(power: PowerSnapshot, powerLevel: HealthLevel): PowerCardPresentation {
            val metrics = PowerMetricsPresentation.of(power)
            val state = power.powerState()

            return PowerCardPresentation(
                battery = TrendMetricPresentation(
                    title = "Battery",
                    reading = power.batteryPercent?.let { FormattedMetric("$it%") } ?: FormattedMetric(BATTERY_ESTIMATING),
                    tone = if (power.batteryPercent != null) CardTone.of(powerLevel) else CardTone.NEUTRAL,
                    trend = MetricTrend.of(power.batteryPercentHistory),
                ),
                drain = metrics.drainReading?.let { reading ->
                    TrendMetricPresentation(title = metrics.drainTitle, reading = reading)
                },
                timeLeft = TrendMetricPresentation(
                    title = metrics.timeLeftTitle,
                    reading = FormattedMetric(metrics.timeLeftValue),
                ),
                statusText = if (state == PowerState.CHARGED) "Plugged In" else power.statusLabel,
                subtitle = subtitle(power, state),
                details = details(power, state),
            )
        }

        private fun subtitle(power: PowerSnapshot, state: PowerState): String? {
            if (state == PowerState.BATTERY || state == PowerState.UNKNOWN) return null
            val source = power.powerSourceLabel?.trim()?.takeIf(String::isNotEmpty) ?: return null
            return if (source.equals("Battery", ignoreCase = true)) null else "$source power"
        }

        private fun details(power: PowerSnapshot, state: PowerState): List<PowerDetail> =
            buildList {
                add(PowerDetail("Health", power.batteryHealthSummary))
                add(PowerDetail("Source", power.powerSourceLabel ?: "Unavailable"))
                if (power.powerSaveMode) add(PowerDetail("Battery Saver", "On"))
                // Android reports the current at the battery; while charging it is the charge rate.
                if (state == PowerState.CHARGING) {
                    power.dischargeWatts?.let { add(PowerDetail("Charge rate", "${TravelFormatters.watts(it).text} (estimated)")) }
                }
                power.temperatureCelsius?.let { add(PowerDetail("Temperature", String.format(Locale.ROOT, "%.1f°C", it))) }
                power.voltageVolts?.let { add(PowerDetail("Voltage", String.format(Locale.ROOT, "%.2f V", it))) }
            }
    }
}
