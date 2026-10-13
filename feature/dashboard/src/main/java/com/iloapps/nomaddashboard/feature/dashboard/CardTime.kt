package com.iloapps.nomaddashboard.feature.dashboard

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardTime
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The [CardTime] a card writes its times in: the clock of [zone] (the weather or daylight location's own
 * time zone), in the device's locale and 12/24-hour setting, like the heads-up items.
 */
@Composable
internal fun rememberCardTime(zone: ZoneId): CardTime {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(zone, locale, is24Hour) {
        val clock = if (is24Hour) {
            ClockTimeFormatter.Hour24
        } else {
            val twelveHour = DateTimeFormatter.ofPattern("h:mm a", locale)
            ClockTimeFormatter { twelveHour.format(it) }
        }
        CardTime(zone = zone, formats = HeadsUpFormats(locale = locale, clockTime = clock))
    }
}
