package com.iloapps.nomaddashboard.core.data.monitor

/** One successful latency probe: the round-trip time and the host that answered. */
data class LatencyProbeResult(
    val latencyMs: Double,
    val host: String?,
)
