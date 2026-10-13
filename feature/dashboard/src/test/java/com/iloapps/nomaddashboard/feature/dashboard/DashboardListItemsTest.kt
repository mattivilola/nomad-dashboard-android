package com.iloapps.nomaddashboard.feature.dashboard

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import org.junit.Test

class DashboardListItemsTest {
    private val everythingOn = AppSettings(
        localInfoEnabled = true,
        fuelPricesEnabled = true,
        emergencyCareEnabled = true,
        projectTimeTrackingEnabled = true,
        visitedPlacesEnabled = true,
        citizenshipCountryCodes = listOf("FI"),
    )

    private fun items(
        settings: AppSettings,
        hasLocationPermission: Boolean = true,
        isCustomizingLayout: Boolean = false,
        isWideLayout: Boolean = false,
        hasArrivalBriefing: Boolean = false,
    ) = dashboardListItems(settings, hasLocationPermission, isCustomizingLayout, isWideLayout, hasArrivalBriefing)

    private fun List<DashboardListItem>.cardRows() = filterIsInstance<DashboardListItem.CardRow>()

    @Test
    fun fixedItemsComeFirstAndTheLocationCardOnlyWithoutPermission() {
        val withPermission = items(everythingOn, hasLocationPermission = true)
        val withoutPermission = items(everythingOn, hasLocationPermission = false)

        assertThat(withPermission.take(3)).containsExactly(
            DashboardListItem.TopBar,
            DashboardListItem.Glance,
            DashboardListItem.CompanionOverview,
        ).inOrder()
        assertThat(withPermission).doesNotContain(DashboardListItem.LocationAccess)
        assertThat(withoutPermission[3]).isEqualTo(DashboardListItem.LocationAccess)
    }

    @Test
    fun theArrivalBriefingSitsRightBelowTheGlanceAndOnlyWhileThereIsOne() {
        val withArrival = items(everythingOn, hasArrivalBriefing = true)
        val without = items(everythingOn)

        assertThat(withArrival.take(4)).containsExactly(
            DashboardListItem.TopBar,
            DashboardListItem.Glance,
            DashboardListItem.ArrivalBriefing,
            DashboardListItem.CompanionOverview,
        ).inOrder()
        assertThat(without).doesNotContain(DashboardListItem.ArrivalBriefing)
    }

    @Test
    fun theArrivalBriefingShiftsTheCardIndexesByOne() {
        val withArrival = items(everythingOn, hasArrivalBriefing = true).indexOfCard(DashboardCardId.WEATHER)
        val without = items(everythingOn).indexOfCard(DashboardCardId.WEATHER)

        assertThat(withArrival).isEqualTo(without + 1)
    }

    @Test
    fun cardsThatAreTurnedOffAreNotRenderedAndOneRowNamesThem() {
        val settings = everythingOn.copy(localInfoEnabled = false, fuelPricesEnabled = false)

        val result = items(settings)

        val cards = result.cardRows().flatMap { row -> row.row.items.map { it.card } }
        assertThat(cards).containsNoneOf(DashboardCardId.LOCAL_INFO, DashboardCardId.FUEL_PRICES)
        val turnedOff = result.filterIsInstance<DashboardListItem.TurnedOff>().single()
        assertThat(turnedOff.text).isEqualTo("Turned off: Local Info, Fuel Prices")
        assertThat(turnedOff.destination).isEqualTo(SettingsDestination.LOCAL_INFO)
        assertThat(result.last()).isEqualTo(turnedOff)
    }

    @Test
    fun noTurnedOffRowWhenEveryFeatureIsOn() {
        assertThat(items(everythingOn).filterIsInstance<DashboardListItem.TurnedOff>()).isEmpty()
    }

    @Test
    fun customizeLayoutListsEveryCardIncludingTurnedOffOnesAndNoTurnedOffRow() {
        val result = items(AppSettings(), isCustomizingLayout = true)

        val cards = result.filterIsInstance<DashboardListItem.CustomizeCard>().map { it.card }
        assertThat(cards).containsExactlyElementsIn(DashboardCardId.entries)
        assertThat(result.filterIsInstance<DashboardListItem.TurnedOff>()).isEmpty()
        assertThat(result.cardRows()).isEmpty()
    }

    @Test
    fun customizeLayoutAddsCardsTheSavedOrderLacks() {
        val settings = AppSettings(dashboardCardOrder = listOf(DashboardCardId.WEATHER))

        val cards = items(settings, isCustomizingLayout = true)
            .filterIsInstance<DashboardListItem.CustomizeCard>().map { it.card }

        assertThat(cards.first()).isEqualTo(DashboardCardId.WEATHER)
        assertThat(cards).containsExactlyElementsIn(DashboardCardId.entries)
    }

    @Test
    fun aPhoneKeepsOneColumnWhateverTheStoredWidths() {
        val settings = everythingOn.copy(
            dashboardCardWidthModes = DashboardCardId.entries.associateWith { DashboardCardWidthMode.NARROW },
        )

        val rows = items(settings, isWideLayout = false).cardRows()

        assertThat(rows.map { it.row.items.size }.toSet()).containsExactly(1)
    }

    @Test
    fun aWideLayoutPairsAdjacentHalfWidthCardsAndLeavesALoneOneFull() {
        val settings = everythingOn.copy(
            dashboardCardOrder = listOf(
                DashboardCardId.CONNECTIVITY,
                DashboardCardId.POWER,
                DashboardCardId.WEATHER,
            ),
            dashboardCardWidthModes = mapOf(
                DashboardCardId.CONNECTIVITY to DashboardCardWidthMode.NARROW,
                DashboardCardId.POWER to DashboardCardWidthMode.NARROW,
                DashboardCardId.WEATHER to DashboardCardWidthMode.NARROW,
            ),
        )

        val rows = items(settings, isWideLayout = true).cardRows().map { row -> row.row.items.map { it.card } }

        assertThat(rows).containsExactly(
            listOf(DashboardCardId.CONNECTIVITY, DashboardCardId.POWER),
            listOf(DashboardCardId.WEATHER),
        ).inOrder()
    }

    @Test
    fun aCollapsedCardNeverSharesARow() {
        val settings = everythingOn.copy(
            dashboardCardOrder = listOf(DashboardCardId.CONNECTIVITY, DashboardCardId.POWER),
            dashboardCardWidthModes = mapOf(
                DashboardCardId.CONNECTIVITY to DashboardCardWidthMode.NARROW,
                DashboardCardId.POWER to DashboardCardWidthMode.NARROW,
            ),
            dashboardCollapsedCards = setOf(DashboardCardId.POWER),
        )

        val rows = items(settings, isWideLayout = true).cardRows()

        assertThat(rows.map { it.row.items.size }).containsExactly(1, 1)
    }

    @Test
    fun indexOfCardFindsTheRowOrTheCustomizeItemAndReportsMissingCards() {
        val settings = everythingOn.copy(localInfoEnabled = false)
        val normal = items(settings)
        val customizing = items(settings, isCustomizingLayout = true)

        assertThat(normal.indexOfCard(DashboardCardId.WEATHER)).isAtLeast(3)
        assertThat(normal[normal.indexOfCard(DashboardCardId.WEATHER)]).isInstanceOf(DashboardListItem.CardRow::class.java)
        assertThat(normal.indexOfCard(DashboardCardId.LOCAL_INFO)).isEqualTo(-1)
        assertThat(customizing.indexOfCard(DashboardCardId.LOCAL_INFO)).isAtLeast(3)
    }
}
