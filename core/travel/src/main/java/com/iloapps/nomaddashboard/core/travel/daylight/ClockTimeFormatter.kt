package com.iloapps.nomaddashboard.core.travel.daylight

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Formats a clock time already expressed in the place's own time zone, for example "18:12" or "6:12 PM".
 *
 * Injectable so the UI layer can follow the device's 12/24-hour preference without this module
 * reading any system setting.
 */
fun interface ClockTimeFormatter {
    fun format(time: ZonedDateTime): String

    companion object {
        /** 24-hour "HH:mm", for example "07:45". */
        val Hour24: ClockTimeFormatter = of(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))

        /** The locale's short time style, for example "07:45" in en-GB and "7:45 AM" in en-US. */
        fun localized(locale: Locale): ClockTimeFormatter =
            of(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

        private fun of(formatter: DateTimeFormatter) = ClockTimeFormatter { formatter.format(it) }
    }
}
