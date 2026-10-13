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
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.SignalLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
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
    override suspend fun connectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> =
        readConnectivity(previousTrafficSample = previousTrafficSample, includeActiveLatencyProbe = true)

    @SuppressLint("MissingPermission")
    override suspend fun passiveConnectivity(previousTrafficSample: TrafficSample?): Pair<ConnectivitySnapshot, TrafficSample> =
        readConnectivity(previousTrafficSample = previousTrafficSample, includeActiveLatencyProbe = false)

    @SuppressLint("MissingPermission")
    private suspend fun readConnectivity(
        previousTrafficSample: TrafficSample?,
        includeActiveLatencyProbe: Boolean,
    ): Pair<ConnectivitySnapshot, TrafficSample> = withContext(ioDispatcher) {
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

        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val resolvedLatency = if (includeActiveLatencyProbe) {
            resolveLatencyForValidatedNetwork(isValidated) { host, port ->
                probeLatency(host, port)
            }
        } else {
            null
        }

        ConnectivitySnapshot(
            internetState = when {
                capabilities == null -> InternetState.OFFLINE
                isValidated -> InternetState.ONLINE
                else -> InternetState.CAPTIVE_OR_UNVALIDATED
            },
            isOnline = isValidated,
            latencyMs = resolvedLatency?.latencyMs,
            // Jitter needs several readings; the repository derives it from the latency history.
            jitterMs = null,
            latencyMeasuredAt = resolvedLatency?.let { Instant.now() },
            latencyProbeHost = resolvedLatency?.host,
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            wifiName = wifiInfo?.ssid?.takeUnless { it == WifiManager.UNKNOWN_SSID }?.trim('"'),
            wifiSignalDbm = wifiInfo?.rssi,
            wifiLinkSpeedMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 },
            wifiFrequencyMhz = wifiInfo?.frequency?.takeIf { it > 0 },
            vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            timeZoneId = ZoneId.systemDefault().id,
        ) to currentTraffic
    }

    override suspend fun latencyProbe(): LatencyProbeResult? = withContext(ioDispatcher) {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        val capabilities = connectivityManager?.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        resolveLatencyForValidatedNetwork(isValidated) { host, port -> probeLatency(host, port) }
    }

    override suspend fun power(): PowerSnapshot = withContext(ioDispatcher) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryManager = context.getSystemService<BatteryManager>()
        val powerSaveMode = context.getSystemService<PowerManager>()?.isPowerSaveMode == true
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
        val currentAverageMicroAmps = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            .takeSupportedBatteryProperty()
        val currentNowMicroAmps = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeSupportedBatteryProperty()
        val currentEstimateMicroAmps = currentAverageMicroAmps ?: currentNowMicroAmps
        val voltageMillivolts = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL
        val minutesToFull = if (charging && !isFull) {
            batteryManager?.computeChargeTimeRemaining()?.let(::chargeMinutesToFull)
        } else {
            null
        }
        val minutesRemaining = if (!charging && plugged == 0) {
            estimateDischargeMinutesRemaining(
                chargeCounterMicroAmpHours = batteryManager
                    ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    .takeSupportedBatteryProperty(),
                currentAverage = currentAverageMicroAmps,
                currentNow = currentNowMicroAmps,
            )
        } else {
            null
        }
        val dischargeWatts = if (currentEstimateMicroAmps != null && voltageMillivolts > 0) {
            val estimatedWatts = kotlin.math.abs(currentEstimateMicroAmps / 1_000_000.0 * voltageMillivolts / 1000.0)
            estimatedWatts.takeIf { it in 0.01..100.0 }
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
            powerSaveMode = powerSaveMode,
            minutesRemaining = minutesRemaining,
            minutesToFull = minutesToFull,
        )
    }

    private suspend fun probeLatency(host: String, port: Int): LatencyProbeResult? =
        try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1500)
            }
            LatencyProbeResult(latencyMs = (System.nanoTime() - start) / 1_000_000.0, host = host)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
}

internal suspend fun resolveLatencyForValidatedNetwork(
    isValidated: Boolean,
    probe: suspend (host: String, port: Int) -> LatencyProbeResult?,
): LatencyProbeResult? {
    if (!isValidated) {
        return null
    }
    return probe(PrimaryLatencyHost, LatencyPort)
        ?: probe(FallbackLatencyHost, LatencyPort)
}

private const val PrimaryLatencyHost = "1.1.1.1"
private const val FallbackLatencyHost = "8.8.8.8"
private const val LatencyPort = 443

private fun Int?.takeSupportedBatteryProperty(): Int? =
    this?.takeUnless { it == Int.MIN_VALUE }

/**
 * Minutes until the battery is empty at the present drain, from the remaining charge and the
 * discharge current, or null when either is missing or the result is implausible.
 *
 * - The sign of the current is ignored: the platform documents negative as discharging but several
 *   vendors report the opposite, and the caller already knows the battery is discharging.
 * - A current of zero means the average is unsupported, so the instantaneous value is used instead.
 * - Some vendors report milliamps and milliamp-hours instead of the documented micro units. Values
 *   too small to be micro units for a phone battery are scaled up.
 * - Anything under a minute or over a week is noise, not an estimate worth showing.
 */
internal fun estimateDischargeMinutesRemaining(
    chargeCounterMicroAmpHours: Int?,
    currentAverage: Int?,
    currentNow: Int?,
): Int? {
    val counter = chargeCounterMicroAmpHours?.takeIf { it > 0 }?.toDouble() ?: return null
    val current = listOfNotNull(currentAverage, currentNow)
        .map { kotlin.math.abs(it.toLong()) }
        .firstOrNull { it > 0 }
        ?.toDouble() ?: return null

    val microAmpHours = if (counter < MilliUnitCounterThreshold) counter * 1_000 else counter
    val microAmps = if (current < MilliUnitCurrentThreshold) current * 1_000 else current
    if (microAmps > MaxPlausibleDischargeMicroAmps) return null

    val minutes = microAmpHours / microAmps * 60
    return minutes.roundToInt().takeIf { it in MinEstimateMinutes..MaxEstimateMinutes }
}

/** Minutes until fully charged from the platform's remaining charge time in milliseconds, or null when unknown. */
internal fun chargeMinutesToFull(remainingMillis: Long): Int? {
    if (remainingMillis <= 0) return null
    return (remainingMillis / 60_000.0).roundToInt().takeIf { it in MinEstimateMinutes..MaxEstimateMinutes }
}

// Below these a micro-unit reading would describe a battery or load no phone has, so it is milli units.
private const val MilliUnitCounterThreshold = 10_000.0
private const val MilliUnitCurrentThreshold = 10_000.0
private const val MaxPlausibleDischargeMicroAmps = 10_000_000.0
private const val MinEstimateMinutes = 1
private const val MaxEstimateMinutes = 7 * 24 * 60
