package com.iloapps.nomaddashboard.core.data.companion

import com.iloapps.nomaddashboard.core.model.CompanionPreferences

data class ForegroundCompanionSignal(
    val online: Boolean?,
    val vpnActive: Boolean?,
    val charging: Boolean?,
)

data class CompanionAlert(val title: String, val body: String)

/** This only classifies foreground-observed state; it never polls or starts background work. */
object CompanionAlertPolicy {
    fun decide(
        previous: ForegroundCompanionSignal?,
        current: ForegroundCompanionSignal,
        preferences: CompanionPreferences,
        nowEpochMillis: Long,
    ): CompanionAlert? {
        if (!preferences.quietNotificationsEnabled || previous == null || isQuietHour(preferences, nowEpochMillis)) return null
        if (nowEpochMillis - preferences.lastNotificationAtEpochMillis < preferences.notificationCooldownMinutes * 60_000L) return null
        return when {
            previous.online == true && current.online == false -> CompanionAlert("Connection changed", "Nomad observed a sustained offline state while the app was open.")
            previous.online == false && current.online == true -> CompanionAlert("Connection restored", "Nomad observed connectivity return while the app was open.")
            current.vpnActive != null && previous.vpnActive != null && previous.vpnActive != current.vpnActive -> CompanionAlert("VPN changed", if (current.vpnActive == true) "VPN is active." else "VPN is no longer active.")
            current.charging != null && previous.charging != null && previous.charging != current.charging -> CompanionAlert("Power changed", if (current.charging == true) "Device is charging." else "Device is on battery power.")
            else -> null
        }
    }

    private fun isQuietHour(preferences: CompanionPreferences, now: Long): Boolean {
        val minute = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
        return if (preferences.quietHoursStartMinute <= preferences.quietHoursEndMinute) {
            minute in preferences.quietHoursStartMinute until preferences.quietHoursEndMinute
        } else minute >= preferences.quietHoursStartMinute || minute < preferences.quietHoursEndMinute
    }
}
