package com.iloapps.nomaddashboard.core.data.monitor

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SystemTelemetryReaderTest {
    @Test
    fun `offline network skips active latency probes`() = runTest {
        var probes = 0

        val latency = resolveLatencyForValidatedNetwork(isValidated = false) { _, _ ->
            probes += 1
            LatencyProbeResult(12.0, "1.1.1.1")
        }

        assertThat(latency).isNull()
        assertThat(probes).isEqualTo(0)
    }

    @Test
    fun `successful primary latency probe skips fallback`() = runTest {
        val hosts = mutableListOf<String>()

        val latency = resolveLatencyForValidatedNetwork(isValidated = true) { host, _ ->
            hosts += host
            LatencyProbeResult(12.0, host)
        }

        assertThat(latency).isEqualTo(LatencyProbeResult(12.0, "1.1.1.1"))
        assertThat(hosts).containsExactly("1.1.1.1")
    }

    @Test
    fun `failed primary latency probe uses one fallback`() = runTest {
        val hosts = mutableListOf<String>()

        val latency = resolveLatencyForValidatedNetwork(isValidated = true) { host, _ ->
            hosts += host
            if (host == "1.1.1.1") null else LatencyProbeResult(18.0, host)
        }

        assertThat(latency).isEqualTo(LatencyProbeResult(18.0, "8.8.8.8"))
        assertThat(hosts).containsExactly("1.1.1.1", "8.8.8.8").inOrder()
    }

    @Test
    fun `discharge estimate divides remaining charge by drain`() {
        // 2000 mAh left at 500 mA is four hours.
        assertThat(estimateDischargeMinutesRemaining(2_000_000, currentAverage = -500_000, currentNow = -700_000))
            .isEqualTo(240)
    }

    @Test
    fun `discharge estimate ignores the vendor specific sign of the current`() {
        val negative = estimateDischargeMinutesRemaining(3_000_000, currentAverage = -600_000, currentNow = null)
        val positive = estimateDischargeMinutesRemaining(3_000_000, currentAverage = 600_000, currentNow = null)

        assertThat(negative).isEqualTo(300)
        assertThat(positive).isEqualTo(300)
    }

    @Test
    fun `discharge estimate falls back to instantaneous current when the average is zero or missing`() {
        assertThat(estimateDischargeMinutesRemaining(1_000_000, currentAverage = 0, currentNow = -250_000)).isEqualTo(240)
        assertThat(estimateDischargeMinutesRemaining(1_000_000, currentAverage = null, currentNow = -250_000)).isEqualTo(240)
    }

    @Test
    fun `discharge estimate scales vendors that report milliamps and milliamp hours`() {
        // 3000 mAh at 600 mA reported in milli units gives the same five hours as the micro units.
        assertThat(estimateDischargeMinutesRemaining(3_000, currentAverage = -600, currentNow = null)).isEqualTo(300)
        assertThat(estimateDischargeMinutesRemaining(3_000_000, currentAverage = -600, currentNow = null)).isEqualTo(300)
        assertThat(estimateDischargeMinutesRemaining(3_000, currentAverage = -600_000, currentNow = null)).isEqualTo(300)
    }

    @Test
    fun `discharge estimate is unknown without usable inputs`() {
        assertThat(estimateDischargeMinutesRemaining(null, -500_000, null)).isNull()
        assertThat(estimateDischargeMinutesRemaining(0, -500_000, null)).isNull()
        assertThat(estimateDischargeMinutesRemaining(-5, -500_000, null)).isNull()
        assertThat(estimateDischargeMinutesRemaining(2_000_000, null, null)).isNull()
        assertThat(estimateDischargeMinutesRemaining(2_000_000, 0, 0)).isNull()
    }

    @Test
    fun `discharge estimate rejects implausible results`() {
        // Under a minute: 10 mAh left at 2 A.
        assertThat(estimateDischargeMinutesRemaining(10_000, currentAverage = -2_000_000, currentNow = null)).isNull()
        // Over a week: 5000 mAh at 20 mA.
        assertThat(estimateDischargeMinutesRemaining(5_000_000, currentAverage = -20_000, currentNow = null)).isNull()
        // A drain above 10 A is a bad reading, not a phone.
        assertThat(estimateDischargeMinutesRemaining(5_000_000, currentAverage = -50_000_000, currentNow = null)).isNull()
    }

    @Test
    fun `charge time converts platform milliseconds to whole minutes`() {
        assertThat(chargeMinutesToFull(13 * 60_000L)).isEqualTo(13)
        assertThat(chargeMinutesToFull(13 * 60_000L + 40_000L)).isEqualTo(14)
    }

    @Test
    fun `charge time is unknown when the platform cannot tell`() {
        assertThat(chargeMinutesToFull(-1)).isNull()
        assertThat(chargeMinutesToFull(0)).isNull()
        assertThat(chargeMinutesToFull(10_000)).isNull()
        assertThat(chargeMinutesToFull(8L * 24 * 3_600_000)).isNull()
    }
}
