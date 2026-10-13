package com.iloapps.nomaddashboard.core.travel.status

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity

/**
 * The one short line of Android's persistent status surface (the Quick Settings tile subtitle), the
 * counterpart of the macOS menu bar text.
 *
 * @property text what to show.
 * @property rule which rule produced it.
 * @property severity how loudly to tint it: only status colors, never a metric color. Null is neutral.
 */
data class StatusLinePresentation(
    val text: String,
    val rule: Rule,
    val severity: HeadsUpSeverity? = null,
) {
    /** The rules in precedence order; the first that applies wins. */
    enum class Rule {
        BATTERY,
        OFFLINE,
        HEADS_UP,
        LATENCY_CAUTION,
        LATENCY_ATTENTION,
        WEATHER,
        LATENCY_FALLBACK,
    }

    companion object {
        /** Battery at or below this percentage is shown. */
        const val BATTERY_MAX_PERCENT = 50
        const val LATENCY_CAUTION_MS = 60.0
        const val LATENCY_ATTENTION_MS = 120.0

        /**
         * First match wins: battery at or below 50%, offline, the top heads-up when it is a caution or
         * worse (and the user turned that on), latency from 60 ms, the temperature, then any latency.
         * Android has no Data Guard, so there is no usage rule. Null when nothing applies: the caller
         * shows the app name.
         *
         * @param headsUp the top-ranked heads-up item, if any. Info items never reach the status line.
         */
        fun of(
            snapshot: DashboardSnapshot,
            settings: AppSettings,
            headsUp: HeadsUpItem?,
        ): StatusLinePresentation? {
            snapshot.power.batteryPercent?.takeIf { it <= BATTERY_MAX_PERCENT }?.let {
                return StatusLinePresentation("$it%", Rule.BATTERY)
            }

            if (snapshot.connectivity.isOffline) {
                return StatusLinePresentation("Offline", Rule.OFFLINE, HeadsUpSeverity.ATTENTION)
            }

            if (settings.statusShowsHeadsUp && headsUp != null && headsUp.severity >= HeadsUpSeverity.CAUTION) {
                return StatusLinePresentation(headsUp.statusText, Rule.HEADS_UP, headsUp.severity)
            }

            val latencyMs = snapshot.connectivity.latencyMs
            if (latencyMs != null && latencyMs >= LATENCY_CAUTION_MS) {
                return StatusLinePresentation(
                    text = TravelFormatters.latency(latencyMs).text,
                    rule = if (latencyMs > LATENCY_ATTENTION_MS) Rule.LATENCY_ATTENTION else Rule.LATENCY_CAUTION,
                )
            }

            snapshot.weather.currentTemperatureCelsius?.let {
                return StatusLinePresentation(TravelFormatters.temperature(it), Rule.WEATHER)
            }

            return latencyMs?.let {
                StatusLinePresentation(TravelFormatters.latency(it).text, Rule.LATENCY_FALLBACK)
            }
        }
    }
}
