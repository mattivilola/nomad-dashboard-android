package com.iloapps.nomaddashboard.core.travel.health

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import org.junit.Test

class DashboardHealthEvaluatorTest {
    private fun connectivity(
        latency: Double? = 24.0,
        jitter: Double? = 3.0,
        rssi: Int? = -56,
        wifiName: String? = "Nomad Hub",
        internetState: InternetState = InternetState.ONLINE,
    ) = ConnectivitySnapshot(
        internetState = internetState,
        isOnline = internetState == InternetState.ONLINE,
        latencyMs = latency,
        jitterMs = jitter,
        wifiName = wifiName,
        wifiSignalDbm = rssi,
    )

    private fun battery(
        percent: Int? = 81,
        minutes: Int? = 210,
        powerSave: Boolean = false,
    ) = PowerSnapshot(
        batteryPercent = percent,
        statusLabel = "On Battery",
        powerSaveMode = powerSave,
        minutesRemaining = minutes,
    )

    private fun evaluate(
        connectivity: ConnectivitySnapshot = connectivity(),
        power: PowerSnapshot = battery(),
    ) = DashboardHealthEvaluator.evaluate(connectivity, power)

    @Test
    fun `healthy telemetry produces a ready summary`() {
        val summary = evaluate()

        assertThat(summary.network.level).isEqualTo(HealthLevel.READY)
        assertThat(summary.network.reason).isEqualTo("Stable on Nomad Hub")
        assertThat(summary.power.level).isEqualTo(HealthLevel.READY)
        assertThat(summary.power.reason).isEqualTo("3h 30m remaining")
        assertThat(summary.overall.level).isEqualTo(HealthLevel.READY)
        assertThat(summary.overall.reason).isEqualTo("Network and power look good")
    }

    @Test
    fun `network reason without a wifi name stays generic`() {
        assertThat(evaluate(connectivity(wifiName = null)).network.reason).isEqualTo("Connection looks stable")
        assertThat(evaluate(connectivity(wifiName = "  ")).network.reason).isEqualTo("Connection looks stable")
    }

    @Test
    fun `latency thresholds are 60 ms caution and above 120 ms attention`() {
        fun level(latency: Double) = evaluate(connectivity(latency = latency, jitter = null)).network.level

        assertThat(level(59.9)).isEqualTo(HealthLevel.READY)
        assertThat(level(60.0)).isEqualTo(HealthLevel.CAUTION)
        assertThat(level(120.0)).isEqualTo(HealthLevel.CAUTION)
        assertThat(level(120.1)).isEqualTo(HealthLevel.ATTENTION)
    }

    @Test
    fun `elevated latency triggers a network caution`() {
        val summary = evaluate(connectivity(latency = 85.0, jitter = 4.0))

        assertThat(summary.network.level).isEqualTo(HealthLevel.CAUTION)
        assertThat(summary.network.reason).isEqualTo("Latency 85 ms")
        assertThat(summary.overall.level).isEqualTo(HealthLevel.CAUTION)
        assertThat(summary.overall.reason).isEqualTo("Network: Latency 85 ms")
    }

    @Test
    fun `jitter thresholds are 10 ms caution and above 25 ms attention`() {
        fun level(jitter: Double) = evaluate(connectivity(latency = 28.0, jitter = jitter)).network.level

        assertThat(level(9.9)).isEqualTo(HealthLevel.READY)
        assertThat(level(10.0)).isEqualTo(HealthLevel.CAUTION)
        assertThat(level(25.0)).isEqualTo(HealthLevel.CAUTION)
        assertThat(level(25.1)).isEqualTo(HealthLevel.ATTENTION)
    }

    @Test
    fun `high jitter triggers a network attention`() {
        val summary = evaluate(connectivity(latency = 28.0, jitter = 31.0))

        assertThat(summary.network.level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(summary.network.reason).isEqualTo("Jitter 31 ms")
        assertThat(summary.overall.level).isEqualTo(HealthLevel.ATTENTION)
    }

    @Test
    fun `a weak wifi signal is a caution from minus 70 dBm`() {
        assertThat(evaluate(connectivity(rssi = -69)).network.level).isEqualTo(HealthLevel.READY)

        val weak = evaluate(connectivity(rssi = -70)).network
        assertThat(weak.level).isEqualTo(HealthLevel.CAUTION)
        assertThat(weak.reason).isEqualTo("Weak Wi-Fi signal")
        assertThat(evaluate(connectivity(rssi = null)).network.level).isEqualTo(HealthLevel.READY)
    }

    @Test
    fun `offline is an attention without a latency sample`() {
        val summary = evaluate(connectivity(latency = null, jitter = null, internetState = InternetState.OFFLINE))

        assertThat(summary.network.level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(summary.network.reason).isEqualTo("No network route")
        assertThat(summary.overall.level).isEqualTo(HealthLevel.ATTENTION)
    }

    @Test
    fun `an unvalidated network is a caution not an endless wait`() {
        val summary = evaluate(connectivity(latency = null, jitter = null, internetState = InternetState.CAPTIVE_OR_UNVALIDATED))

        assertThat(summary.network.level).isEqualTo(HealthLevel.CAUTION)
        assertThat(summary.network.reason).isEqualTo("Network needs sign-in or isn't verified")
        assertThat(summary.overall.level).isEqualTo(HealthLevel.CAUTION)
        assertThat(summary.overall.reason).isEqualTo("Network needs sign-in or isn't verified")
    }

    @Test
    fun `an unvalidated network stays a caution even with a latency reading`() {
        val summary = evaluate(connectivity(latency = 20.0, internetState = InternetState.CAPTIVE_OR_UNVALIDATED))

        assertThat(summary.network.reason).isEqualTo("Network needs sign-in or isn't verified")
    }

    @Test
    fun `battery attention still outranks an unvalidated network`() {
        val summary = evaluate(
            connectivity(latency = null, internetState = InternetState.CAPTIVE_OR_UNVALIDATED),
            battery(percent = 10, minutes = 20),
        )

        assertThat(summary.overall.level).isEqualTo(HealthLevel.ATTENTION)
    }

    @Test
    fun `a checking connection with no reading yet is still waiting`() {
        val summary = evaluate(connectivity(latency = null, jitter = null, internetState = InternetState.CHECKING))

        assertThat(summary.network.level).isEqualTo(HealthLevel.UNAVAILABLE)
        assertThat(summary.network.reason).isEqualTo("Collecting network quality")
    }

    @Test
    fun `battery thresholds are attention below 25 percent or 45 minutes`() {
        fun level(percent: Int?, minutes: Int?) = evaluate(power = battery(percent, minutes)).power

        assertThat(level(24, 300).level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(level(24, 300).reason).isEqualTo("Battery 24%")
        assertThat(level(60, 44).level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(level(60, 44).reason).isEqualTo("44m remaining")
        assertThat(level(25, 300).level).isEqualTo(HealthLevel.CAUTION)
        assertThat(level(60, 45).level).isEqualTo(HealthLevel.CAUTION)
    }

    @Test
    fun `battery caution is 50 percent or 120 minutes or battery saver`() {
        fun power(percent: Int?, minutes: Int?, saver: Boolean = false) =
            evaluate(power = battery(percent, minutes, saver)).power

        assertThat(power(50, 300).level).isEqualTo(HealthLevel.CAUTION)
        assertThat(power(50, 300).reason).isEqualTo("Battery 50%")
        assertThat(power(51, 300).level).isEqualTo(HealthLevel.READY)
        assertThat(power(80, 120).level).isEqualTo(HealthLevel.CAUTION)
        assertThat(power(80, 120).reason).isEqualTo("2h 0m remaining")
        assertThat(power(80, 121).level).isEqualTo(HealthLevel.READY)
        assertThat(power(80, 300, saver = true).level).isEqualTo(HealthLevel.CAUTION)
        assertThat(power(80, 300, saver = true).reason).isEqualTo("Battery Saver enabled")
    }

    @Test
    fun `ready battery reason falls back to the percentage and then a generic line`() {
        assertThat(evaluate(power = battery(81, null)).power.reason).isEqualTo("Battery 81%")
    }

    @Test
    fun `low battery triggers a power attention`() {
        val summary = evaluate(power = battery(20, 38))

        assertThat(summary.power.level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(summary.overall.level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(summary.overall.reason).isEqualTo("Power: Battery 20%")
    }

    @Test
    fun `charging and plugged in are ready unless battery saver is on`() {
        val charging = PowerSnapshot(batteryPercent = 10, charging = true, statusLabel = "Charging")
        val full = PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Full")
        val pluggedIn = PowerSnapshot(batteryPercent = 90, statusLabel = "Plugged In")

        assertThat(evaluate(power = charging).power.reason).isEqualTo("Charging on external power")
        assertThat(evaluate(power = charging).power.level).isEqualTo(HealthLevel.READY)
        assertThat(evaluate(power = full).power.reason).isEqualTo("Connected to power")
        assertThat(evaluate(power = pluggedIn).power.reason).isEqualTo("Connected to power")
        assertThat(evaluate(power = charging.copy(powerSaveMode = true)).power.level).isEqualTo(HealthLevel.CAUTION)
        assertThat(evaluate(power = full.copy(powerSaveMode = true)).power.reason).isEqualTo("Battery Saver enabled")
    }

    @Test
    fun `attention outranks caution and network outranks power`() {
        val bothAttention = evaluate(connectivity(latency = 200.0), battery(10, 10))
        assertThat(bothAttention.overall.reason).isEqualTo("Network: Latency 200 ms")

        val powerAttention = evaluate(connectivity(latency = 80.0), battery(10, 10))
        assertThat(powerAttention.overall.level).isEqualTo(HealthLevel.ATTENTION)
        assertThat(powerAttention.overall.reason).isEqualTo("Power: Battery 10%")

        val bothCaution = evaluate(connectivity(latency = 80.0), battery(40, 300))
        assertThat(bothCaution.overall.reason).isEqualTo("Network: Latency 80 ms")
    }

    @Test
    fun `missing samples stay unavailable until data arrives`() {
        val summary = DashboardHealthEvaluator.evaluate(ConnectivitySnapshot(), PowerSnapshot())

        assertThat(summary.network.level).isEqualTo(HealthLevel.UNAVAILABLE)
        assertThat(summary.network.reason).isEqualTo("Collecting network quality")
        assertThat(summary.power.level).isEqualTo(HealthLevel.UNAVAILABLE)
        assertThat(summary.power.reason).isEqualTo("Collecting power status")
        assertThat(summary.overall.level).isEqualTo(HealthLevel.UNAVAILABLE)
        assertThat(summary.overall.reason).isEqualTo("Network: Collecting network quality")
    }

    @Test
    fun `a battery that cannot be read is unavailable with its own reason`() {
        val power = PowerSnapshot(batteryPercent = null, statusLabel = "Idle")

        assertThat(evaluate(power = power).power.reason).isEqualTo("Power source unavailable")
        assertThat(evaluate(power = power).power.level).isEqualTo(HealthLevel.UNAVAILABLE)
    }
}
