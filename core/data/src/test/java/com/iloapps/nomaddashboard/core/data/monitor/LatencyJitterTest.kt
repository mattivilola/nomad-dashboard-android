package com.iloapps.nomaddashboard.core.data.monitor

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import java.time.Instant
import org.junit.Test

class LatencyJitterTest {
    private val now = Instant.parse("2026-10-12T10:00:00Z")

    @Test
    fun `jitter is unknown with fewer than two readings`() {
        assertThat(latencyJitter(emptyList())).isNull()
        assertThat(latencyJitter(listOf(40.0))).isNull()
    }

    @Test
    fun `two readings give their absolute difference like macOS`() {
        assertThat(latencyJitter(listOf(40.0, 52.0))).isEqualTo(12.0)
        assertThat(latencyJitter(listOf(52.0, 40.0))).isEqualTo(12.0)
    }

    @Test
    fun `more readings give the mean absolute successive difference`() {
        // |20-10|, |15-20|, |15-15| average to 5.
        assertThat(latencyJitter(listOf(10.0, 20.0, 15.0, 15.0))).isEqualTo(5.0)
    }

    @Test
    fun `only the last five readings within five minutes count`() {
        val history = listOf(
            MetricHistoryPoint(now.minusSeconds(3_600), 500.0),
            MetricHistoryPoint(now.minusSeconds(290), 10.0),
            MetricHistoryPoint(now.minusSeconds(240), 20.0),
            MetricHistoryPoint(now.minusSeconds(180), 20.0),
            MetricHistoryPoint(now.minusSeconds(120), 20.0),
            MetricHistoryPoint(now.minusSeconds(60), 20.0),
            MetricHistoryPoint(now, 30.0),
        )

        // The last five are 20, 20, 20, 20, 30: differences 0, 0, 0, 10.
        assertThat(latencyJitter(history, now)).isEqualTo(2.5)
    }

    @Test
    fun `readings from an earlier session do not make jitter`() {
        val history = listOf(MetricHistoryPoint(now.minusSeconds(3_600), 500.0), MetricHistoryPoint(now, 30.0))

        assertThat(latencyJitter(history, now)).isNull()
    }
}
