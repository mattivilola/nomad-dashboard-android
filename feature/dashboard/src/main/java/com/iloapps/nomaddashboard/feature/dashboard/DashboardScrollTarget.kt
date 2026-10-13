package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.snapshotFlow
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A request to bring a card into view: the dashboard expands the card if it is collapsed, then scrolls
 * to it. Tapping a vital tile or a heads-up item makes this request for the card it belongs to.
 *
 * Pass it to `DashboardScreen` and clear it in `onScrollTargetHandled`, so the same card can be
 * requested again later.
 */
data class DashboardScrollTarget(val card: DashboardCardId)

/** How long to wait for a saved expansion to show up before scrolling anyway. */
internal const val ExpandBeforeScrollTimeoutMillis = 1_000L

/**
 * Expands [card] when it is collapsed, waits until the expansion has landed, then scrolls to it, in that
 * order: scrolling first would aim at the card's one-line height and stop short of where it ends up.
 *
 * @param isCollapsed Reads the saved collapsed state; it must read Compose snapshot state so the wait
 * for the saved change can observe it.
 * @param indexOfCard The card's list index right now (-1 when it is not on the dashboard, for example
 * because its feature is turned off), read after the expansion because packing can change the rows.
 */
internal suspend fun revealDashboardCard(
    card: DashboardCardId,
    isCollapsed: () -> Boolean,
    expand: () -> Unit,
    indexOfCard: () -> Int,
    scrollToItem: suspend (Int) -> Unit,
    expandTimeoutMillis: Long = ExpandBeforeScrollTimeoutMillis,
) {
    if (isCollapsed()) {
        expand()
        withTimeoutOrNull(expandTimeoutMillis) { snapshotFlow(isCollapsed).first { collapsed -> !collapsed } }
    }
    val index = indexOfCard()
    if (index >= 0) scrollToItem(index)
}
