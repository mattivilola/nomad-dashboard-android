package com.iloapps.nomaddashboard.core.data.repository

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import java.time.Instant

/**
 * A passive connectivity read describes the network state but measures nothing, so it must not wipe the
 * measurements that are still true. This keeps the histories and, while the device stays online on the
 * same network, a latency reading (with its jitter) that is no older than
 * [ConnectivitySnapshot.MaximumLatencyAge].
 *
 * The reading is dropped when the device is offline or the network is not validated, when the Wi-Fi
 * name or VPN state changed (a different network has different latency), and when it is too old.
 * A latency this snapshot measured itself is never replaced.
 */
internal fun ConnectivitySnapshot.carryingReadingsFrom(previous: ConnectivitySnapshot, now: Instant): ConnectivitySnapshot {
    val withHistory = copy(
        latencyHistoryMs = latencyHistoryMs.ifEmpty { previous.latencyHistoryMs },
        downloadHistoryMbps = downloadHistoryMbps.ifEmpty { previous.downloadHistoryMbps },
        uploadHistoryMbps = uploadHistoryMbps.ifEmpty { previous.uploadHistoryMbps },
    )
    if (latencyMs != null) return withHistory

    val sameNetwork = wifiName == previous.wifiName && vpnActive == previous.vpnActive
    if (!isOnline || !sameNetwork) return withHistory

    val kept = previous.withoutStaleLatency(now)
    return withHistory.copy(
        latencyMs = kept.latencyMs,
        jitterMs = kept.jitterMs,
        latencyMeasuredAt = kept.latencyMeasuredAt,
        latencyProbeHost = kept.latencyProbeHost,
    )
}
