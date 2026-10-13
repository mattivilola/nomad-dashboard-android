package com.iloapps.nomaddashboard.core.data.insights

import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalEvent
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthSummary
import com.iloapps.nomaddashboard.core.travel.status.StatusLinePresentation
import java.time.Instant
import java.time.ZoneId

/**
 * Everything the dashboard, the Quick Settings tile and the widgets derive from the snapshot, computed
 * once so they agree. A pure function of its inputs at [evaluatedAt]; it is re-evaluated at least every
 * minute because heads-up items such as "rain from 16:00" or "sunset in 45 min" depend on the time.
 */
data class DashboardInsights(
    /** The snapshot the insights were derived from, with latency readings older than five minutes removed. */
    val snapshot: DashboardSnapshot,
    /** Ranked heads-up items, best first (at most [com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpEngine.MAX_ITEMS]). */
    val headsUp: List<HeadsUpItem>,
    val health: DashboardHealthSummary,
    /** The one short status line, or null when nothing applies and the app name is shown instead. */
    val statusLine: StatusLinePresentation?,
    /** The border crossing that should be briefed now, if any. */
    val arrival: ArrivalEvent?,
    /** The companion's home time zone, or null when none is set. */
    val homeTimeZoneId: String?,
    /** The device time zone the insights were evaluated in. */
    val zone: ZoneId,
    val evaluatedAt: Instant,
)
