package com.iloapps.nomaddashboard.core.data.monitor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.SignalLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class TrafficSample(
    val rxBytes: Long,
    val txBytes: Long,
    val capturedAtMillis: Long,
)

@Singleton
class SystemTelemetryReader @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TelemetryReader {
    @SuppressLint("MissingPermission")
    override suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> = withContext(ioDispatcher) {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        val wifiManager = context.applicationContext.getSystemService<WifiManager>()
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        val wifiInfo = runCatching { wifiManager?.connectionInfo }.getOrNull()

        val currentTraffic = TrafficSample(
            rxBytes = TrafficStats.getTotalRxBytes(),
            txBytes = TrafficStats.getTotalTxBytes(),
            capturedAtMillis = System.currentTimeMillis(),
        )

        val deltaSeconds = previousTrafficSample?.let {
            ((currentTraffic.capturedAtMillis - it.capturedAtMillis).coerceAtLeast(1L)).toDouble() / 1000.0
        }
        val downloadMbps = if (previousTrafficSample != null && deltaSeconds != null) {
            ((currentTraffic.rxBytes - previousTrafficSample.rxBytes).coerceAtLeast(0L) * 8.0) / deltaSeconds / 1_000_000.0
        } else {
            null
        }
        val uploadMbps = if (previousTrafficSample != null && deltaSeconds != null) {
            ((currentTraffic.txBytes - previousTrafficSample.txBytes).coerceAtLeast(0L) * 8.0) / deltaSeconds / 1_000_000.0
        } else {
            null
        }

        val latencyMs = probeLatency("1.1.1.1", 443)
        val fallbackLatency = probeLatency("8.8.8.8", 443)
        val resolvedLatency = latencyMs ?: fallbackLatency

        ConnectivitySnapshot(
            internetState = when {
                capabilities == null -> "Offline"
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "Online"
                else -> "Captive/Checking"
            },
            isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            latencyMs = resolvedLatency,
            jitterMs = resolvedLatency?.div(10.0),
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            wifiName = wifiInfo?.ssid?.takeUnless { it == WifiManager.UNKNOWN_SSID }?.trim('"'),
            wifiSignalDbm = wifiInfo?.rssi,
            vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            timeZoneId = ZoneId.systemDefault().id,
        ) to currentTraffic
    }

    override suspend fun power(): PowerSnapshot = withContext(ioDispatcher) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryManager = context.getSystemService<BatteryManager>()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100f / scale).toInt()
        } else {
            null
        }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val temperatureTenthsCelsius = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val currentAverageMicroAmps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0
        val currentNowMicroAmps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val currentEstimateMicroAmps = currentAverageMicroAmps.takeIf { it != 0 } ?: currentNowMicroAmps
        val voltageMillivolts = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val dischargeWatts = if (currentEstimateMicroAmps != 0 && voltageMillivolts != 0) {
            kotlin.math.abs(currentEstimateMicroAmps / 1_000_000.0 * voltageMillivolts / 1000.0)
        } else {
            null
        }
        val temperatureCelsius = if (temperatureTenthsCelsius > 0) {
            temperatureTenthsCelsius / 10.0
        } else {
            null
        }
        val voltageVolts = if (voltageMillivolts > 0) {
            voltageMillivolts / 1000.0
        } else {
            null
        }
        val (batteryHealthSummary, batteryHealthLevel) = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good" to SignalLevel.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating" to SignalLevel.WARNING
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold" to SignalLevel.WARNING
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage" to SignalLevel.WARNING
            BatteryManager.BATTERY_HEALTH_DEAD -> "Needs service" to SignalLevel.BAD
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure" to SignalLevel.BAD
            else -> when {
                batteryPct == null -> "Estimating" to SignalLevel.NEUTRAL
                batteryPct > 70 -> "Healthy" to SignalLevel.GOOD
                batteryPct > 35 -> "Steady" to SignalLevel.NEUTRAL
                else -> "Low" to SignalLevel.WARNING
            }
        }
        val statusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "On Battery"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> if (plugged != 0) "Plugged In" else "Idle"
            else -> "Checking"
        }
        val powerSourceLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            else -> "Battery"
        }

        PowerSnapshot(
            batteryPercent = batteryPct,
            charging = charging,
            statusLabel = statusLabel,
            batteryHealthSummary = batteryHealthSummary,
            batteryHealthLevel = batteryHealthLevel,
            dischargeWatts = dischargeWatts,
            powerSourceLabel = powerSourceLabel,
            temperatureCelsius = temperatureCelsius,
            voltageVolts = voltageVolts,
        )
    }

    private suspend fun probeLatency(host: String, port: Int): Double? = coroutineScope {
        val attempt = async(ioDispatcher) {
            runCatching {
                val start = System.nanoTime()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1500)
                }
                (System.nanoTime() - start) / 1_000_000.0
            }.getOrNull()
        }
        attempt.await()
    }
}
