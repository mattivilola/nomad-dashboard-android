package com.iloapps.nomaddashboard.core.travel.headsup

import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import com.iloapps.nomaddashboard.core.travel.stays.StayHeadsUpFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How the heads-up rules write times, dates, country names and prices. Injectable so the UI layer can
 * follow the device's locale and 12/24-hour preference; this module reads no system setting itself.
 */
data class HeadsUpFormats(
    val locale: Locale = Locale.getDefault(),
    /** A clock time already expressed in the zone of the place it is about (device or daylight location). */
    val clockTime: ClockTimeFormatter = ClockTimeFormatter.localized(locale),
    /** Day and abbreviated month, for example "8 Feb". */
    val dayMonth: (LocalDate) -> String = { DateTimeFormatter.ofPattern("d MMM", locale).format(it) },
    /** Full weekday name, for example "Wednesday". */
    val weekday: (LocalDate) -> String = { DateTimeFormatter.ofPattern("EEEE", locale).format(it) },
    val countryName: (String) -> String = CountryNames.forLocale(locale)::name,
) {
    internal val stay: StayHeadsUpFormat
        get() = StayHeadsUpFormat(countryName = countryName, day = dayMonth)
}
