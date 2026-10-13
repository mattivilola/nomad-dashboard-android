package com.iloapps.nomaddashboard.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import java.time.Instant
import org.junit.Test

class ConnectivityCarryOverTest {
    private val now = Instant.parse("2026-10-12T10:00:00Z")
    private val history = listOf(MetricHistoryPoint(now.minusSeconds(60), 40.0))

    private fun previous(wifi: String? = "Cafe", vpn: Boolean = false, age: Long = 30) = ConnectivitySnapshot(
        internetState = InternetState.ONLINE,
        isOnline = true,
        latencyMs = 40.0,
        jitterMs = 6.0,
        latencyMeasuredAt = now.minusSeconds(age),
        latencyProbeHost = "1.1.1.1",
        latencyHistoryMs = history,
        downloadHistoryMbps = history,
        wifiName = wifi,
        vpnActive = vpn,
    )

    private fun passive(wifi: String? = "Cafe", vpn: Boolean = false, state: InternetState = InternetState.ONLINE) =
        ConnectivitySnapshot(internetState = state, isOnline = state == InternetState.ONLINE, wifiName = wifi, vpnActive = vpn)

    @Test
    fun `a recent reading on the same network survives a passive read`() {
        val carried = passive().carryingReadingsFrom(previous(), now)

        assertThat(carried.latencyMs).isEqualTo(40.0)
        assertThat(carried.jitterMs).isEqualTo(6.0)
        assertThat(carried.latencyMeasuredAt).isEqualTo(now.minusSeconds(30))
        assertThat(carried.latencyProbeHost).isEqualTo("1.1.1.1")
        assertThat(carried.latencyHistoryMs).isEqualTo(history)
        assertThat(carried.downloadHistoryMbps).isEqualTo(history)
    }

    @Test
    fun `a reading older than five minutes is dropped but the history stays`() {
        val carried = passive().carryingReadingsFrom(previous(age = 301), now)

        assertThat(carried.latencyMs).isNull()
        assertThat(carried.jitterMs).isNull()
        assertThat(carried.latencyProbeHost).isNull()
        assertThat(carried.latencyHistoryMs).isEqualTo(history)
    }

    @Test
    fun `offline and unvalidated networks drop the reading`() {
        assertThat(passive(state = InternetState.OFFLINE).carryingReadingsFrom(previous(), now).latencyMs).isNull()
        assertThat(passive(state = InternetState.CAPTIVE_OR_UNVALIDATED).carryingReadingsFrom(previous(), now).latencyMs).isNull()
    }

    @Test
    fun `a different wifi or vpn state drops the reading`() {
        assertThat(passive(wifi = "Hotel").carryingReadingsFrom(previous(), now).latencyMs).isNull()
        assertThat(passive(wifi = null).carryingReadingsFrom(previous(), now).latencyMs).isNull()
        assertThat(passive(vpn = true).carryingReadingsFrom(previous(), now).latencyMs).isNull()
    }

    @Test
    fun `a latency the new snapshot measured itself is never replaced`() {
        val measured = passive().copy(latencyMs = 55.0, latencyMeasuredAt = now)

        val carried = measured.carryingReadingsFrom(previous(), now)

        assertThat(carried.latencyMs).isEqualTo(55.0)
        assertThat(carried.latencyMeasuredAt).isEqualTo(now)
        assertThat(carried.jitterMs).isNull()
    }
}
