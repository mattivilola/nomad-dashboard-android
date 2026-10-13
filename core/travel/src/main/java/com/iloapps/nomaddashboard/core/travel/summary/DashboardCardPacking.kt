package com.iloapps.nomaddashboard.core.travel.summary

import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode

enum class DashboardCardRenderWidth {
    FULL,
    HALF,
}

data class DashboardCardRowItem(
    val card: DashboardCardId,
    /** The width the user chose for the card. Stored preferences are never rewritten by packing. */
    val preferredWidthMode: DashboardCardWidthMode,
    val renderWidth: DashboardCardRenderWidth,
) {
    /**
     * The layout the card actually renders with: narrow only while it really shares a row with a narrow
     * neighbour, otherwise the wide layout at full width.
     */
    val layoutWidthMode: DashboardCardWidthMode
        get() = when (renderWidth) {
            DashboardCardRenderWidth.HALF -> DashboardCardWidthMode.NARROW
            DashboardCardRenderWidth.FULL -> DashboardCardWidthMode.WIDE
        }
}

data class DashboardCardRow(
    val items: List<DashboardCardRowItem>,
) {
    val id: String
        get() = items.joinToString("|") { it.card.name }
}

object DashboardCardPacking {
    /**
     * Packs cards into rows: two adjacent narrow cards share a row at half width; every other card,
     * including a narrow card without a narrow partner, takes the full width. A collapsed card is a
     * one-line summary and always takes the full width, so it never pairs.
     */
    fun rows(
        order: List<DashboardCardId>,
        widthModes: Map<DashboardCardId, DashboardCardWidthMode>,
        collapsed: Set<DashboardCardId> = emptySet(),
    ): List<DashboardCardRow> {
        fun packsNarrow(card: DashboardCardId): Boolean =
            (widthModes[card] ?: DashboardCardWidthMode.WIDE) == DashboardCardWidthMode.NARROW && card !in collapsed

        val rows = mutableListOf<DashboardCardRow>()
        var index = 0
        while (index < order.size) {
            val card = order[index]
            val next = order.getOrNull(index + 1)

            if (packsNarrow(card) && next != null && packsNarrow(next)) {
                rows += DashboardCardRow(
                    listOf(
                        DashboardCardRowItem(card, DashboardCardWidthMode.NARROW, DashboardCardRenderWidth.HALF),
                        DashboardCardRowItem(next, DashboardCardWidthMode.NARROW, DashboardCardRenderWidth.HALF),
                    ),
                )
                index += 2
            } else {
                rows += DashboardCardRow(
                    listOf(
                        DashboardCardRowItem(
                            card = card,
                            preferredWidthMode = widthModes[card] ?: DashboardCardWidthMode.WIDE,
                            renderWidth = DashboardCardRenderWidth.FULL,
                        ),
                    ),
                )
                index += 1
            }
        }
        return rows
    }
}
