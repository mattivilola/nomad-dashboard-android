package com.iloapps.nomaddashboard.core.travel.status

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpItem
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.iloapps.nomaddashboard.core.travel.status.StatusLinePresentation.Rule
import org.junit.Test

/** The first-match rules of the status line (macOS MenuBarStatusPresentationTests, minus Data Guard). */
class StatusLinePresentationTest {
    private val showsHeadsUp = AppSettings(statusShowsHeadsUp = true)

    @Test
    fun `battery below fifty wins over other signals`() {
        val presentation = present(batteryPercent = 49, latencyMs = 28.0, temperature = 22.0)

        assertThat(presentation).isEqualTo(StatusLinePresentation("49%", Rule.BATTERY))
    }

    @Test
    fun `battery at fifty still uses the battery rule`() {
        assertThat(present(batteryPercent = 50, latencyMs = 88.0, temperature = 22.0))
            .isEqualTo(StatusLinePresentation("50%", Rule.BATTERY))
    }

    @Test
    fun `caution latency beats the weather above fifty percent`() {
        val presentation = present(batteryPercent = 51, latencyMs = 88.0, temperature = 22.0)!!

        assertThat(presentation.text).isEqualTo("88 ms")
        assertThat(presentation.rule).isEqualTo(Rule.LATENCY_CAUTION)
        assertThat(presentation.severity).isNull()
    }

    @Test
    fun `latency of exactly sixty is a caution and above one twenty is an attention`() {
        assertThat(present(latencyMs = 60.0, temperature = 22.0)!!.rule).isEqualTo(Rule.LATENCY_CAUTION)
        assertThat(present(latencyMs = 59.0, temperature = 22.0)!!.rule).isEqualTo(Rule.WEATHER)
        assertThat(present(latencyMs = 120.0, temperature = 22.0)!!.rule).isEqualTo(Rule.LATENCY_CAUTION)

        val attention = present(latencyMs = 130.0, temperature = 22.0)!!
        assertThat(attention.text).isEqualTo("130 ms")
        assertThat(attention.rule).isEqualTo(Rule.LATENCY_ATTENTION)
        assertThat(attention.severity).isNull()
    }

    @Test
    fun `the weather wins when the battery is healthy and the latency is good`() {
        assertThat(present(batteryPercent = 51, latencyMs = 28.0, temperature = 22.0))
            .isEqualTo(StatusLinePresentation("22°", Rule.WEATHER))
    }

    @Test
    fun `offline wins over latency and weather`() {
        val presentation = present(batteryPercent = 51, offline = true, latencyMs = 28.0, temperature = 22.0)

        assertThat(presentation).isEqualTo(StatusLinePresentation("Offline", Rule.OFFLINE, HeadsUpSeverity.ATTENTION))
    }

    @Test
    fun `battery still wins over offline`() {
        val presentation = present(batteryPercent = 49, offline = true, latencyMs = 28.0, temperature = 22.0)

        assertThat(presentation).isEqualTo(StatusLinePresentation("49%", Rule.BATTERY))
    }

    @Test
    fun `a connection that is only still being checked is not offline`() {
        val checking = snapshot(connectivity = ConnectivitySnapshot(internetState = InternetState.CHECKING), temperature = 22.0)

        assertThat(StatusLinePresentation.of(checking, AppSettings(), null)!!.rule).isEqualTo(Rule.WEATHER)
    }

    @Test
    fun `latency falls back when the weather is unavailable`() {
        assertThat(present(batteryPercent = 51, latencyMs = 28.0, temperature = null))
            .isEqualTo(StatusLinePresentation("28 ms", Rule.LATENCY_FALLBACK))
    }

    @Test
    fun `an unknown battery does not block the other rules`() {
        assertThat(present(batteryPercent = null, latencyMs = null, temperature = 22.0)!!.rule).isEqualTo(Rule.WEATHER)
    }

    @Test
    fun `the temperature is formatted like the card`() {
        assertThat(present(temperature = -0.4)!!.text).isEqualTo("0°")
        assertThat(present(temperature = 19.6)!!.text).isEqualTo("20°")
    }

    @Test
    fun `a caution heads-up beats latency and weather`() {
        val presentation = present(batteryPercent = 90, latencyMs = 130.0, temperature = 22.0, headsUp = headsUp(HeadsUpSeverity.CAUTION))

        assertThat(presentation).isEqualTo(StatusLinePresentation("Wind 45", Rule.HEADS_UP, HeadsUpSeverity.CAUTION))
    }

    @Test
    fun `an attention heads-up carries the attention severity`() {
        val presentation = present(batteryPercent = 90, latencyMs = 28.0, temperature = 22.0, headsUp = headsUp(HeadsUpSeverity.ATTENTION))

        assertThat(presentation).isEqualTo(StatusLinePresentation("Wind 45", Rule.HEADS_UP, HeadsUpSeverity.ATTENTION))
    }

    @Test
    fun `an info heads-up is ignored`() {
        val withInfo = present(batteryPercent = 49, latencyMs = 28.0, temperature = 22.0, headsUp = headsUp(HeadsUpSeverity.INFO))

        assertThat(withInfo).isEqualTo(present(batteryPercent = 49, latencyMs = 28.0, temperature = 22.0))
        assertThat(withInfo!!.rule).isEqualTo(Rule.BATTERY)
        assertThat(present(latencyMs = 28.0, temperature = 22.0, headsUp = headsUp(HeadsUpSeverity.INFO))!!.rule)
            .isEqualTo(Rule.WEATHER)
    }

    @Test
    fun `the heads-up is shown only when the user turned it on`() {
        val presentation = StatusLinePresentation.of(
            snapshot(batteryPercent = 90, temperature = 22.0),
            AppSettings(statusShowsHeadsUp = false),
            headsUp(HeadsUpSeverity.ATTENTION),
        )

        assertThat(presentation!!.rule).isEqualTo(Rule.WEATHER)
    }

    @Test
    fun `battery at or below fifty percent beats the heads-up`() {
        assertThat(present(batteryPercent = 50, temperature = 22.0, headsUp = headsUp(HeadsUpSeverity.ATTENTION))!!.rule)
            .isEqualTo(Rule.BATTERY)
        assertThat(present(batteryPercent = 51, temperature = 22.0, headsUp = headsUp(HeadsUpSeverity.ATTENTION))!!.rule)
            .isEqualTo(Rule.HEADS_UP)
    }

    @Test
    fun `offline beats the heads-up`() {
        val presentation = present(batteryPercent = 90, offline = true, latencyMs = 28.0, temperature = 22.0, headsUp = headsUp(HeadsUpSeverity.ATTENTION))

        assertThat(presentation!!.rule).isEqualTo(Rule.OFFLINE)
    }

    @Test
    fun `nothing to show returns null so the caller shows the app name`() {
        assertThat(present(batteryPercent = null, latencyMs = null, temperature = null)).isNull()
        assertThat(StatusLinePresentation.of(DashboardSnapshot(), AppSettings(), null)).isNull()
    }

    private fun headsUp(severity: HeadsUpSeverity) = HeadsUpItem(
        kind = HeadsUpKind.STRONG_WIND,
        severity = severity,
        title = "Wind up to 45 km/h",
        statusText = "Wind 45",
        detail = "From W now",
        relatedCard = DashboardCardId.WEATHER,
    )

    private fun present(
        batteryPercent: Int? = 90,
        offline: Boolean = false,
        latencyMs: Double? = null,
        temperature: Double? = null,
        headsUp: HeadsUpItem? = null,
    ): StatusLinePresentation? = StatusLinePresentation.of(
        snapshot(batteryPercent, offline, latencyMs, temperature),
        showsHeadsUp,
        headsUp,
    )

    private fun snapshot(
        batteryPercent: Int? = 90,
        offline: Boolean = false,
        latencyMs: Double? = null,
        temperature: Double? = null,
        connectivity: ConnectivitySnapshot = ConnectivitySnapshot(
            internetState = if (offline) InternetState.OFFLINE else InternetState.ONLINE,
            isOnline = !offline,
            latencyMs = latencyMs,
        ),
    ) = DashboardSnapshot(
        connectivity = connectivity,
        power = PowerSnapshot(batteryPercent = batteryPercent),
        weather = WeatherSnapshot(currentTemperatureCelsius = temperature),
    )
}
