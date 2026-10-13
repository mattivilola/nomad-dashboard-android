package com.iloapps.nomaddashboard.core.travel.summary

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardCardId.CONNECTIVITY
import com.iloapps.nomaddashboard.core.model.DashboardCardId.POWER
import com.iloapps.nomaddashboard.core.model.DashboardCardId.WEATHER
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode.NARROW
import com.iloapps.nomaddashboard.core.model.DashboardCardWidthMode.WIDE
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.TravelProfile
import org.junit.Test

class DashboardCardLayoutTest {
    // region Packing

    @Test
    fun `two adjacent narrow cards share a row at half width`() {
        val rows = DashboardCardPacking.rows(
            order = listOf(CONNECTIVITY, POWER, WEATHER),
            widthModes = mapOf(CONNECTIVITY to NARROW, POWER to NARROW),
        )

        assertThat(rows).hasSize(2)
        assertThat(rows[0].items.map { it.card }).containsExactly(CONNECTIVITY, POWER).inOrder()
        assertThat(rows[0].items.map { it.renderWidth }).containsExactly(DashboardCardRenderWidth.HALF, DashboardCardRenderWidth.HALF)
        assertThat(rows[0].items.map { it.layoutWidthMode }).containsExactly(NARROW, NARROW)
        assertThat(rows[0].id).isEqualTo("CONNECTIVITY|POWER")
    }

    @Test
    fun `a narrow card without a narrow partner renders wide at full width but keeps its preference`() {
        val rows = DashboardCardPacking.rows(
            order = listOf(CONNECTIVITY, POWER),
            widthModes = mapOf(CONNECTIVITY to NARROW, POWER to WIDE),
        )

        assertThat(rows).hasSize(2)
        val lone = rows[0].items.single()
        assertThat(lone.card).isEqualTo(CONNECTIVITY)
        assertThat(lone.renderWidth).isEqualTo(DashboardCardRenderWidth.FULL)
        assertThat(lone.layoutWidthMode).isEqualTo(WIDE)
        assertThat(lone.preferredWidthMode).isEqualTo(NARROW)
    }

    @Test
    fun `a trailing narrow card renders wide`() {
        val rows = DashboardCardPacking.rows(listOf(CONNECTIVITY, POWER), mapOf(POWER to NARROW))

        assertThat(rows.map { it.items.size }).containsExactly(1, 1)
        assertThat(rows[1].items.single().layoutWidthMode).isEqualTo(WIDE)
    }

    @Test
    fun `three narrow cards pack a pair and leave the third wide`() {
        val rows = DashboardCardPacking.rows(
            order = listOf(CONNECTIVITY, POWER, WEATHER),
            widthModes = mapOf(CONNECTIVITY to NARROW, POWER to NARROW, WEATHER to NARROW),
        )

        assertThat(rows.map { it.items.size }).containsExactly(2, 1)
        assertThat(rows[1].items.single().card).isEqualTo(WEATHER)
        assertThat(rows[1].items.single().layoutWidthMode).isEqualTo(WIDE)
    }

    @Test
    fun `cards default to wide`() {
        val rows = DashboardCardPacking.rows(listOf(CONNECTIVITY, POWER), emptyMap())

        assertThat(rows.map { it.items.size }).containsExactly(1, 1)
        assertThat(rows.flatMap { it.items }.all { it.layoutWidthMode == WIDE }).isTrue()
    }

    @Test
    fun `a collapsed narrow card never shares a row`() {
        val rows = DashboardCardPacking.rows(
            order = listOf(CONNECTIVITY, POWER, WEATHER),
            widthModes = mapOf(CONNECTIVITY to NARROW, POWER to NARROW, WEATHER to NARROW),
            collapsed = setOf(CONNECTIVITY),
        )

        // Connectivity is a one-line summary, so Power and Weather pair up on their own.
        assertThat(rows.map { it.items.size }).containsExactly(1, 2)
        assertThat(rows[0].items.single().card).isEqualTo(CONNECTIVITY)
        assertThat(rows[0].items.single().renderWidth).isEqualTo(DashboardCardRenderWidth.FULL)
        assertThat(rows[0].items.single().preferredWidthMode).isEqualTo(NARROW)
        assertThat(rows[1].items.map { it.card }).containsExactly(POWER, WEATHER).inOrder()
    }

    @Test
    fun `collapsing one half of a pair leaves the other full width`() {
        val rows = DashboardCardPacking.rows(
            order = listOf(CONNECTIVITY, POWER),
            widthModes = mapOf(CONNECTIVITY to NARROW, POWER to NARROW),
            collapsed = setOf(POWER),
        )

        assertThat(rows.map { it.items.size }).containsExactly(1, 1)
        assertThat(rows.flatMap { it.items }.all { it.layoutWidthMode == WIDE }).isTrue()
    }

    // endregion

    // region Visibility

    private fun settings(configure: (AppSettings) -> AppSettings = { it }) =
        configure(
            AppSettings(
                projectTimeTrackingEnabled = true,
                localInfoEnabled = false,
                fuelPricesEnabled = false,
                emergencyCareEnabled = true,
                citizenshipCountryCodes = listOf("FI"),
            ),
        )

    @Test
    fun `turned off cards leave the grid and are named in dashboard order`() {
        val visibility = DashboardCardVisibility.of(DashboardCardId.defaultOrder, settings(), isCustomizingLayout = false)

        assertThat(visibility.visibleOrder).doesNotContain(DashboardCardId.LOCAL_INFO)
        assertThat(visibility.visibleOrder).doesNotContain(DashboardCardId.FUEL_PRICES)
        assertThat(visibility.visibleOrder).contains(CONNECTIVITY)
        assertThat(visibility.visibleOrder).contains(WEATHER)
        assertThat(visibility.turnedOffText).isEqualTo("Turned off: Local Info, Fuel Prices")
        assertThat(visibility.turnedOffCards).containsExactly(DashboardCardId.LOCAL_INFO, DashboardCardId.FUEL_PRICES).inOrder()
        assertThat(visibility.turnedOffSettingsDestination).isEqualTo(SettingsDestination.LOCAL_INFO)
    }

    @Test
    fun `the embassy card is named as turned off until a citizenship is chosen`() {
        val visibility = DashboardCardVisibility.of(
            DashboardCardId.defaultOrder,
            settings { it.copy(citizenshipCountryCodes = emptyList()) },
            isCustomizingLayout = false,
        )

        assertThat(visibility.visibleOrder).doesNotContain(DashboardCardId.EMBASSY)
        assertThat(visibility.turnedOffText).isEqualTo("Turned off: Local Info, Fuel Prices, Your Embassy")
    }

    @Test
    fun `the settings button opens the group of the first turned off card`() {
        val onlyEmbassy = DashboardCardVisibility.of(
            DashboardCardId.defaultOrder,
            settings { it.copy(localInfoEnabled = true, fuelPricesEnabled = true, citizenshipCountryCodes = emptyList()) },
            isCustomizingLayout = false,
        )
        val timeTracking = DashboardCardVisibility.of(
            DashboardCardId.defaultOrder,
            settings { it.copy(projectTimeTrackingEnabled = false) },
            isCustomizingLayout = false,
        )

        assertThat(onlyEmbassy.turnedOffSettingsDestination).isEqualTo(SettingsDestination.CITIZENSHIP)
        assertThat(timeTracking.turnedOffSettingsDestination).isEqualTo(SettingsDestination.TIME_TRACKING)
    }

    @Test
    fun `the stays card is turned off when saving visited places is off`() {
        val visibility = DashboardCardVisibility.of(
            DashboardCardId.defaultOrder,
            settings { it.copy(visitedPlacesEnabled = false) },
            isCustomizingLayout = false,
        )

        assertThat(visibility.turnedOffText).isEqualTo("Turned off: Stays & Visas, Local Info, Fuel Prices")
        assertThat(visibility.turnedOffSettingsDestination).isEqualTo(SettingsDestination.VISITED_PLACES)
    }

    @Test
    fun `customizing shows every card and no turned off row`() {
        val visibility = DashboardCardVisibility.of(DashboardCardId.defaultOrder, settings(), isCustomizingLayout = true)

        assertThat(visibility.visibleOrder).isEqualTo(DashboardCardId.defaultOrder)
        assertThat(visibility.turnedOffText).isNull()
        assertThat(visibility.turnedOffSettingsDestination).isNull()
    }

    @Test
    fun `nothing is turned off when every feature is on`() {
        val visibility = DashboardCardVisibility.of(
            DashboardCardId.defaultOrder,
            settings { it.copy(localInfoEnabled = true, fuelPricesEnabled = true) },
            isCustomizingLayout = false,
        )

        assertThat(visibility.visibleOrder).isEqualTo(DashboardCardId.defaultOrder)
        assertThat(visibility.turnedOffText).isNull()
    }

    // endregion

    // region Travel profile

    @Test
    fun `footer is the profile summary when the recommended order is in use`() {
        TravelProfile.entries.forEach { profile ->
            val presentation = TravelProfileSettingsPresentation(profile, isUsingRecommendedOrder = true)

            assertThat(presentation.footer).isEqualTo(profile.summary)
            assertThat(presentation.offersRecommendedOrder).isFalse()
        }
    }

    @Test
    fun `footer mentions the kept custom order and offers the recommended one when customized`() {
        TravelProfile.entries.forEach { profile ->
            val presentation = TravelProfileSettingsPresentation(profile, isUsingRecommendedOrder = false)

            assertThat(presentation.footer).isEqualTo("${profile.summary} Your custom card order is kept.")
            assertThat(presentation.offersRecommendedOrder).isTrue()
        }
    }

    @Test
    fun `the recommended order is the profile's default card order`() {
        TravelProfile.entries.forEach { profile ->
            val presentation = TravelProfileSettingsPresentation(profile, isUsingRecommendedOrder = false)

            assertThat(presentation.recommendedOrder).isEqualTo(DashboardCardId.defaultOrder(profile))
        }
    }

    @Test
    fun `profile titles and summaries are the macOS wording`() {
        assertThat(TravelProfile.NOMAD.title).isEqualTo("Nomad")
        assertThat(TravelProfile.NOMAD.summary).isEqualTo("Puts connection quality, work time and city context first.")
        assertThat(TravelProfile.ROAD_TRIP.title).isEqualTo("Road trip & RV")
        assertThat(TravelProfile.ROAD_TRIP.summary)
            .isEqualTo("Puts weather, fuel, emergency care, power and data allowance first.")
        assertThat(TravelProfileSettingsPresentation.USE_RECOMMENDED_ORDER_TITLE).isEqualTo("Use recommended order")
    }

    // endregion
}
