package com.iloapps.nomaddashboard.core.travel.summary

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.settingsDestination
import com.iloapps.nomaddashboard.core.travel.glance.displayTitle

/**
 * Which cards take up space in the dashboard. Cards whose opt-in feature is switched off are not
 * rendered (each would spend a card of height saying "Off"); one compact row names them instead. While
 * the layout is being customized every card shows so it can still be ordered.
 */
data class DashboardCardVisibility(
    val visibleOrder: List<DashboardCardId>,
    /** The cards that are turned off, in dashboard order. Empty while customizing. */
    val turnedOffCards: List<DashboardCardId>,
) {
    /** Titles of the turned-off cards, in dashboard order. */
    val turnedOffTitles: List<String>
        get() = turnedOffCards.map { it.displayTitle }

    /** "Turned off: Local Info, Fuel Prices", or null when everything is on. */
    val turnedOffText: String?
        get() = turnedOffTitles.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Turned off: ", separator = ", ")

    /** Where the row's Settings button goes: the settings group of the first turned-off card. */
    val turnedOffSettingsDestination: SettingsDestination?
        get() = turnedOffCards.firstOrNull()?.settingsDestination

    companion object {
        fun of(
            order: List<DashboardCardId>,
            settings: AppSettings,
            isCustomizingLayout: Boolean,
        ): DashboardCardVisibility {
            if (isCustomizingLayout) {
                return DashboardCardVisibility(visibleOrder = order, turnedOffCards = emptyList())
            }
            val (visible, turnedOff) = order.partition { it.isFeatureEnabled(settings) }
            return DashboardCardVisibility(visibleOrder = visible, turnedOffCards = turnedOff)
        }
    }
}
