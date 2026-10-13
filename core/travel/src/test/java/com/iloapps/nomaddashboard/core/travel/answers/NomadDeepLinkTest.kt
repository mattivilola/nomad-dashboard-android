package com.iloapps.nomaddashboard.core.travel.answers

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import org.junit.Test

class NomadDeepLinkTest {
    @Test
    fun `the dashboard link round trips per scheme`() {
        for (scheme in listOf("nomaddashboard", "nomaddashboard-dev")) {
            val uri = NomadDeepLink.Dashboard().uri(scheme)

            assertThat(uri).isEqualTo("$scheme://dashboard")
            assertThat(NomadDeepLink.parse(uri, scheme)).isEqualTo(NomadDeepLink.Dashboard())
        }
    }

    @Test
    fun `the release scheme is the default`() {
        assertThat(NomadDeepLink.Dashboard().uri()).isEqualTo("nomaddashboard://dashboard")
        assertThat(NomadDeepLink.Settings().uri()).isEqualTo("nomaddashboard://settings")
        assertThat(NomadDeepLink.parse("nomaddashboard://dashboard")).isEqualTo(NomadDeepLink.Dashboard())
    }

    @Test
    fun `every dashboard card round trips`() {
        for (card in DashboardCardId.entries) {
            val link = NomadDeepLink.Dashboard(card)

            assertThat(NomadDeepLink.parse(link.uri())).isEqualTo(link)
        }
        assertThat(NomadDeepLink.Dashboard(DashboardCardId.EMBASSY).uri()).isEqualTo("nomaddashboard://dashboard/embassy")
        assertThat(NomadDeepLink.Dashboard(DashboardCardId.TRAVEL_ALERTS).uri())
            .isEqualTo("nomaddashboard://dashboard/travelAlerts")
    }

    @Test
    fun `every settings destination round trips`() {
        for (destination in SettingsDestination.entries) {
            val link = NomadDeepLink.Settings(destination)

            assertThat(NomadDeepLink.parse(link.uri())).isEqualTo(link)
        }
        assertThat(NomadDeepLink.Settings(SettingsDestination.HOME_TIME_ZONE).uri())
            .isEqualTo("nomaddashboard://settings/homeTimeZone")
    }

    @Test
    fun `names are unique within a target`() {
        val cards = DashboardCardId.entries.map { NomadDeepLink.Dashboard(it).uri().lowercase() }
        val destinations = SettingsDestination.entries.map { NomadDeepLink.Settings(it).uri().lowercase() }

        assertThat(cards).containsNoDuplicates()
        assertThat(destinations).containsNoDuplicates()
    }

    @Test
    fun `parsing ignores case and a trailing slash`() {
        assertThat(NomadDeepLink.parse("NomadDashboard://Settings/HOMETIMEZONE"))
            .isEqualTo(NomadDeepLink.Settings(SettingsDestination.HOME_TIME_ZONE))
        assertThat(NomadDeepLink.parse("nomaddashboard://dashboard/")).isEqualTo(NomadDeepLink.Dashboard())
        assertThat(NomadDeepLink.parse("nomaddashboard://dashboard/weather?source=widget#top"))
            .isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.WEATHER))
    }

    @Test
    fun `the development app ignores the release app's links`() {
        val uri = NomadDeepLink.Dashboard().uri("nomaddashboard")

        assertThat(NomadDeepLink.parse(uri, "nomaddashboard-dev")).isNull()
        assertThat(NomadDeepLink.parse(NomadDeepLink.Dashboard().uri("nomaddashboard-dev"))).isNull()
    }

    @Test
    fun `unknown targets and names are ignored`() {
        assertThat(NomadDeepLink.parse("nomaddashboard://widgets")).isNull()
        assertThat(NomadDeepLink.parse("nomaddashboard://dashboard/unknownCard")).isNull()
        assertThat(NomadDeepLink.parse("nomaddashboard://settings/unknownGroup")).isNull()
        assertThat(NomadDeepLink.parse("nomaddashboard://dashboard/weather/extra")).isNull()
        assertThat(NomadDeepLink.parse("nomaddashboard://dashboard/homeTimeZone")).isNull()
        assertThat(NomadDeepLink.parse("nomaddashboard://settings/weather")).isEqualTo(
            NomadDeepLink.Settings(SettingsDestination.WEATHER),
        )
    }

    @Test
    fun `links that are not for the app are ignored`() {
        assertThat(NomadDeepLink.parse("https://dashboard")).isNull()
        assertThat(NomadDeepLink.parse("nomaddashboard:dashboard")).isNull()
        assertThat(NomadDeepLink.parse("nomaddashboard:///dashboard")).isNull()
        assertThat(NomadDeepLink.parse("not a uri")).isNull()
        assertThat(NomadDeepLink.parse("")).isNull()
    }
}
