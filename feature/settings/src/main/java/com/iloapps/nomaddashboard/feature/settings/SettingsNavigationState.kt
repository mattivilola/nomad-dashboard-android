package com.iloapps.nomaddashboard.feature.settings

import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.SettingsPage
import com.iloapps.nomaddashboard.core.travel.stayscard.StaySettingsPresentation

/**
 * Which settings page is open and which group a deep link wants scrolled into view and highlighted.
 * On a phone [selectedPage] null means the page list is showing; side by side the list is always
 * visible, so null falls back to the first page.
 */
data class SettingsNavigationState(
    val selectedPage: SettingsPage? = null,
    val focus: SettingsDestination? = null,
    /** Bumped on every deep link so the same destination can be focused again. */
    val focusRequest: Int = 0,
) {
    fun effectivePage(sideBySide: Boolean): SettingsPage? =
        selectedPage ?: SettingsPage.entries.first().takeIf { sideBySide }

    fun openPage(page: SettingsPage): SettingsNavigationState = copy(selectedPage = page, focus = null)

    fun openDestination(destination: SettingsDestination): SettingsNavigationState =
        copy(selectedPage = destination.page, focus = destination, focusRequest = focusRequest + 1)

    fun back(): SettingsNavigationState = copy(selectedPage = null, focus = null)

    fun focusHandled(): SettingsNavigationState = copy(focus = null)
}

/**
 * Every group card in settings, in the order it appears on its page. A group with a
 * [destination] can be deep-linked to.
 */
enum class SettingsGroup(
    val page: SettingsPage,
    val title: String,
    val destination: SettingsDestination? = null,
) {
    TRAVEL_PROFILE(SettingsPage.GENERAL, "Travel profile", SettingsDestination.TRAVEL_PROFILE),
    HOME_TIME_ZONE(SettingsPage.GENERAL, "Home time zone", SettingsDestination.HOME_TIME_ZONE),
    CITIZENSHIP(SettingsPage.GENERAL, "Citizenship", SettingsDestination.CITIZENSHIP),
    APPEARANCE(SettingsPage.GENERAL, "Appearance"),
    STATUS_TILE(SettingsPage.GENERAL, "Status tile"),
    ARRIVAL_BRIEFING(SettingsPage.GENERAL, "Arrival briefing", SettingsDestination.ARRIVAL_BRIEFING),
    DASHBOARD_LAYOUT(SettingsPage.GENERAL, "Dashboard layout"),

    WEATHER(SettingsPage.WEATHER_AND_LOCAL, "Weather location", SettingsDestination.WEATHER),
    AIR_QUALITY(SettingsPage.WEATHER_AND_LOCAL, "Air quality", SettingsDestination.AIR_QUALITY),
    SURF_SPOT(SettingsPage.WEATHER_AND_LOCAL, "Surf spot", SettingsDestination.SURF_SPOT),
    LOCAL_INFO(SettingsPage.WEATHER_AND_LOCAL, "Local Info", SettingsDestination.LOCAL_INFO),
    FUEL_PRICES(SettingsPage.WEATHER_AND_LOCAL, "Fuel prices", SettingsDestination.FUEL_PRICES),

    TRAVEL_ALERTS(SettingsPage.SAFETY, "Travel alerts", SettingsDestination.TRAVEL_ALERTS),
    EMERGENCY_CARE(SettingsPage.SAFETY, "Emergency care", SettingsDestination.EMERGENCY_CARE),
    YOUR_EMBASSY(SettingsPage.SAFETY, "Your embassy"),

    SCHENGEN_COUNTER(SettingsPage.STAYS_AND_VISAS, StaySettingsPresentation.SchengenSectionTitle, SettingsDestination.STAYS),
    STAY_ALLOWANCES(SettingsPage.STAYS_AND_VISAS, StaySettingsPresentation.AllowancesTitle),
    TAX_RESIDENCE_DAYS(SettingsPage.STAYS_AND_VISAS, StaySettingsPresentation.TaxSectionTitle),
    STAYS_DATA(SettingsPage.STAYS_AND_VISAS, StaySettingsPresentation.DataTitle),

    DATA_USAGE(SettingsPage.CONNECTION_AND_DATA, "Data usage"),
    OFFLINE_ESSENTIALS(SettingsPage.CONNECTION_AND_DATA, "Offline essentials"),
    QUIET_ALERTS(SettingsPage.CONNECTION_AND_DATA, "Quiet connection alerts", SettingsDestination.QUIET_ALERTS),

    TIME_TRACKING(SettingsPage.WORK, "Project time tracking", SettingsDestination.TIME_TRACKING),
    WORKPLACE_DIARY(SettingsPage.WORK, "Workplace diary", SettingsDestination.WORKPLACE_DIARY),

    LOCATION_PERMISSION(SettingsPage.PRIVACY_AND_LOCATION, "Location permission"),
    EXTERNAL_IP(SettingsPage.PRIVACY_AND_LOCATION, "External IP location"),
    WIFI_NAMES(SettingsPage.PRIVACY_AND_LOCATION, "Wi-Fi names"),
    VISITED_PLACES(SettingsPage.PRIVACY_AND_LOCATION, "Visited places", SettingsDestination.VISITED_PLACES),
    ANALYTICS(SettingsPage.PRIVACY_AND_LOCATION, "Anonymous analytics"),

    REFRESH(SettingsPage.ADVANCED, "Refresh"),
    ABOUT(SettingsPage.ADVANCED, "About"),
    ;

    companion object {
        fun groupsOn(page: SettingsPage): List<SettingsGroup> = entries.filter { it.page == page }

        fun forDestination(destination: SettingsDestination): SettingsGroup =
            entries.first { it.destination == destination }
    }
}
