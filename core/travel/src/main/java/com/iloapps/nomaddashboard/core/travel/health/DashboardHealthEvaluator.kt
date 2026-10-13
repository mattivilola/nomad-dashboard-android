package com.iloapps.nomaddashboard.core.travel.health

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters

/**
 * Turns the connectivity and power readings into the network, power and overall health that the
 * verdict strip and the vital tiles show. Ported from the macOS `DashboardHealthEvaluator`.
 *
 * Not ported, because Android does not expose the signal: the "no active network interface" and
 * "public IP lookup unavailable" cautions. A network the platform has not validated (captive portal)
 * is a caution of its own rather than an endless "Checking conditions". Android's "Battery Saver" stands in for Low Power Mode.
 */
object DashboardHealthEvaluator {
    const val LATENCY_ATTENTION_MS = 120.0
    const val LATENCY_CAUTION_MS = 60.0
    const val JITTER_ATTENTION_MS = 25.0
    const val JITTER_CAUTION_MS = 10.0
    const val WEAK_WIFI_DBM = -70
    const val BATTERY_ATTENTION_PERCENT = 25
    const val BATTERY_ATTENTION_MINUTES = 45
    const val BATTERY_CAUTION_PERCENT = 50
    const val BATTERY_CAUTION_MINUTES = 120
    const val NETWORK_UNVERIFIED_REASON = "Network needs sign-in or isn't verified"

    fun evaluate(connectivity: ConnectivitySnapshot, power: PowerSnapshot): DashboardHealthSummary {
        val network = evaluateNetwork(connectivity)
        val powerHealth = evaluatePower(power)
        return DashboardHealthSummary(
            overall = evaluateOverall(network, powerHealth),
            network = network,
            power = powerHealth,
        )
    }

    private fun evaluateNetwork(connectivity: ConnectivitySnapshot): SectionHealth {
        if (connectivity.isOffline) {
            return SectionHealth("Attention", HealthLevel.ATTENTION, "No network route", HealthSymbol.OFFLINE)
        }

        if (connectivity.internetState == InternetState.CAPTIVE_OR_UNVALIDATED) {
            return SectionHealth("Caution", HealthLevel.CAUTION, NETWORK_UNVERIFIED_REASON, HealthSymbol.NETWORK_UNVERIFIED)
        }

        val latency = connectivity.latencyMs
            ?: return SectionHealth("Waiting", HealthLevel.UNAVAILABLE, "Collecting network quality", HealthSymbol.WAITING)
        val jitter = connectivity.jitterMs

        return when {
            latency > LATENCY_ATTENTION_MS ->
                SectionHealth("Attention", HealthLevel.ATTENTION, "Latency ${latencyText(latency)}", HealthSymbol.NETWORK_WARNING)

            jitter != null && jitter > JITTER_ATTENTION_MS ->
                SectionHealth("Attention", HealthLevel.ATTENTION, "Jitter ${latencyText(jitter)}", HealthSymbol.JITTER)

            latency >= LATENCY_CAUTION_MS ->
                SectionHealth("Caution", HealthLevel.CAUTION, "Latency ${latencyText(latency)}", HealthSymbol.NETWORK)

            jitter != null && jitter >= JITTER_CAUTION_MS ->
                SectionHealth("Caution", HealthLevel.CAUTION, "Jitter ${latencyText(jitter)}", HealthSymbol.JITTER)

            connectivity.wifiSignalDbm?.let { it <= WEAK_WIFI_DBM } == true ->
                SectionHealth("Caution", HealthLevel.CAUTION, "Weak Wi-Fi signal", HealthSymbol.NETWORK)

            else -> SectionHealth("Ready", HealthLevel.READY, stableNetworkReason(connectivity), HealthSymbol.NETWORK_READY)
        }
    }

    private fun evaluatePower(power: PowerSnapshot): SectionHealth {
        val state = power.powerState()
        val percent = power.batteryPercent
        val minutes = power.minutesRemaining

        return when (state) {
            PowerState.UNKNOWN ->
                SectionHealth(
                    label = "Waiting",
                    level = HealthLevel.UNAVAILABLE,
                    reason = if (power.isCollecting) "Collecting power status" else "Power source unavailable",
                    symbol = if (power.isCollecting) HealthSymbol.WAITING else HealthSymbol.BATTERY_UNKNOWN,
                )

            PowerState.BATTERY -> when {
                percent != null && percent < BATTERY_ATTENTION_PERCENT ->
                    SectionHealth("Attention", HealthLevel.ATTENTION, "Battery $percent%", HealthSymbol.BATTERY_LOW)

                minutes != null && minutes < BATTERY_ATTENTION_MINUTES ->
                    SectionHealth("Attention", HealthLevel.ATTENTION, "${TravelFormatters.minutes(minutes)} remaining", HealthSymbol.BATTERY_LOW)

                percent != null && percent <= BATTERY_CAUTION_PERCENT ->
                    SectionHealth("Caution", HealthLevel.CAUTION, "Battery $percent%", HealthSymbol.BATTERY_HALF)

                minutes != null && minutes <= BATTERY_CAUTION_MINUTES ->
                    SectionHealth("Caution", HealthLevel.CAUTION, "${TravelFormatters.minutes(minutes)} remaining", HealthSymbol.BATTERY_HALF)

                power.powerSaveMode -> batterySaver()
                else -> SectionHealth("Ready", HealthLevel.READY, readyPowerReason(percent, minutes), HealthSymbol.BATTERY_FULL)
            }

            PowerState.CHARGING ->
                if (power.powerSaveMode) {
                    batterySaver()
                } else {
                    SectionHealth("Ready", HealthLevel.READY, "Charging on external power", HealthSymbol.CHARGING)
                }

            PowerState.CHARGED ->
                if (power.powerSaveMode) {
                    batterySaver()
                } else {
                    SectionHealth("Ready", HealthLevel.READY, "Connected to power", HealthSymbol.PLUGGED_IN)
                }
        }
    }

    private fun evaluateOverall(network: SectionHealth, power: SectionHealth): SectionHealth {
        fun overall(label: String, level: HealthLevel, prefix: String, section: SectionHealth, symbol: HealthSymbol) =
            SectionHealth(label, level, "$prefix: ${section.reason}", symbol)

        return when {
            network.level == HealthLevel.ATTENTION ->
                overall("Attention", HealthLevel.ATTENTION, "Network", network, HealthSymbol.OVERALL_ATTENTION)

            power.level == HealthLevel.ATTENTION ->
                overall("Attention", HealthLevel.ATTENTION, "Power", power, HealthSymbol.OVERALL_ATTENTION)

            // The reason already says it is about the network; a prefix would only repeat it.
            network.symbol == HealthSymbol.NETWORK_UNVERIFIED ->
                SectionHealth("Caution", HealthLevel.CAUTION, network.reason, HealthSymbol.OVERALL_CAUTION)

            network.level == HealthLevel.CAUTION ->
                overall("Caution", HealthLevel.CAUTION, "Network", network, HealthSymbol.OVERALL_CAUTION)

            power.level == HealthLevel.CAUTION ->
                overall("Caution", HealthLevel.CAUTION, "Power", power, HealthSymbol.OVERALL_CAUTION)

            network.level == HealthLevel.UNAVAILABLE ->
                overall("Waiting", HealthLevel.UNAVAILABLE, "Network", network, HealthSymbol.WAITING)

            power.level == HealthLevel.UNAVAILABLE ->
                overall("Waiting", HealthLevel.UNAVAILABLE, "Power", power, HealthSymbol.WAITING)

            else -> SectionHealth("Ready", HealthLevel.READY, "Network and power look good", HealthSymbol.OVERALL_READY)
        }
    }

    private fun batterySaver() =
        SectionHealth("Caution", HealthLevel.CAUTION, "Battery Saver enabled", HealthSymbol.POWER_SAVE)

    private fun stableNetworkReason(connectivity: ConnectivitySnapshot): String =
        connectivity.wifiName?.trim()?.takeIf { it.isNotEmpty() }?.let { "Stable on $it" } ?: "Connection looks stable"

    private fun readyPowerReason(percent: Int?, minutes: Int?): String =
        when {
            minutes != null -> "${TravelFormatters.minutes(minutes)} remaining"
            percent != null -> "Battery $percent%"
            else -> "Power status looks good"
        }

    private fun latencyText(milliseconds: Double): String = TravelFormatters.latency(milliseconds).text
}
