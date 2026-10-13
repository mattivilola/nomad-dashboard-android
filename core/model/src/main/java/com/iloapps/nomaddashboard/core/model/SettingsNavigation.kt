package com.iloapps.nomaddashboard.core.model

/** The settings pages, ordered the way a traveller thinks. */
enum class SettingsPage(val title: String) {
    GENERAL("General"),
    WEATHER_AND_LOCAL("Weather & Local"),
    SAFETY("Safety"),
    STAYS_AND_VISAS("Stays & Visas"),
    CONNECTION_AND_DATA("Connection & Data"),
    WORK("Work"),
    PRIVACY_AND_LOCATION("Privacy & Location"),
    ADVANCED("Advanced"),
}

/** A settings group that an "Open Settings" entry point can deep-link to. */
enum class SettingsDestination(val page: SettingsPage) {
    HOME_TIME_ZONE(SettingsPage.GENERAL),
    CITIZENSHIP(SettingsPage.GENERAL),
    TRAVEL_PROFILE(SettingsPage.GENERAL),
    ARRIVAL_BRIEFING(SettingsPage.GENERAL),
    WEATHER(SettingsPage.WEATHER_AND_LOCAL),
    SURF_SPOT(SettingsPage.WEATHER_AND_LOCAL),
    LOCAL_INFO(SettingsPage.WEATHER_AND_LOCAL),
    FUEL_PRICES(SettingsPage.WEATHER_AND_LOCAL),
    AIR_QUALITY(SettingsPage.WEATHER_AND_LOCAL),
    TRAVEL_ALERTS(SettingsPage.SAFETY),
    EMERGENCY_CARE(SettingsPage.SAFETY),
    QUIET_ALERTS(SettingsPage.CONNECTION_AND_DATA),
    TIME_TRACKING(SettingsPage.WORK),
    WORKPLACE_DIARY(SettingsPage.WORK),
    VISITED_PLACES(SettingsPage.PRIVACY_AND_LOCATION),
    STAYS(SettingsPage.STAYS_AND_VISAS),
}

/**
 * The settings group that switches this card's feature on, or null when the card has no setting
 * of its own.
 */
val DashboardCardId.settingsDestination: SettingsDestination?
    get() = when (this) {
        DashboardCardId.TIME_TRACKING -> SettingsDestination.TIME_TRACKING
        DashboardCardId.STAYS -> SettingsDestination.VISITED_PLACES
        DashboardCardId.LOCAL_INFO -> SettingsDestination.LOCAL_INFO
        DashboardCardId.FUEL_PRICES -> SettingsDestination.FUEL_PRICES
        DashboardCardId.EMERGENCY_CARE -> SettingsDestination.EMERGENCY_CARE
        DashboardCardId.EMBASSY -> SettingsDestination.CITIZENSHIP
        DashboardCardId.TRAVEL_ALERTS -> SettingsDestination.TRAVEL_ALERTS
        DashboardCardId.WEATHER -> SettingsDestination.WEATHER
        DashboardCardId.CONNECTIVITY,
        DashboardCardId.POWER,
        DashboardCardId.TRAVEL_CONTEXT,
        -> null
    }
