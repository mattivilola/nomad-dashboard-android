package com.iloapps.nomaddashboard.feature.widgets

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.answers.NomadDeepLink
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.widget.DaylightWidgetContent
import com.iloapps.nomaddashboard.core.travel.widget.SafetyWidgetContent
import com.iloapps.nomaddashboard.core.travel.widget.WidgetUpdatedFooter
import org.junit.Test

class WidgetLinksTest {
    private val fresh = WidgetUpdatedFooter(text = "Updated 12:40", accessibilityLabel = "Updated 12:40", isStale = false)

    private fun safety(hotline: String?, hint: String?) = SafetyWidgetContent(
        emergencyLabel = "Emergency",
        emergencyNumber = "112",
        countryName = "Portugal",
        unknownTitle = null,
        unknownDetail = null,
        consularLabel = "Consular line",
        hotline = hotline,
        citizenshipHint = hint,
        accessibilityLabel = "Emergency number in Portugal: 112",
        footer = fresh,
    )

    private fun daylight(kind: DaylightWidgetContent.Kind) = DaylightWidgetContent(
        kind = kind,
        title = "t",
        detail = "d",
        isCaution = false,
        footer = fresh,
    )

    @Test
    fun `a heads-up opens the card the dashboard scrolls to for it`() {
        assertThat(WidgetLinks.headsUp(HeadsUpKind.STRONG_WIND)).isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.WEATHER))
        assertThat(WidgetLinks.headsUp(HeadsUpKind.SCHENGEN_LIMIT)).isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.STAYS))
        assertThat(WidgetLinks.headsUp(HeadsUpKind.PUBLIC_HOLIDAY)).isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.LOCAL_INFO))
        assertThat(WidgetLinks.headsUp(HeadsUpKind.FUEL_NEARBY)).isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.FUEL_PRICES))
        assertThat(WidgetLinks.headsUp(HeadsUpKind.TRAVEL_ADVISORY)).isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.TRAVEL_ALERTS))
    }

    @Test
    fun `every heads-up kind has a card`() {
        for (kind in HeadsUpKind.entries) {
            assertThat(WidgetLinks.card(kind)).isNotNull()
        }
    }

    @Test
    fun `a kind from a newer build opens the dashboard`() {
        assertThat(WidgetLinks.headsUp(null)).isEqualTo(NomadDeepLink.Dashboard())
    }

    @Test
    fun `the safety widget opens the emergency card and the consular line opens the embassy card`() {
        assertThat(WidgetLinks.emergency).isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.EMERGENCY_CARE))
        assertThat(WidgetLinks.consular(safety(hotline = "+358 9 1605 5555", hint = null)))
            .isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.EMBASSY))
    }

    @Test
    fun `without a citizenship the consular line opens the citizenship setting`() {
        assertThat(WidgetLinks.consular(safety(hotline = null, hint = "Add your citizenship in Settings")))
            .isEqualTo(NomadDeepLink.Settings(SettingsDestination.CITIZENSHIP))
    }

    @Test
    fun `the daylight widget opens the weather card unless it still needs a location`() {
        assertThat(WidgetLinks.daylight(daylight(DaylightWidgetContent.Kind.DAYLIGHT)))
            .isEqualTo(NomadDeepLink.Dashboard(DashboardCardId.WEATHER))
        assertThat(WidgetLinks.daylight(daylight(DaylightWidgetContent.Kind.LOCATION_NEEDED)))
            .isEqualTo(NomadDeepLink.Dashboard())
    }

    @Test
    fun `every link survives the trip through its uri`() {
        val links = listOf(
            WidgetLinks.dashboard,
            WidgetLinks.emergency,
            WidgetLinks.consular(safety("+1", null)),
            WidgetLinks.consular(safety(null, "hint")),
        ) + HeadsUpKind.entries.map(WidgetLinks::headsUp)

        for (link in links) {
            assertThat(NomadDeepLink.parse(link.uri())).isEqualTo(link)
        }
    }
}
