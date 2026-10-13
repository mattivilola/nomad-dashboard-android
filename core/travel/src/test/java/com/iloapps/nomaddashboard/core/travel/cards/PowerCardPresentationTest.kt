package com.iloapps.nomaddashboard.core.travel.cards

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import java.time.Instant
import org.junit.Test

class PowerCardPresentationTest {
    private val onBattery = PowerSnapshot(
        batteryPercent = 72,
        statusLabel = "On Battery",
        batteryHealthSummary = "Good",
        powerSourceLabel = "Battery",
        dischargeWatts = 11.4,
        temperatureCelsius = 31.2,
        voltageVolts = 3.85,
        minutesRemaining = 208,
    )

    private val charging = PowerSnapshot(
        batteryPercent = 50,
        charging = true,
        statusLabel = "Charging",
        batteryHealthSummary = "Good",
        powerSourceLabel = "AC",
        dischargeWatts = 7.2,
        minutesToFull = 13,
    )

    @Test
    fun `on battery the row is battery drain and time left`() {
        val card = PowerCardPresentation.of(onBattery, HealthLevel.READY)

        assertThat(card.battery.title).isEqualTo("Battery")
        assertThat(card.battery.reading.text).isEqualTo("72%")
        assertThat(card.drain?.title).isEqualTo("Drain")
        assertThat(card.drain?.reading?.text).isEqualTo("11.4 W")
        assertThat(card.timeLeft.title).isEqualTo("Time Left")
        assertThat(card.timeLeft.reading.text).isEqualTo("3h 28m")
    }

    @Test
    fun `the battery is tinted by the power health and nothing else is`() {
        assertThat(PowerCardPresentation.of(onBattery, HealthLevel.READY).battery.tone).isEqualTo(CardTone.GOOD)
        assertThat(PowerCardPresentation.of(onBattery, HealthLevel.CAUTION).battery.tone).isEqualTo(CardTone.CAUTION)
        assertThat(PowerCardPresentation.of(onBattery, HealthLevel.ATTENTION).battery.tone).isEqualTo(CardTone.ATTENTION)
        assertThat(PowerCardPresentation.of(onBattery, HealthLevel.ATTENTION).drain?.tone).isEqualTo(CardTone.NEUTRAL)
        assertThat(PowerCardPresentation.of(onBattery, HealthLevel.ATTENTION).timeLeft.tone).isEqualTo(CardTone.NEUTRAL)
    }

    @Test
    fun `before the first reading the battery says estimating and is neutral`() {
        val card = PowerCardPresentation.of(PowerSnapshot(), HealthLevel.UNAVAILABLE)

        assertThat(card.battery.reading.text).isEqualTo("Estimating")
        assertThat(card.battery.tone).isEqualTo(CardTone.NEUTRAL)
    }

    @Test
    fun `charging shows the time to full and omits the unreadable adapter metric`() {
        val card = PowerCardPresentation.of(charging, HealthLevel.READY)

        assertThat(card.drain).isNull()
        assertThat(card.timeLeft.title).isEqualTo("To Full")
        assertThat(card.timeLeft.reading.text).isEqualTo("13m")
    }

    @Test
    fun `a plugged in battery reads full`() {
        val card = PowerCardPresentation.of(
            PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Full", powerSourceLabel = "AC"),
            HealthLevel.READY,
        )

        assertThat(card.timeLeft.title).isEqualTo("Charge")
        assertThat(card.timeLeft.reading.text).isEqualTo("Full")
    }

    @Test
    fun `the badge never repeats the time left value`() {
        assertThat(PowerCardPresentation.of(onBattery, HealthLevel.READY).statusText).isEqualTo("On Battery")
        assertThat(PowerCardPresentation.of(charging, HealthLevel.READY).statusText).isEqualTo("Charging")
        assertThat(
            PowerCardPresentation.of(
                PowerSnapshot(batteryPercent = 100, charging = true, statusLabel = "Full", powerSourceLabel = "AC"),
                HealthLevel.READY,
            ).statusText,
        ).isEqualTo("Plugged In")
        assertThat(
            PowerCardPresentation.of(PowerSnapshot(batteryPercent = 80, statusLabel = "Plugged In"), HealthLevel.READY).statusText,
        ).isEqualTo("Plugged In")
    }

    @Test
    fun `the subtitle names the source only when plugged in`() {
        assertThat(PowerCardPresentation.of(onBattery, HealthLevel.READY).subtitle).isNull()
        assertThat(PowerCardPresentation.of(charging, HealthLevel.READY).subtitle).isEqualTo("AC power")
        assertThat(PowerCardPresentation.of(PowerSnapshot(), HealthLevel.UNAVAILABLE).subtitle).isNull()
    }

    @Test
    fun `the details keep health source temperature and voltage`() {
        val details = PowerCardPresentation.of(onBattery, HealthLevel.READY).details

        assertThat(details).containsExactly(
            PowerDetail("Health", "Good"),
            PowerDetail("Source", "Battery"),
            PowerDetail("Temperature", "31.2°C"),
            PowerDetail("Voltage", "3.85 V"),
        ).inOrder()
    }

    @Test
    fun `the details keep the charge rate while charging and the battery saver`() {
        val details = PowerCardPresentation.of(charging.copy(powerSaveMode = true), HealthLevel.CAUTION).details

        assertThat(details).contains(PowerDetail("Charge rate", "7.2 W (estimated)"))
        assertThat(details).contains(PowerDetail("Battery Saver", "On"))
    }

    @Test
    fun `charge history becomes the battery sparkline`() {
        val start = Instant.parse("2026-10-12T10:00:00Z")
        val history = listOf(80.0, 78.0, 75.0).mapIndexed { index, value -> MetricHistoryPoint(start.plusSeconds(index * 900L), value) }

        val card = PowerCardPresentation.of(onBattery.copy(batteryPercentHistory = history), HealthLevel.READY)

        assertThat(card.battery.trend?.values).containsExactly(80.0, 78.0, 75.0).inOrder()
        assertThat(card.battery.trend?.spanLabel).isEqualTo("Last 30 min")
    }
}
