package com.iloapps.nomaddashboard.core.travel.health

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import org.junit.Test

class PowerMetricsPresentationTest {
    @Test
    fun `power state is rebuilt from android's battery status`() {
        assertThat(PowerSnapshot(batteryPercent = 50, statusLabel = "On Battery").powerState()).isEqualTo(PowerState.BATTERY)
        assertThat(PowerSnapshot(batteryPercent = 50, statusLabel = "Idle").powerState()).isEqualTo(PowerState.BATTERY)
        assertThat(PowerSnapshot(batteryPercent = 50, charging = true, statusLabel = "Charging").powerState())
            .isEqualTo(PowerState.CHARGING)
        assertThat(PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Full").powerState())
            .isEqualTo(PowerState.CHARGED)
        assertThat(PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Charging").powerState())
            .isEqualTo(PowerState.CHARGED)
        assertThat(PowerSnapshot(batteryPercent = 80, statusLabel = "Plugged In").powerState()).isEqualTo(PowerState.CHARGED)
        assertThat(PowerSnapshot().powerState()).isEqualTo(PowerState.UNKNOWN)
        assertThat(PowerSnapshot().isCollecting).isTrue()
        assertThat(PowerSnapshot(statusLabel = "Idle").isCollecting).isFalse()
    }

    @Test
    fun `on battery the metrics show drain and time left`() {
        val metrics = PowerMetricsPresentation.of(
            PowerSnapshot(batteryPercent = 72, statusLabel = "On Battery", dischargeWatts = 11.4, minutesRemaining = 208),
        )

        assertThat(metrics.drainTitle).isEqualTo("Drain")
        assertThat(metrics.drainReading?.number).isEqualTo("11.4")
        assertThat(metrics.drainReading?.unit).isEqualTo("W")
        assertThat(metrics.timeLeftTitle).isEqualTo("Time Left")
        assertThat(metrics.timeLeftValue).isEqualTo("3h 28m")
        assertThat(metrics.summary).isEqualTo("3h 28m left")
    }

    @Test
    fun `on battery without estimates says so`() {
        val metrics = PowerMetricsPresentation.of(PowerSnapshot(batteryPercent = 72, statusLabel = "On Battery"))

        assertThat(metrics.drainValue).isEqualTo("On battery")
        assertThat(metrics.timeLeftValue).isEqualTo("Estimating")
        assertThat(metrics.summary).isEqualTo("On battery")
    }

    @Test
    fun `charging shows the time to full and the adapter metric is omitted`() {
        val metrics = PowerMetricsPresentation.of(
            PowerSnapshot(batteryPercent = 50, charging = true, statusLabel = "Charging", minutesToFull = 13),
        )

        assertThat(metrics.drainTitle).isEqualTo("Adapter")
        assertThat(metrics.drainReading).isNull()
        assertThat(metrics.timeLeftTitle).isEqualTo("To Full")
        assertThat(metrics.timeLeftValue).isEqualTo("13m")
        assertThat(metrics.summary).isEqualTo("Charging · 13m to full")
        assertThat(
            PowerMetricsPresentation.of(PowerSnapshot(batteryPercent = 50, charging = true, statusLabel = "Charging")).summary,
        ).isEqualTo("Charging")
    }

    @Test
    fun `plugged in and full says plugged in`() {
        val metrics = PowerMetricsPresentation.of(PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Full"))

        assertThat(metrics.timeLeftTitle).isEqualTo("Charge")
        assertThat(metrics.timeLeftValue).isEqualTo("Full")
        assertThat(metrics.summary).isEqualTo("Plugged in")
    }

    @Test
    fun `an unknown battery is estimating`() {
        val metrics = PowerMetricsPresentation.of(PowerSnapshot())

        assertThat(metrics.summary).isEqualTo("Estimating")
        assertThat(metrics.timeLeftValue).isEqualTo("Estimating")
        assertThat(metrics.drainValue).isEqualTo("Estimating")
    }
}
