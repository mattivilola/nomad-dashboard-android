package com.iloapps.nomaddashboard.feature.dashboard

import com.iloapps.nomaddashboard.core.model.customizeOrder
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Settings
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardPacking
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardRow
import com.iloapps.nomaddashboard.core.travel.summary.DashboardCardVisibility
import kotlinx.coroutines.flow.first

/**
 * One entry of the dashboard list, in display order. The list is built from the settings alone (see
 * [dashboardListItems]), so the screen and the scroll-to-card request agree on where a card is without
 * waiting for a recomposition.
 */
internal sealed interface DashboardListItem {
    val key: String

    /** The glance header and, while time tracking is on, the time-tracking row. */
    data object TopBar : DashboardListItem {
        override val key: String = "top_bar"
    }

    /** The verdict strip, clocks, vital tiles, heads-up items and links. */
    data object Glance : DashboardListItem {
        override val key: String = "glance"
    }

    /** The one-time "Welcome to ..." card, present only while an arrival is waiting to be acknowledged. */
    data object ArrivalBriefing : DashboardListItem {
        override val key: String = "arrival_briefing"
    }

    data object CompanionOverview : DashboardListItem {
        override val key: String = "companion_overview"
    }

    data object LocationAccess : DashboardListItem {
        override val key: String = "location_access"
    }

    /** One or two cards sharing a row. Two only on wide layouts, when both chose the narrow width. */
    data class CardRow(val row: DashboardCardRow) : DashboardListItem {
        override val key: String = row.id
    }

    /** A card in Customize Layout: one per item, with its drag handle and width toggle. */
    data class CustomizeCard(val card: DashboardCardId) : DashboardListItem {
        override val key: String = card.name
    }

    /** "Turned off: Local Info, Fuel Prices" with the Settings button that opens the first group. */
    data class TurnedOff(
        val text: String,
        val destination: SettingsDestination?,
    ) : DashboardListItem {
        override val key: String = "turned_off"
    }

    companion object {
        /** Item keys of the cards in Customize Layout, which the drag gesture tells from the other items. */
        val customizeCardKeys: Set<String> = DashboardCardId.entries.map { it.name }.toSet()
    }
}

internal fun dashboardListItems(
    settings: AppSettings,
    hasLocationPermission: Boolean,
    isCustomizingLayout: Boolean,
    isWideLayout: Boolean,
    hasArrivalBriefing: Boolean = false,
    customizeOrder: List<DashboardCardId> = DashboardCardId.customizeOrder(settings.dashboardCardOrder),
): List<DashboardListItem> = buildList {
    add(DashboardListItem.TopBar)
    add(DashboardListItem.Glance)
    if (hasArrivalBriefing) add(DashboardListItem.ArrivalBriefing)
    add(DashboardListItem.CompanionOverview)
    if (!hasLocationPermission) add(DashboardListItem.LocationAccess)

    if (isCustomizingLayout) {
        customizeOrder.forEach { add(DashboardListItem.CustomizeCard(it)) }
        return@buildList
    }

    val visibility = DashboardCardVisibility.of(
        order = settings.dashboardCardOrder,
        settings = settings,
        isCustomizingLayout = false,
    )
    val widthModes = if (isWideLayout) settings.dashboardCardWidthModes else emptyMap()
    DashboardCardPacking.rows(
        order = visibility.visibleOrder,
        widthModes = widthModes,
        collapsed = settings.dashboardCollapsedCards,
    ).forEach { add(DashboardListItem.CardRow(it)) }

    visibility.turnedOffText?.let { text ->
        add(DashboardListItem.TurnedOff(text = text, destination = visibility.turnedOffSettingsDestination))
    }
}

/** The index of the item that shows [card], or -1 when the card is not on the dashboard. */
internal fun List<DashboardListItem>.indexOfCard(card: DashboardCardId): Int =
    indexOfFirst { item ->
        when (item) {
            is DashboardListItem.CardRow -> item.row.items.any { it.card == card }
            is DashboardListItem.CustomizeCard -> item.card == card
            else -> false
        }
    }
