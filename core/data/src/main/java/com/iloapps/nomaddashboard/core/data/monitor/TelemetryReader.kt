package com.iloapps.nomaddashboard.core.data.monitor

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot

interface TelemetryReader {
    suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample>

    /** Returns immediate system connectivity state without optional active socket probes. */
    suspend fun passiveConnectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> =
        connectivity(previousTrafficSample)

    suspend fun power(): PowerSnapshot
}
