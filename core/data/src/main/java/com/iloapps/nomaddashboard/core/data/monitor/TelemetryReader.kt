package com.iloapps.nomaddashboard.core.data.monitor

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot

interface TelemetryReader {
    suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample>

    /** Returns immediate system connectivity state without optional active socket probes. */
    suspend fun passiveConnectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> =
        connectivity(previousTrafficSample)

    /**
     * Measures round-trip latency once with an active socket probe, or null when the network is not
     * validated or the probe fails. Does not touch the throughput counters.
     */
    suspend fun latencyProbe(): LatencyProbeResult? =
        connectivity(null).first.let { reading ->
            reading.latencyMs?.let { LatencyProbeResult(latencyMs = it, host = reading.latencyProbeHost) }
        }

    suspend fun power(): PowerSnapshot
}
