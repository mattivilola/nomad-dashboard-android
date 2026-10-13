package com.iloapps.nomaddashboard.feature.settings

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import org.junit.Test

class SettingsRoutesTest {
    @Test
    fun without_a_destination_the_route_is_the_plain_settings_route() {
        assertThat(settingsRoute()).isEqualTo("settings")
        assertThat(settingsRoute(null)).isEqualTo("settings")
    }

    @Test
    fun a_destination_travels_as_its_name() {
        assertThat(settingsRoute(SettingsDestination.HOME_TIME_ZONE)).isEqualTo("settings?destination=HOME_TIME_ZONE")
    }

    @Test
    fun the_argument_round_trips_for_every_destination() {
        SettingsDestination.entries.forEach { destination ->
            assertThat(settingsDestinationFromArgument(destination.name)).isEqualTo(destination)
        }
    }

    @Test
    fun an_unknown_or_missing_argument_is_no_destination() {
        assertThat(settingsDestinationFromArgument(null)).isNull()
        assertThat(settingsDestinationFromArgument("")).isNull()
        assertThat(settingsDestinationFromArgument("nope")).isNull()
    }

    @Test
    fun the_pattern_declares_the_optional_argument() {
        assertThat(SettingsRoutePattern).isEqualTo("settings?destination={destination}")
    }
}
