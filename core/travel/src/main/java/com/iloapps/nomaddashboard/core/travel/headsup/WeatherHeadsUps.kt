package com.iloapps.nomaddashboard.core.travel.headsup

import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Strong wind and rain-soon heads-ups from the current reading and the next hours of the forecast. */
object WeatherHeadsUps {
    const val WIND_CAUTION_KPH = 40.0
    const val WIND_ATTENTION_KPH = 60.0
    const val RAIN_CHANCE_THRESHOLD_PERCENT = 60

    /** How far ahead the hourly forecast is read. */
    val LOOK_AHEAD: Duration = Duration.ofHours(6)

    /** A current reading older than this is ignored, though the forecast still counts. */
    val CURRENT_READING_MAX_AGE: Duration = Duration.ofHours(2)

    private val SlotLength: Duration = Duration.ofHours(1)

    private data class WindPeak(val kph: Double, val degrees: Double?, val at: Instant?)

    /**
     * The strongest wind now or in the next six hours: caution from 40 km/h, attention from 60. The
     * earliest reading wins a tie.
     *
     * @param clock formats an instant as a clock time in the traveller's zone and locale.
     */
    fun wind(weather: WeatherSnapshot, now: Instant, clock: (Instant) -> String): HeadsUpDecision? {
        var peak: WindPeak? = null

        val currentKph = weather.windSpeedKph
        if (isCurrentReadingFresh(weather, now) && currentKph != null) {
            peak = WindPeak(currentKph, weather.windDirectionDegrees, null)
        }
        for (slot in upcomingSlots(weather, now)) {
            val kph = slot.windSpeedKph ?: continue
            if (kph > (peak?.kph ?: Double.NEGATIVE_INFINITY)) {
                peak = WindPeak(kph, slot.windDirectionDegrees, slot.at)
            }
        }

        val strongest = peak?.takeIf { it.kph >= WIND_CAUTION_KPH } ?: return null
        val timeText = strongest.at?.let { "around ${clock(it)}" } ?: "now"
        val detail = strongest.degrees
            ?.let { "From ${TravelFormatters.compassDirection(it)} $timeText" }
            ?: timeText.replaceFirstChar { it.titlecase(Locale.ROOT) }

        // Rounded half to even, like the macOS `%.0f`.
        val speedText = Math.rint(strongest.kph).toLong().toString()
        return HeadsUpDecision(
            severity = if (strongest.kph >= WIND_ATTENTION_KPH) HeadsUpSeverity.ATTENTION else HeadsUpSeverity.CAUTION,
            title = "Wind up to $speedText km/h",
            detail = detail,
            statusText = "Wind $speedText",
        )
    }

    /**
     * Rain at 60% or more: "now" from a fresh current reading or the hourly slot that covers the
     * present, else from the first qualifying hour in the next six hours.
     */
    fun rain(weather: WeatherSnapshot, now: Instant, clock: (Instant) -> String): HeadsUpDecision? {
        fun decision(chancePercent: Int, startsAt: Instant?) = HeadsUpDecision(
            severity = HeadsUpSeverity.INFO,
            title = startsAt?.let { "Rain likely from ${clock(it)}" } ?: "Rain likely now",
            detail = "$chancePercent% chance",
            statusText = startsAt?.let { "Rain ${clock(it)}" } ?: "Rain now",
        )

        val current = weather.rainChancePercent
        if (isCurrentReadingFresh(weather, now) && current != null && current >= RAIN_CHANCE_THRESHOLD_PERCENT) {
            return decision(current, null)
        }

        // The hourly slot that began before now but still covers it counts as "now".
        val covering = weather.hourlyForecast.firstOrNull { it.at <= now && now < it.at.plus(SlotLength) }
        val coveringChance = covering?.rainChancePercent
        if (coveringChance != null && coveringChance >= RAIN_CHANCE_THRESHOLD_PERCENT) {
            return decision(coveringChance, null)
        }

        return upcomingSlots(weather, now)
            .firstOrNull { (it.rainChancePercent ?: 0) >= RAIN_CHANCE_THRESHOLD_PERCENT }
            ?.let { decision(it.rainChancePercent ?: 0, it.at) }
    }

    private fun isCurrentReadingFresh(weather: WeatherSnapshot, now: Instant): Boolean =
        weather.fetchedAt?.let { Duration.between(it, now) <= CURRENT_READING_MAX_AGE } ?: false

    /** Hourly slots in `[now, now + 6 h]`, earliest first. */
    private fun upcomingSlots(weather: WeatherSnapshot, now: Instant): List<WeatherHourlyForecastSlot> {
        val end = now.plus(LOOK_AHEAD)
        return weather.hourlyForecast.filter { it.at >= now && it.at <= end }.sortedBy { it.at }
    }
}
