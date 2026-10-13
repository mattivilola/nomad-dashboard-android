package com.iloapps.nomaddashboard.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DashboardLayoutEditsTest {
    @Test
    fun customizeOrderAppendsCardsTheSavedOrderLacks() {
        val saved = listOf(DashboardCardId.WEATHER, DashboardCardId.POWER)

        val order = DashboardCardId.customizeOrder(saved)

        assertThat(order.take(2)).containsExactly(DashboardCardId.WEATHER, DashboardCardId.POWER).inOrder()
        assertThat(order).containsExactlyElementsIn(DashboardCardId.entries)
        assertThat(order).containsNoDuplicates()
    }

    @Test
    fun withCardMovedShiftsACardAndStopsAtTheEnds() {
        val settings = AppSettings(
            dashboardCardOrder = listOf(DashboardCardId.CONNECTIVITY, DashboardCardId.POWER, DashboardCardId.WEATHER),
        )

        assertThat(settings.withCardMoved(DashboardCardId.POWER, 1).dashboardCardOrder)
            .containsExactly(DashboardCardId.CONNECTIVITY, DashboardCardId.WEATHER, DashboardCardId.POWER).inOrder()
        assertThat(settings.withCardMoved(DashboardCardId.POWER, -1).dashboardCardOrder)
            .containsExactly(DashboardCardId.POWER, DashboardCardId.CONNECTIVITY, DashboardCardId.WEATHER).inOrder()
        assertThat(settings.withCardMoved(DashboardCardId.CONNECTIVITY, -1)).isEqualTo(settings)
        assertThat(settings.withCardMoved(DashboardCardId.WEATHER, 1)).isEqualTo(settings)
        assertThat(settings.withCardMoved(DashboardCardId.STAYS, 1)).isEqualTo(settings)
    }

    @Test
    fun listMoveAppliesTheSameRule() {
        val order = listOf(DashboardCardId.CONNECTIVITY, DashboardCardId.POWER, DashboardCardId.WEATHER)

        assertThat(order.withCardMoved(DashboardCardId.CONNECTIVITY, 1))
            .containsExactly(DashboardCardId.POWER, DashboardCardId.CONNECTIVITY, DashboardCardId.WEATHER).inOrder()
        assertThat(order.withCardMoved(DashboardCardId.WEATHER, 1)).isEqualTo(order)
    }

    @Test
    fun withCardMovedToPlacesTheCardAtTheTargetIndex() {
        val settings = AppSettings(
            dashboardCardOrder = listOf(DashboardCardId.CONNECTIVITY, DashboardCardId.POWER, DashboardCardId.WEATHER),
        )

        assertThat(settings.withCardMovedTo(DashboardCardId.CONNECTIVITY, 2).dashboardCardOrder)
            .containsExactly(DashboardCardId.POWER, DashboardCardId.WEATHER, DashboardCardId.CONNECTIVITY).inOrder()
        assertThat(settings.withCardMovedTo(DashboardCardId.STAYS, 0)).isEqualTo(settings)
    }

    @Test
    fun withCardCollapsedAddsAndRemovesTheCardFromTheSavedSet() {
        val collapsed = AppSettings().withCardCollapsed(DashboardCardId.WEATHER, collapsed = true)
        assertThat(collapsed.dashboardCollapsedCards).containsExactly(DashboardCardId.WEATHER)

        assertThat(collapsed.withCardCollapsed(DashboardCardId.WEATHER, collapsed = false).dashboardCollapsedCards).isEmpty()
    }

    @Test
    fun withCardOrderKeepsTheFirstOccurrenceOfEachCard() {
        val settings = AppSettings().withCardOrder(
            listOf(DashboardCardId.POWER, DashboardCardId.WEATHER, DashboardCardId.POWER),
        )

        assertThat(settings.dashboardCardOrder).containsExactly(DashboardCardId.POWER, DashboardCardId.WEATHER).inOrder()
    }

    @Test
    fun withCardWidthToggledFlipsBetweenWideAndNarrow() {
        val narrow = AppSettings().withCardWidthToggled(DashboardCardId.WEATHER)
        assertThat(narrow.dashboardCardWidthModes[DashboardCardId.WEATHER]).isEqualTo(DashboardCardWidthMode.NARROW)

        assertThat(narrow.withCardWidthToggled(DashboardCardId.WEATHER).dashboardCardWidthModes[DashboardCardId.WEATHER])
            .isEqualTo(DashboardCardWidthMode.WIDE)
    }

    @Test
    fun withRecommendedOrderRestoresTheProfileOrderAndKeepsWidths() {
        val custom = AppSettings(
            travelProfile = TravelProfile.ROAD_TRIP,
            dashboardCardOrder = DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP).reversed(),
            dashboardCardWidthModes = mapOf(DashboardCardId.WEATHER to DashboardCardWidthMode.NARROW),
        )
        assertThat(custom.isUsingRecommendedOrder).isFalse()

        val result = custom.withRecommendedOrder()

        assertThat(result.isUsingRecommendedOrder).isTrue()
        assertThat(result.dashboardCardOrder).isEqualTo(DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP))
        assertThat(result.dashboardCardWidthModes[DashboardCardId.WEATHER]).isEqualTo(DashboardCardWidthMode.NARROW)
    }

    @Test
    fun withDashboardLayoutResetRestoresOrderWidthsAndExpandedCards() {
        val custom = AppSettings(
            travelProfile = TravelProfile.ROAD_TRIP,
            dashboardCardOrder = DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP).reversed(),
            dashboardCardWidthModes = DashboardCardId.defaultOrder.associateWith { DashboardCardWidthMode.NARROW },
            dashboardCollapsedCards = setOf(DashboardCardId.POWER),
        )

        val result = custom.withDashboardLayoutReset()

        assertThat(result.dashboardCardOrder).isEqualTo(DashboardCardId.defaultOrder(TravelProfile.ROAD_TRIP))
        assertThat(result.dashboardCardWidthModes.values.toSet()).containsExactly(DashboardCardWidthMode.WIDE)
        assertThat(result.dashboardCollapsedCards).isEmpty()
        assertThat(result.travelProfile).isEqualTo(TravelProfile.ROAD_TRIP)
    }

    @Test
    fun withCardEnsuredAddsAMissingCardWideAndLeavesAPresentOneAlone() {
        val without = AppSettings(dashboardCardOrder = DashboardCardId.defaultOrder - DashboardCardId.LOCAL_INFO)

        val result = without.withCardEnsured(DashboardCardId.LOCAL_INFO)

        assertThat(result.dashboardCardOrder.last()).isEqualTo(DashboardCardId.LOCAL_INFO)
        assertThat(result.dashboardCardWidthModes[DashboardCardId.LOCAL_INFO]).isEqualTo(DashboardCardWidthMode.WIDE)
        assertThat(result.withCardEnsured(DashboardCardId.LOCAL_INFO)).isEqualTo(result)
    }
}
