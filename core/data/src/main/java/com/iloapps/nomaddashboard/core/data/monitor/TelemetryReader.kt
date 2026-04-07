package com.iloapps.nomaddashboard.core.data.monitor

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot

interface TelemetryReader {
    suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample>

    suspend fun power(): PowerSnapshot
}
