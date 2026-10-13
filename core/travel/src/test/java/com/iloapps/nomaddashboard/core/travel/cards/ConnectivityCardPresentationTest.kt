package com.iloapps.nomaddashboard.core.travel.cards

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.MetricHistoryPoint
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import java.time.Instant
import org.junit.Test

class ConnectivityCardPresentationTest {
    private val online = ConnectivitySnapshot(
        internetState = InternetState.ONLINE,
        isOnline = true,
        latencyMs = 28.4,
        jitterMs = 3.2,
        downloadMbps = 96.04,
        uploadMbps = 19.2,
    )

    @Test
    fun `down up and latency keep their unit apart from the number`() {
        val card = ConnectivityCardPresentation.of(online, HealthLevel.READY)

        assertThat(card.download.title).isEqualTo("Down")
        assertThat(card.download.reading.number).isEqualTo("96.0")
        assertThat(card.download.reading.unit).isEqualTo("Mbps")
        assertThat(card.upload.reading.number).isEqualTo("19.2")
        assertThat(card.latency.reading.number).isEqualTo("28")
        assertThat(card.latency.reading.unit).isEqualTo("ms")
    }

    @Test
    fun `an idle link reads as zero rather than a measurement`() {
        val card = ConnectivityCardPresentation.of(online.copy(downloadMbps = 0.01, uploadMbps = null), HealthLevel.READY)

        assertThat(card.download.reading.text).isEqualTo("0 Mbps")
        assertThat(card.upload.reading.text).isEqualTo("0 Mbps")
    }

    @Test
    fun `only the latency is tinted and by the network health`() {
        assertThat(ConnectivityCardPresentation.of(online, HealthLevel.READY).latency.tone).isEqualTo(CardTone.GOOD)
        assertThat(ConnectivityCardPresentation.of(online, HealthLevel.CAUTION).latency.tone).isEqualTo(CardTone.CAUTION)
        assertThat(ConnectivityCardPresentation.of(online, HealthLevel.ATTENTION).latency.tone).isEqualTo(CardTone.ATTENTION)

        val card = ConnectivityCardPresentation.of(online, HealthLevel.ATTENTION)
        assertThat(card.download.tone).isEqualTo(CardTone.NEUTRAL)
        assertThat(card.upload.tone).isEqualTo(CardTone.NEUTRAL)
    }

    @Test
    fun `a missing latency says waiting and is not tinted`() {
        val card = ConnectivityCardPresentation.of(online.copy(latencyMs = null), HealthLevel.ATTENTION)

        assertThat(card.latency.reading.text).isEqualTo("Waiting")
        assertThat(card.latency.tone).isEqualTo(CardTone.NEUTRAL)
    }

    @Test
    fun `offline latency is unavailable and carries no jitter hint`() {
        val card = ConnectivityCardPresentation.of(
            ConnectivitySnapshot(internetState = InternetState.OFFLINE),
            HealthLevel.ATTENTION,
        )

        assertThat(card.latency.reading.text).isEqualTo("Unavailable")
        assertThat(card.jitterCaption).isNull()
    }

    @Test
    fun `the jitter caption names the probe host only when it is known`() {
        assertThat(ConnectivityCardPresentation.of(online, HealthLevel.READY, probeHost = "1.1.1.1").jitterCaption)
            .isEqualTo("Jitter 3 ms via 1.1.1.1")
        assertThat(ConnectivityCardPresentation.of(online, HealthLevel.READY).jitterCaption).isEqualTo("Jitter 3 ms")
        assertThat(ConnectivityCardPresentation.of(online, HealthLevel.READY, probeHost = " ").jitterCaption).isEqualTo("Jitter 3 ms")
    }

    @Test
    fun `missing jitter says when it will appear`() {
        assertThat(ConnectivityCardPresentation.of(online.copy(jitterMs = null), HealthLevel.READY).jitterCaption)
            .isEqualTo("Jitter appears after the next refresh")
    }

    @Test
    fun `histories become trends with their span`() {
        val start = Instant.parse("2026-10-12T10:00:00Z")
        val history = listOf(80.0, 92.0, 88.0).mapIndexed { index, value ->
            MetricHistoryPoint(start.plusSeconds(index * 600L), value)
        }

        val card = ConnectivityCardPresentation.of(online.copy(downloadHistoryMbps = history), HealthLevel.READY)

        assertThat(card.download.trend?.values).containsExactly(80.0, 92.0, 88.0).inOrder()
        assertThat(card.download.trend?.spanLabel).isEqualTo("Last 20 min")
        assertThat(card.upload.trend).isNull()
    }

    @Test
    fun `a single sample is not a trend`() {
        val one = listOf(MetricHistoryPoint(Instant.parse("2026-10-12T10:00:00Z"), 5.0))

        assertThat(MetricTrend.of(one)).isNull()
    }

    @Test
    fun `sparkline spans use the coarsest readable unit`() {
        assertThat(SparklineSpan.text(0)).isEqualTo("1 s")
        assertThat(SparklineSpan.text(45)).isEqualTo("45 s")
        assertThat(SparklineSpan.text(89)).isEqualTo("89 s")
        assertThat(SparklineSpan.text(90)).isEqualTo("2 min")
        assertThat(SparklineSpan.text(30 * 60)).isEqualTo("30 min")
        assertThat(SparklineSpan.text(2 * 3600)).isEqualTo("2 h")
        assertThat(SparklineSpan.text(5400 + 1800)).isEqualTo("2 h")
        assertThat(SparklineSpan.text(5400 + 900)).isEqualTo("1.8 h")
    }
}
