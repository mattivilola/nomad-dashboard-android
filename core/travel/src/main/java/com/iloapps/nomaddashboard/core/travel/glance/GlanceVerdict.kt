package com.iloapps.nomaddashboard.core.travel.glance

import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthSummary
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.health.HealthSymbol

/** The verdict strip: one line answering "can I work here right now?", tinted by the overall health level. */
data class GlanceVerdict(
    val title: String,
    val detail: String,
    val symbol: HealthSymbol,
    val level: HealthLevel,
) {
    companion object {
        const val OFFLINE_TITLE = "You're offline"
        const val OFFLINE_DETAIL = "Saved essentials are available"

        fun of(health: DashboardHealthSummary, vpnActive: Boolean, isOffline: Boolean): GlanceVerdict {
            if (isOffline) {
                return GlanceVerdict(OFFLINE_TITLE, OFFLINE_DETAIL, HealthSymbol.OFFLINE, HealthLevel.CAUTION)
            }

            val overall = health.overall
            return when (overall.level) {
                HealthLevel.READY -> GlanceVerdict(
                    title = "Ready to work",
                    detail = if (vpnActive) "${health.network.reason} · VPN on" else health.network.reason,
                    symbol = overall.symbol,
                    level = overall.level,
                )

                HealthLevel.CAUTION -> GlanceVerdict("Workable", overall.reason, overall.symbol, overall.level)
                HealthLevel.ATTENTION -> GlanceVerdict("Needs attention", overall.reason, overall.symbol, overall.level)
                HealthLevel.UNAVAILABLE -> GlanceVerdict("Checking conditions", overall.reason, overall.symbol, overall.level)
            }
        }
    }
}
