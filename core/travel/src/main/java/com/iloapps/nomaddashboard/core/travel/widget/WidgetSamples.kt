package com.iloapps.nomaddashboard.core.travel.widget

import com.iloapps.nomaddashboard.core.travel.health.HealthLevel
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** What the widget picker and the redacted placeholder show before the app has saved anything real. */
object WidgetSamples {
    fun snapshot(savedAt: Instant, zone: ZoneId = ZoneId.systemDefault()): WidgetSnapshot {
        val today = savedAt.atZone(zone).toLocalDate()
        val sunset = today.atTime(LocalTime.of(19, 42)).atZone(zone).toInstant()
        val sunrise = today.atTime(LocalTime.of(6, 31)).atZone(zone).toInstant()
        return WidgetSnapshot.of(
            verdict = WidgetSnapshot.Verdict(title = "Ready", level = HealthLevel.READY),
            savedAt = savedAt,
            placeName = "Lisbon, Portugal",
            headsUp = listOf(
                WidgetSnapshot.HeadsUp("Wind up to 48 km/h", HeadsUpKind.STRONG_WIND.name, WidgetSnapshot.Severity.CAUTION),
                WidgetSnapshot.HeadsUp("Public holiday tomorrow", HeadsUpKind.PUBLIC_HOLIDAY.name, WidgetSnapshot.Severity.INFO),
            ),
            emergency = WidgetSnapshot.Emergency(countryName = "Portugal", generalNumber = "112"),
            embassy = WidgetSnapshot.Embassy(name = "Embassy of Finland in Lisbon", hotline = "+00 123 456 789"),
            daylight = WidgetDaylight(
                sunrise = sunrise,
                sunset = sunset,
                nextSunrise = sunrise.plus(Duration.ofHours(24)),
                timeZoneIdentifier = zone.id,
            ),
        )
    }
}
