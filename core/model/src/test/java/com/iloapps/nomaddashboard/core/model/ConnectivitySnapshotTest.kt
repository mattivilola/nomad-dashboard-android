package com.iloapps.nomaddashboard.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class ConnectivitySnapshotTest {
    @Test
    fun `a fresh snapshot is still checking`() {
        val snapshot = ConnectivitySnapshot()

        assertThat(snapshot.internetState).isEqualTo(InternetState.CHECKING)
        assertThat(snapshot.isChecking).isTrue()
        assertThat(snapshot.isOffline).isFalse()
    }

    @Test
    fun `only the offline state is offline`() {
        InternetState.entries.forEach { state ->
            assertThat(ConnectivitySnapshot(internetState = state).isOffline).isEqualTo(state == InternetState.OFFLINE)
        }
    }

    @Test
    fun `an unvalidated network is neither offline nor checking`() {
        val snapshot = ConnectivitySnapshot(internetState = InternetState.CAPTIVE_OR_UNVALIDATED)

        assertThat(snapshot.isOffline).isFalse()
        assertThat(snapshot.isChecking).isFalse()
    }

    @Test
    fun `labels are the text the UI shows`() {
        assertThat(InternetState.entries.map { it.label })
            .containsExactly("Checking", "Online", "Captive/Checking", "Offline")
            .inOrder()
    }

    @Test
    fun `a latency reading older than five minutes is dropped with its jitter`() {
        val now = Instant.parse("2026-10-12T10:00:00Z")
        val snapshot = ConnectivitySnapshot(
            internetState = InternetState.ONLINE,
            latencyMs = 40.0,
            jitterMs = 4.0,
            latencyMeasuredAt = now.minusSeconds(301),
            latencyProbeHost = "1.1.1.1",
            latencyHistoryMs = listOf(MetricHistoryPoint(now.minusSeconds(301), 40.0)),
        )

        val fresh = snapshot.withoutStaleLatency(now)

        assertThat(fresh.latencyMs).isNull()
        assertThat(fresh.jitterMs).isNull()
        assertThat(fresh.latencyMeasuredAt).isNull()
        assertThat(fresh.latencyProbeHost).isNull()
        assertThat(fresh.latencyHistoryMs).hasSize(1)
    }

    @Test
    fun `a reading of exactly five minutes or an unknown age is kept`() {
        val now = Instant.parse("2026-10-12T10:00:00Z")
        val edge = ConnectivitySnapshot(latencyMs = 40.0, latencyMeasuredAt = now.minusSeconds(300))
        val unknownAge = ConnectivitySnapshot(latencyMs = 40.0)

        assertThat(edge.withoutStaleLatency(now)).isEqualTo(edge)
        assertThat(unknownAge.withoutStaleLatency(now)).isEqualTo(unknownAge)
    }
}
