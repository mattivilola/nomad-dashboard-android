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
            12.0
        }

        assertThat(latency).isNull()
        assertThat(probes).isEqualTo(0)
    }

    @Test
    fun `successful primary latency probe skips fallback`() = runTest {
        val hosts = mutableListOf<String>()

        val latency = resolveLatencyForValidatedNetwork(isValidated = true) { host, _ ->
            hosts += host
            12.0
        }

        assertThat(latency).isEqualTo(12.0)
        assertThat(hosts).containsExactly("1.1.1.1")
    }

    @Test
    fun `failed primary latency probe uses one fallback`() = runTest {
        val hosts = mutableListOf<String>()

        val latency = resolveLatencyForValidatedNetwork(isValidated = true) { host, _ ->
            hosts += host
            if (host == "1.1.1.1") null else 18.0
        }

        assertThat(latency).isEqualTo(18.0)
        assertThat(hosts).containsExactly("1.1.1.1", "8.8.8.8").inOrder()
    }
}
