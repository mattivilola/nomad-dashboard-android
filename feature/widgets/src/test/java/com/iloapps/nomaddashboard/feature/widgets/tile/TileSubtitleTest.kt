package com.iloapps.nomaddashboard.feature.widgets.tile

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.status.StatusLinePresentation
import org.junit.Test

class TileSubtitleTest {
    private val appName = "Nomad Dashboard"

    private fun snapshot(
        battery: Int? = 80,
        state: InternetState = InternetState.ONLINE,
        latency: Double? = 24.0,
        temperature: Double? = 20.0,
    ) = DashboardSnapshot(
        connectivity = ConnectivitySnapshot(internetState = state, isOnline = state == InternetState.ONLINE, latencyMs = latency),
        power = PowerSnapshot(batteryPercent = battery),
        weather = WeatherSnapshot(currentTemperatureCelsius = temperature),
    )

    private fun wind(severity: HeadsUpSeverity = HeadsUpSeverity.CAUTION) = HeadsUpItem(
        kind = HeadsUpKind.STRONG_WIND,
        severity = severity,
        title = "Wind up to 45 km/h",
        statusText = "Wind 45",
        detail = null,
        relatedCard = DashboardCardId.WEATHER,
    )

    private fun subtitle(snapshot: DashboardSnapshot, settings: AppSettings = AppSettings(), headsUp: HeadsUpItem? = null) =
        TileSubtitle.of(StatusLinePresentation.of(snapshot, settings, headsUp), appName)

    @Test
    fun `nothing to say shows the app name`() {
        assertThat(TileSubtitle.of(null, appName)).isEqualTo(appName)
        assertThat(subtitle(snapshot(temperature = null, latency = null))).isEqualTo(appName)
    }

    @Test
    fun `a blank status text shows the app name`() {
        assertThat(TileSubtitle.of(StatusLinePresentation("  ", StatusLinePresentation.Rule.WEATHER), appName)).isEqualTo(appName)
    }

    @Test
    fun `the status line text is shown as it is`() {
        assertThat(TileSubtitle.of(StatusLinePresentation("Wind 45", StatusLinePresentation.Rule.HEADS_UP), appName))
            .isEqualTo("Wind 45")
    }

    @Test
    fun `a low battery comes first`() {
        assertThat(subtitle(snapshot(battery = 30, state = InternetState.OFFLINE), headsUp = wind())).isEqualTo("30%")
    }

    @Test
    fun `offline comes before the heads-up`() {
        assertThat(subtitle(snapshot(state = InternetState.OFFLINE), AppSettings(statusShowsHeadsUp = true), wind()))
            .isEqualTo("Offline")
    }

    @Test
    fun `the top heads-up shows only when the user turned it on and it is a caution or worse`() {
        assertThat(subtitle(snapshot(), AppSettings(statusShowsHeadsUp = true), wind())).isEqualTo("Wind 45")
        assertThat(subtitle(snapshot(), AppSettings(statusShowsHeadsUp = false), wind())).isEqualTo("20°")
        assertThat(subtitle(snapshot(), AppSettings(statusShowsHeadsUp = true), wind(HeadsUpSeverity.INFO))).isEqualTo("20°")
    }

    @Test
    fun `a slow connection shows its latency and otherwise the temperature`() {
        assertThat(subtitle(snapshot(latency = 85.0))).isEqualTo("85 ms")
        assertThat(subtitle(snapshot(latency = 24.0))).isEqualTo("20°")
    }
}
