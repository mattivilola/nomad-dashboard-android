package com.iloapps.nomaddashboard.core.travel.headsup

import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.TravelProfile

/**
 * What a heads-up item is about. At most one item per kind is shown. Declaration order is the Nomad
 * tie-break priority within the same severity (earlier wins), matching the macOS heads-up engine.
 * Network and battery health are never heads-up items: the verdict strip covers them.
 */
enum class HeadsUpKind {
    DATA_CAP,
    STAY_ALLOWANCE,
    SCHENGEN_LIMIT,
    TAX_RESIDENCE_DAYS,
    TRAVEL_ADVISORY,
    WEATHER_ALERT,
    REGIONAL_SECURITY,
    STRONG_WIND,
    RAIN_SOON,
    UV_INDEX,
    AIR_QUALITY,
    PUBLIC_HOLIDAY,
    LOCAL_RHYTHM,
    FUEL_NEARBY,
    DAYLIGHT,
    ;

    companion object {
        private val roadTripOrder = listOf(
            STRONG_WIND,
            DAYLIGHT,
            UV_INDEX,
            AIR_QUALITY,
            FUEL_NEARBY,
            WEATHER_ALERT,
            DATA_CAP,
            STAY_ALLOWANCE,
            SCHENGEN_LIMIT,
            TAX_RESIDENCE_DAYS,
            TRAVEL_ADVISORY,
            REGIONAL_SECURITY,
            RAIN_SOON,
            PUBLIC_HOLIDAY,
            LOCAL_RHYTHM,
        )

        /** Kinds in tie-break priority within the same severity (earlier wins) for a travel profile. */
        fun tieBreakOrder(profile: TravelProfile): List<HeadsUpKind> =
            when (profile) {
                TravelProfile.NOMAD -> entries
                TravelProfile.ROAD_TRIP -> roadTripOrder
            }
    }
}

/** One ranked heads-up item shown under the glance overview and, optionally, on the status surface. */
data class HeadsUpItem(
    val kind: HeadsUpKind,
    val severity: HeadsUpSeverity,
    /** About 32 characters or fewer. */
    val title: String,
    /** Compact label (about 12 characters or fewer) for the status surface, e.g. "Wind 45". */
    val statusText: String,
    val detail: String?,
    /** The card that tapping the item expands and scrolls to. */
    val relatedCard: DashboardCardId,
)
