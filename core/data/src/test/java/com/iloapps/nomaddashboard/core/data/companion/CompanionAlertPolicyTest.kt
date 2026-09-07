package com.iloapps.nomaddashboard.core.data.companion

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.CompanionPreferences
import org.junit.Test

class CompanionAlertPolicyTest {
    @Test
    fun `does not alert for unknown or disabled state`() {
        val result = CompanionAlertPolicy.decide(
            previous = ForegroundCompanionSignal(online = true, vpnActive = false, charging = false),
            current = ForegroundCompanionSignal(online = null, vpnActive = false, charging = false),
            preferences = CompanionPreferences(quietNotificationsEnabled = false),
            nowEpochMillis = 600_000,
        )
        assertThat(result).isNull()
    }

    @Test
    fun `uses cooldown for foreground connectivity transition`() {
        val preferences = CompanionPreferences(quietNotificationsEnabled = true, quietHoursStartMinute = 0, quietHoursEndMinute = 0, lastNotificationAtEpochMillis = 1)
        val result = CompanionAlertPolicy.decide(
            previous = ForegroundCompanionSignal(online = true, vpnActive = false, charging = false),
            current = ForegroundCompanionSignal(online = false, vpnActive = false, charging = false),
            preferences = preferences,
            nowEpochMillis = 10,
        )
        assertThat(result).isNull()
    }
}
