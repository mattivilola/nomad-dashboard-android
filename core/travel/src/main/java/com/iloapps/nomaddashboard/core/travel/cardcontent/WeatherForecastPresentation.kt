package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * The wind stat of the weather card: "14 km/h NW" with "3.9 m/s" under it. Ported from the macOS
 * `WeatherWindMetricPresentation`.
 */
data class WindMetric(
    val primary: String,
    val secondary: String?,
) {
    companion object {
        fun of(weather: WeatherSnapshot): WindMetric =
            WindMetric(
                primary = summary(weather.windSpeedKph, weather.windDirectionDegrees),
                secondary = weather.windSpeedKph?.let(TravelFormatters::metersPerSecond),
            )

        /** "14 km/h NW", just the speed or the direction when only one is known, else "n/a". */
        fun summary(speedKph: Double?, directionDegrees: Double?): String =
            listOfNotNull(
                speedKph?.let(TravelFormatters::kilometersPerHour),
                directionDegrees?.let(TravelFormatters::compassDirection),
            ).joinToString(" ").ifEmpty { TravelFormatters.Unavailable }
    }
}

/** One hour of the hourly strip. */
data class HourlyChip(
    val time: Instant,
    /** "Now" for the hour that is under way, else the clock time ("15:00"). */
    val label: String,
    val weatherCode: Int?,
    val summary: String,
    val temperature: String,
    /** "40%", null without a rain chance. */
    val rainChance: String?,
    /** "14 km/h NW", null without a wind reading. */
    val wind: String?,
) {
    /** The whole chip as one sentence, for accessibility. */
    val accessibilityLabel: String
        get() = listOfNotNull(
            label,
            summary,
            temperature,
            rainChance?.let { "rain $it" },
            wind,
        ).joinToString(", ")
}

/** One day of the outlook: "Tomorrow · Partly cloudy · 12° / 21°". */
data class DayOutlook(
    val date: LocalDate,
    /** "Today", "Tomorrow", else the short weekday ("Wed"). */
    val label: String,
    val summary: String,
    val weatherCode: Int?,
    /** "12° / 21°". */
    val range: String,
    /** "Rain 40%", null without a rain chance. */
    val rainChance: String?,
    /** "14 km/h NW", null without a wind reading. */
    val wind: String?,
)

/**
 * What the forecast part of the weather card shows: the hourly strip (always visible), the Tomorrow row
 * and the folding 7-day outlook. Ported from the macOS `WeatherForecastPresentation`.
 *
 * @property showsOutlook Whether the 7-day outlook disclosure is offered (the snapshot has daily data).
 * @property isOutlookExpanded Whether the outlook is open; only the outlook folds away.
 * @property showsTomorrow The Tomorrow row stands in for the outlook while it is folded; once the outlook
 * is open, tomorrow is one of its days.
 */
data class WeatherForecastPresentation(
    val hourly: List<HourlyChip>,
    val tomorrow: DayOutlook?,
    val days: List<DayOutlook>,
    val showsOutlook: Boolean,
    val isOutlookExpanded: Boolean,
) {
    val showsTomorrow: Boolean
        get() = tomorrow != null && !isOutlookExpanded

    companion object {
        /** The strip covers the next day, which is all the snapshot keeps. */
        const val HOURLY_LIMIT = 24
        const val OUTLOOK_DAYS = 7

        /** @param outlookExpanded the user's choice for the 7-day outlook (the `weatherForecastExpanded` setting). */
        fun of(
            weather: WeatherSnapshot,
            time: CardTime,
            now: Instant,
            outlookExpanded: Boolean,
        ): WeatherForecastPresentation {
            val today = time.localDate(now)
            val days = weather.dailyForecast.take(OUTLOOK_DAYS).map { dayOutlook(it, today, time) }
            val showsOutlook = days.isNotEmpty()
            return WeatherForecastPresentation(
                hourly = hourlyChips(weather, time, now),
                tomorrow = weather.dailyForecast.firstOrNull { it.date == today.plusDays(1) }
                    ?.let { dayOutlook(it, today, time) },
                days = days,
                showsOutlook = showsOutlook,
                isOutlookExpanded = showsOutlook && outlookExpanded,
            )
        }

        /** The hours that have not ended yet, starting with the one under way. */
        fun hourlyChips(weather: WeatherSnapshot, time: CardTime, now: Instant): List<HourlyChip> =
            weather.hourlyForecast
                .filter { it.at.plusSeconds(3_600) > now }
                .sortedBy { it.at }
                .take(HOURLY_LIMIT)
                .map { slot -> hourlyChip(slot, if (slot.at <= now) "Now" else time.clock(slot.at)) }

        private fun hourlyChip(slot: WeatherHourlyForecastSlot, label: String): HourlyChip =
            HourlyChip(
                time = slot.at,
                label = label,
                weatherCode = slot.weatherCode,
                summary = slot.summary,
                temperature = TravelFormatters.temperature(slot.temperatureCelsius),
                rainChance = slot.rainChancePercent?.let(::percent),
                wind = windOrNull(slot.windSpeedKph, slot.windDirectionDegrees),
            )

        private fun dayOutlook(day: WeatherDayForecast, today: LocalDate, time: CardTime): DayOutlook =
            DayOutlook(
                date = day.date,
                label = when (day.date) {
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    else -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, time.locale)
                },
                summary = day.summary,
                weatherCode = day.weatherCode,
                range = "${TravelFormatters.temperature(day.minCelsius)} / ${TravelFormatters.temperature(day.maxCelsius)}",
                rainChance = day.rainChancePercent?.let { "Rain ${percent(it)}" },
                wind = windOrNull(day.windSpeedKph, day.windDirectionDegrees),
            )

        private fun percent(value: Int): String = TravelFormatters.percentage(value.toDouble())

        private fun windOrNull(speedKph: Double?, directionDegrees: Double?): String? =
            WindMetric.summary(speedKph, directionDegrees).takeUnless { it == TravelFormatters.Unavailable }
    }
}
