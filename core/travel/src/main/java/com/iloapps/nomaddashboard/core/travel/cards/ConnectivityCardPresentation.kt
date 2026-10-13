package com.iloapps.nomaddashboard.core.travel.cards

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.travel.format.FormattedMetric
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel

/**
 * One metric of a card with a sparkline under it: its title, the value with its unit apart, the
 * status [tone] of the value and the trend behind it.
 */
data class TrendMetricPresentation(
    val title: String,
    val reading: FormattedMetric,
    val tone: CardTone = CardTone.NEUTRAL,
    val trend: MetricTrend? = null,
)

/**
 * The Connectivity card's body: Down, Up and Latency in one row, each with a sparkline, latency
 * tinted by the network health, and one caption for the jitter.
 */
data class ConnectivityCardPresentation(
    val download: TrendMetricPresentation,
    val upload: TrendMetricPresentation,
    val latency: TrendMetricPresentation,
    /** "Jitter 3 ms via 1.1.1.1"; null while offline, where a jitter hint would only mislead. */
    val jitterCaption: String?,
) {
    companion object {
        const val WAITING = "Waiting"
        const val UNAVAILABLE = "Unavailable"
        const val JITTER_PENDING = "Jitter appears after the next refresh"

        /** A throughput below this reads as "0": an idle link, not a measurement. */
        private const val MINIMUM_THROUGHPUT_MBPS = 0.05

        /**
         * @param networkLevel The network health, which tints the latency value.
         * @param probeHost The host the latency probe talked to; the caption leaves out "via" when
         *   the data layer does not report one.
         */
        fun of(
            connectivity: ConnectivitySnapshot,
            networkLevel: HealthLevel,
            probeHost: String? = null,
        ): ConnectivityCardPresentation =
            ConnectivityCardPresentation(
                download = TrendMetricPresentation(
                    title = "Down",
                    reading = throughput(connectivity.downloadMbps),
                    trend = MetricTrend.of(connectivity.downloadHistoryMbps),
                ),
                upload = TrendMetricPresentation(
                    title = "Up",
                    reading = throughput(connectivity.uploadMbps),
                    trend = MetricTrend.of(connectivity.uploadHistoryMbps),
                ),
                latency = TrendMetricPresentation(
                    title = "Latency",
                    reading = connectivity.latencyMs?.let(TravelFormatters::latency)
                        ?: FormattedMetric(if (connectivity.isOffline) UNAVAILABLE else WAITING),
                    tone = if (connectivity.latencyMs != null) CardTone.of(networkLevel) else CardTone.NEUTRAL,
                    trend = MetricTrend.of(connectivity.latencyHistoryMs),
                ),
                jitterCaption = jitterCaption(connectivity, probeHost),
            )

        private fun throughput(value: Double?): FormattedMetric {
            val megabits = value ?: 0.0
            return if (megabits < MINIMUM_THROUGHPUT_MBPS) {
                FormattedMetric("0", "Mbps")
            } else {
                TravelFormatters.megabitsPerSecond(megabits)
            }
        }

        private fun jitterCaption(connectivity: ConnectivitySnapshot, probeHost: String?): String? {
            if (connectivity.isOffline) return null
            val jitter = connectivity.jitterMs ?: return JITTER_PENDING
            val host = probeHost?.trim()?.takeIf(String::isNotEmpty)
            return "Jitter ${TravelFormatters.latency(jitter).text}" + (host?.let { " via $it" } ?: "")
        }
    }
}
