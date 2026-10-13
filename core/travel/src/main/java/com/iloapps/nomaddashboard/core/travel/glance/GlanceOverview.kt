package com.iloapps.nomaddashboard.core.travel.glance

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.health.DashboardHealthEvaluator
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The refresh button and the status text under it ("Last refresh 11.30"). */
data class GlanceRefresh(
    val statusText: String,
    val buttonTitle: String,
    val isButtonEnabled: Boolean,
)

/**
 * A heads-up item as the glance list shows it. Severity is carried as a health level so the row reuses
 * the shared status colors: attention is coral and caution is sand. Info carries no status, so its
 * [level] is null and the row stays neutral (colour means status only; macOS tints info teal).
 */
data class GlanceHeadsUpRow(
    val kind: HeadsUpKind,
    val title: String,
    val detail: String?,
    val level: HealthLevel?,
    /** The card that tapping the row expands and scrolls to. */
    val relatedCard: DashboardCardId,
) {
    val accessibilityHint: String
        get() = "Shows the ${relatedCard.displayTitle} card"

    companion object {
        fun of(item: HeadsUpItem): GlanceHeadsUpRow =
            GlanceHeadsUpRow(
                kind = item.kind,
                title = item.title,
                detail = item.detail,
                level = level(item.severity),
                relatedCard = item.relatedCard,
            )

        fun level(severity: HeadsUpSeverity): HealthLevel? =
            when (severity) {
                HeadsUpSeverity.ATTENTION -> HealthLevel.ATTENTION
                HeadsUpSeverity.CAUTION -> HealthLevel.CAUTION
                HeadsUpSeverity.INFO -> null
            }
    }
}

/**
 * The row of links under the heads-up list. macOS also offers "Explore cities" (Nomads.com); Android
 * has no counterpart yet, and a new link is added here once it does.
 */
enum class GlanceQuickLink(val title: String) {
    WORKPLACES("Workplaces"),
    SAVED_ESSENTIALS("Saved essentials"),
}

/**
 * The top of the dashboard: a location-led header, the verdict strip, a one-line clocks row, four
 * vital tiles, up to three heads-up items and a row of links. The Compose layer renders it without
 * further decisions.
 */
data class GlanceOverview(
    val header: GlanceHeader,
    val refresh: GlanceRefresh,
    val verdict: GlanceVerdict,
    val clocks: GlanceClocks,
    val tiles: List<GlanceVitalTile>,
    /** Hidden when empty. */
    val headsUp: List<GlanceHeadsUpRow>,
    val quickLinks: List<GlanceQuickLink>,
) {
    companion object {
        const val MAX_HEADS_UP_ITEMS = 3
    }
}

/**
 * Builds the [GlanceOverview] from the dashboard snapshot. Pure: the clock, the zone and the locale are
 * injected, so the result can be recomputed every minute and tested without a device.
 */
class GlanceOverviewBuilder(
    locale: Locale = Locale.getDefault(),
    private val clockTime: ClockTimeFormatter = ClockTimeFormatter.localized(locale),
) {
    /**
     * @param headsUp the ranked heads-up items, best first; the first [GlanceOverview.MAX_HEADS_UP_ITEMS] are shown.
     * @param homeTimeZoneId the user's home time zone, or null when none is set.
     * @param zone the device's current time zone: it decides "Here" and the local calendar day.
     */
    fun build(
        snapshot: DashboardSnapshot,
        settings: AppSettings,
        headsUp: List<HeadsUpItem>,
        homeTimeZoneId: String?,
        now: Instant,
        zone: ZoneId,
    ): GlanceOverview {
        val isOffline = snapshot.connectivity.isOffline
        val health = DashboardHealthEvaluator.evaluate(snapshot.connectivity, snapshot.power)

        return GlanceOverview(
            header = GlanceHeader.of(snapshot.travelContext),
            refresh = refresh(snapshot, zone),
            verdict = GlanceVerdict.of(health, vpnActive = snapshot.connectivity.vpnActive, isOffline = isOffline),
            clocks = GlanceClocks.of(
                deviceCity = snapshot.travelContext.deviceCity,
                homeTimeZoneId = homeTimeZoneId,
                now = now,
                zone = zone,
                clockTime = clockTime,
            ),
            tiles = GlanceVitalTile.tiles(
                snapshot = snapshot,
                settings = settings,
                health = health,
                isOffline = isOffline,
                today = now.atZone(zone).toLocalDate(),
            ),
            headsUp = headsUp.take(GlanceOverview.MAX_HEADS_UP_ITEMS).map(GlanceHeadsUpRow::of),
            quickLinks = GlanceQuickLink.entries,
        )
    }

    private fun refresh(snapshot: DashboardSnapshot, zone: ZoneId): GlanceRefresh {
        if (snapshot.isRefreshing) {
            return GlanceRefresh(
                statusText = "Refreshing dashboard…",
                buttonTitle = "Refreshing dashboard",
                isButtonEnabled = false,
            )
        }
        val lastRefresh = snapshot.lastRefresh
        return GlanceRefresh(
            statusText = lastRefresh?.let { "Last refresh ${clockTime.format(it.atZone(zone))}" }
                ?: "Waiting for first refresh",
            buttonTitle = "Refresh",
            isButtonEnabled = true,
        )
    }
}
