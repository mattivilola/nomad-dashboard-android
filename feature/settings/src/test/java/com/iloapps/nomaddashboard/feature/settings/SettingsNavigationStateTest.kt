package com.iloapps.nomaddashboard.feature.settings

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.SettingsPage
import org.junit.Test

class SettingsNavigationStateTest {
    @Test
    fun phone_starts_on_the_page_list_and_wide_layouts_start_on_the_first_page() {
        val state = SettingsNavigationState()

        assertThat(state.effectivePage(sideBySide = false)).isNull()
        assertThat(state.effectivePage(sideBySide = true)).isEqualTo(SettingsPage.GENERAL)
    }

    @Test
    fun opening_a_page_selects_it_and_back_returns_to_the_list() {
        val opened = SettingsNavigationState().openPage(SettingsPage.WORK)

        assertThat(opened.effectivePage(sideBySide = false)).isEqualTo(SettingsPage.WORK)
        assertThat(opened.focus).isNull()
        assertThat(opened.back().effectivePage(sideBySide = false)).isNull()
    }

    @Test
    fun a_destination_selects_its_page_and_asks_for_its_group_to_be_focused() {
        val state = SettingsNavigationState().openDestination(SettingsDestination.CITIZENSHIP)

        assertThat(state.selectedPage).isEqualTo(SettingsPage.GENERAL)
        assertThat(state.focus).isEqualTo(SettingsDestination.CITIZENSHIP)
        assertThat(state.focusHandled().focus).isNull()
        assertThat(state.focusHandled().selectedPage).isEqualTo(SettingsPage.GENERAL)
    }

    @Test
    fun the_same_destination_can_be_focused_again() {
        val first = SettingsNavigationState().openDestination(SettingsDestination.WEATHER).focusHandled()
        val second = first.openDestination(SettingsDestination.WEATHER)

        assertThat(second.focusRequest).isGreaterThan(first.focusRequest)
        assertThat(second.focus).isEqualTo(SettingsDestination.WEATHER)
    }

    @Test
    fun opening_a_page_by_hand_drops_a_pending_focus() {
        val state = SettingsNavigationState().openDestination(SettingsDestination.STAYS).openPage(SettingsPage.SAFETY)

        assertThat(state.focus).isNull()
        assertThat(state.selectedPage).isEqualTo(SettingsPage.SAFETY)
    }

    @Test
    fun every_destination_has_exactly_one_group_on_its_own_page() {
        SettingsDestination.entries.forEach { destination ->
            val groups = SettingsGroup.entries.filter { it.destination == destination }

            assertThat(groups).hasSize(1)
            assertThat(groups.single().page).isEqualTo(destination.page)
            assertThat(SettingsGroup.forDestination(destination)).isEqualTo(groups.single())
        }
    }

    @Test
    fun every_page_has_groups_and_they_follow_the_order_of_the_brief() {
        SettingsPage.entries.forEach { page ->
            assertThat(SettingsGroup.groupsOn(page)).isNotEmpty()
        }
        assertThat(SettingsGroup.groupsOn(SettingsPage.GENERAL)).containsExactly(
            SettingsGroup.TRAVEL_PROFILE,
            SettingsGroup.HOME_TIME_ZONE,
            SettingsGroup.CITIZENSHIP,
            SettingsGroup.APPEARANCE,
            SettingsGroup.STATUS_TILE,
            SettingsGroup.ARRIVAL_BRIEFING,
            SettingsGroup.DASHBOARD_LAYOUT,
        ).inOrder()
    }

    @Test
    fun there_are_eight_pages_with_stays_and_visas_between_safety_and_connection() {
        assertThat(SettingsPage.entries).containsExactly(
            SettingsPage.GENERAL,
            SettingsPage.WEATHER_AND_LOCAL,
            SettingsPage.SAFETY,
            SettingsPage.STAYS_AND_VISAS,
            SettingsPage.CONNECTION_AND_DATA,
            SettingsPage.WORK,
            SettingsPage.PRIVACY_AND_LOCATION,
            SettingsPage.ADVANCED,
        ).inOrder()
        assertThat(SettingsPage.STAYS_AND_VISAS.title).isEqualTo("Stays & Visas")
    }

    @Test
    fun the_stays_groups_moved_from_privacy_and_location_to_their_own_page() {
        assertThat(SettingsGroup.groupsOn(SettingsPage.STAYS_AND_VISAS)).containsExactly(
            SettingsGroup.SCHENGEN_COUNTER,
            SettingsGroup.STAY_ALLOWANCES,
            SettingsGroup.TAX_RESIDENCE_DAYS,
            SettingsGroup.STAYS_DATA,
        ).inOrder()
        assertThat(SettingsGroup.groupsOn(SettingsPage.PRIVACY_AND_LOCATION)).containsExactly(
            SettingsGroup.LOCATION_PERMISSION,
            SettingsGroup.EXTERNAL_IP,
            SettingsGroup.WIFI_NAMES,
            SettingsGroup.VISITED_PLACES,
            SettingsGroup.ANALYTICS,
        ).inOrder()
    }

    @Test
    fun the_stays_destination_opens_the_stays_page_on_the_schengen_group() {
        val state = SettingsNavigationState().openDestination(SettingsDestination.STAYS)

        assertThat(state.selectedPage).isEqualTo(SettingsPage.STAYS_AND_VISAS)
        assertThat(SettingsGroup.forDestination(SettingsDestination.STAYS)).isEqualTo(SettingsGroup.SCHENGEN_COUNTER)
    }
}
