package com.iloapps.nomaddashboard.core.model

/*
 * The pure edits to the dashboard layout stored in [AppSettings]: card order, width and collapsed state.
 * The dashboard's Customize Layout, its card chevrons and the Settings layout editor all use these, so
 * they write the same shape.
 */

/**
 * The order Customize Layout shows: the [saved] order, then any card it lacks, so every card can be
 * ordered.
 */
fun DashboardCardId.Companion.customizeOrder(saved: List<DashboardCardId>): List<DashboardCardId> =
    saved + DashboardCardId.entries.filterNot { it in saved }

/** This order with [card] moved by [delta] places, stopping at either end; an unknown card changes nothing. */
fun List<DashboardCardId>.withCardMoved(card: DashboardCardId, delta: Int): List<DashboardCardId> {
    val index = indexOf(card)
    if (index == -1 || delta == 0) return this
    val target = (index + delta).coerceIn(0, lastIndex)
    if (target == index) return this
    return toMutableList().apply {
        removeAt(index)
        add(target, card)
    }
}

/** The card order with [card] moved by [delta] places, stopping at either end; an unknown card changes nothing. */
fun AppSettings.withCardMoved(card: DashboardCardId, delta: Int): AppSettings {
    val moved = dashboardCardOrder.withCardMoved(card, delta)
    return if (moved == dashboardCardOrder) this else copy(dashboardCardOrder = moved)
}

/** The card order with [card] placed at [index] (clamped); an unknown card changes nothing. */
fun AppSettings.withCardMovedTo(card: DashboardCardId, index: Int): AppSettings {
    val current = dashboardCardOrder.indexOf(card)
    if (current == -1) return this
    return withCardMoved(card, index - current)
}

/** Saves [order] as the card order, keeping the first occurrence of each card. */
fun AppSettings.withCardOrder(order: List<DashboardCardId>): AppSettings =
    copy(dashboardCardOrder = order.distinct())

fun AppSettings.withCardWidthToggled(card: DashboardCardId): AppSettings {
    val current = dashboardCardWidthModes[card] ?: DashboardCardWidthMode.WIDE
    val next = if (current == DashboardCardWidthMode.NARROW) DashboardCardWidthMode.WIDE else DashboardCardWidthMode.NARROW
    return copy(dashboardCardWidthModes = dashboardCardWidthModes + (card to next))
}

fun AppSettings.withCardCollapsed(card: DashboardCardId, collapsed: Boolean): AppSettings =
    copy(
        dashboardCollapsedCards = if (collapsed) dashboardCollapsedCards + card else dashboardCollapsedCards - card,
    )

/** The recommended order for [AppSettings.travelProfile], keeping widths and collapsed cards. */
fun AppSettings.withRecommendedOrder(): AppSettings =
    copy(dashboardCardOrder = DashboardCardId.defaultOrder(travelProfile))

/** The current profile's recommended order, every card wide and expanded. */
fun AppSettings.withDashboardLayoutReset(): AppSettings =
    copy(
        dashboardCardOrder = DashboardCardId.defaultOrder(travelProfile),
        dashboardCardWidthModes = DashboardCardId.defaultOrder.associateWith { DashboardCardWidthMode.WIDE },
        dashboardCollapsedCards = emptySet(),
    )

/** Adds [card] at the end of the order, wide, when the saved order lacks it, for example when its feature is turned on. */
fun AppSettings.withCardEnsured(card: DashboardCardId): AppSettings {
    if (dashboardCardOrder.contains(card)) return this
    return copy(
        dashboardCardOrder = dashboardCardOrder + card,
        dashboardCardWidthModes = dashboardCardWidthModes + (card to DashboardCardWidthMode.WIDE),
    )
}
