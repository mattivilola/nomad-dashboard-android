package com.iloapps.nomaddashboard.core.travel.glance

import com.iloapps.nomaddashboard.core.model.DashboardCardId

/** The trimmed string, or null when it is missing or blank. */
internal fun String?.trimmedNonEmpty(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** The title shown on the card itself. */
val DashboardCardId.displayTitle: String
    get() = when (this) {
        DashboardCardId.CONNECTIVITY -> "Connectivity"
        DashboardCardId.POWER -> "Power"
        DashboardCardId.TIME_TRACKING -> "Time Tracking"
        DashboardCardId.TRAVEL_CONTEXT -> "Travel Context"
        DashboardCardId.STAYS -> "Stays & Visas"
        DashboardCardId.LOCAL_INFO -> "Local Info"
        DashboardCardId.FUEL_PRICES -> "Fuel Prices"
        DashboardCardId.EMERGENCY_CARE -> "Emergency Care"
        DashboardCardId.EMBASSY -> "Your Embassy"
        DashboardCardId.TRAVEL_ALERTS -> "Travel Alerts"
        DashboardCardId.WEATHER -> "Weather"
    }
