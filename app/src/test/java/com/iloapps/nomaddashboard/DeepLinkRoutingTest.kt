package com.iloapps.nomaddashboard

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.answers.NomadDeepLink
import com.iloapps.nomaddashboard.feature.dashboard.DashboardScrollTarget
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test

class DeepLinkRoutingTest {
    private val view = "android.intent.action.VIEW"

    @Test
    fun `a dashboard link opens the dashboard`() {
        assertThat(NomadDeepLink.Dashboard().toRoute()).isEqualTo(DeepLinkRoute.Dashboard(scrollTarget = null))
    }

    @Test
    fun `a card link scrolls the dashboard to that card`() {
        for (card in DashboardCardId.entries) {
            assertThat(NomadDeepLink.Dashboard(card).toRoute())
                .isEqualTo(DeepLinkRoute.Dashboard(DashboardScrollTarget(card)))
        }
    }

    @Test
    fun `a settings link opens that group, and plain settings opens the page list`() {
        assertThat(NomadDeepLink.Settings().toRoute()).isEqualTo(DeepLinkRoute.Settings(destination = null))
        for (destination in SettingsDestination.entries) {
            assertThat(NomadDeepLink.Settings(destination).toRoute()).isEqualTo(DeepLinkRoute.Settings(destination))
        }
    }

    @Test
    fun `an intent is a link only when it is a VIEW intent with one of our uris`() {
        assertThat(nomadDeepLinkOf(view, "nomaddashboard://dashboard/embassy"))
            .isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.EMBASSY))
        assertThat(nomadDeepLinkOf(view, "nomaddashboard://settings/travelProfile"))
            .isEqualTo(NomadDeepLink.Settings(SettingsDestination.TRAVEL_PROFILE))

        assertThat(nomadDeepLinkOf(null, "nomaddashboard://dashboard")).isNull()
        assertThat(nomadDeepLinkOf("android.intent.action.MAIN", "nomaddashboard://dashboard")).isNull()
        assertThat(nomadDeepLinkOf(view, null)).isNull()
        assertThat(nomadDeepLinkOf(view, "https://example.com/dashboard")).isNull()
        assertThat(nomadDeepLinkOf(view, "nomaddashboard://dashboard/unknownCard")).isNull()
        assertThat(nomadDeepLinkOf(view, "nomaddashboard://elsewhere")).isNull()
    }

    @Test
    fun `the launcher shortcuts open the card or setting that shows the answer`() {
        val routes = shortcutRoutes()

        assertThat(routes.keys).containsExactly(
            "emergency_number",
            "dashboard_status",
            "nearest_embassy",
            "daylight_left",
            "switch_travel_profile",
        )
        assertThat(routes["emergency_number"]).isEqualTo(DeepLinkRoute.Dashboard(DashboardScrollTarget(DashboardCardId.EMERGENCY_CARE)))
        assertThat(routes["dashboard_status"]).isEqualTo(DeepLinkRoute.Dashboard(null))
        assertThat(routes["nearest_embassy"]).isEqualTo(DeepLinkRoute.Dashboard(DashboardScrollTarget(DashboardCardId.EMBASSY)))
        assertThat(routes["daylight_left"]).isEqualTo(DeepLinkRoute.Dashboard(DashboardScrollTarget(DashboardCardId.WEATHER)))
        assertThat(routes["switch_travel_profile"]).isEqualTo(DeepLinkRoute.Settings(SettingsDestination.TRAVEL_PROFILE))
    }

    /** The route each static shortcut's intent resolves to, by shortcut id. */
    private fun shortcutRoutes(): Map<String, DeepLinkRoute?> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File("src/main/res/xml/shortcuts.xml"))
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val shortcuts = document.getElementsByTagName("shortcut")
        return (0 until shortcuts.length).associate { index ->
            val shortcut = shortcuts.item(index) as org.w3c.dom.Element
            val intent = shortcut.getElementsByTagName("intent").item(0) as org.w3c.dom.Element
            val link = nomadDeepLinkOf(
                action = intent.getAttributeNS(androidNamespace, "action"),
                dataString = intent.getAttributeNS(androidNamespace, "data"),
            )
            shortcut.getAttributeNS(androidNamespace, "shortcutId") to link?.toRoute()
        }
    }
}
