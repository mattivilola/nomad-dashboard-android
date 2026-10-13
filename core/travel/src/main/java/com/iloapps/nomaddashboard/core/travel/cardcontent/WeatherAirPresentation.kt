package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.iloapps.nomaddashboard.core.model.AirQualitySnapshot
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.environment.EuropeanAqiBand
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The UV index stat of the weather card: "UV 7 (high)" with the day's peak when it is still to come.
 * Ported from the macOS `WeatherUVPresentation`.
 *
 * Bands follow the WHO UV index scale: 0-2 low, 3-5 moderate, 6-7 high, 8-10 very high, 11+ extreme.
 * https://www.who.int/news-room/questions-and-answers/item/radiation-the-ultraviolet-(uv)-index
 *
 * @property value "7 (high)".
 * @property peak "peak 9 at 13:00", only when a higher reading follows later today.
 * @property tone Sand for high to very high, coral for extreme, neutral below high.
 */
data class WeatherUvPresentation(
    val value: String,
    val peak: String?,
    val tone: CardTone,
) {
    /** "UV 7 (high)". */
    val text: String
        get() = "UV $value"

    companion object {
        /** Null when the weather has no UV reading. */
        fun of(weather: WeatherSnapshot, time: CardTime, now: Instant): WeatherUvPresentation? {
            val uv = weather.uvIndex ?: return null

            val today = time.localDate(now)
            val highestLater = weather.hourlyForecast.asSequence()
                .filter { it.at >= now && time.localDate(it.at) == today }
                .mapNotNull { slot -> slot.uvIndex?.let { slot.at to it } }
                .maxByOrNull { it.second }
            val peak = highestLater
                ?.takeIf { it.second > uv }
                ?.let { (at, peakUv) -> "peak $peakUv at ${time.clock(at)}" }

            return WeatherUvPresentation(value = "$uv (${bandName(uv)})", peak = peak, tone = tone(uv))
        }

        fun bandName(uv: Int): String = when {
            uv < 3 -> "low"
            uv < 6 -> "moderate"
            uv < 8 -> "high"
            uv < 11 -> "very high"
            else -> "extreme"
        }

        fun tone(uv: Int): CardTone = when {
            uv < 6 -> CardTone.NEUTRAL
            uv < 11 -> CardTone.CAUTION
            else -> CardTone.ATTENTION
        }
    }
}

/**
 * The air quality block of the weather card: "AQI 38 · Fair · mostly PM10" and a 12 hour outlook.
 * Ported from the macOS `WeatherAirQualityPresentation`.
 *
 * @property headline "AQI 38 · Fair · mostly PM10".
 * @property tone Good, fair and moderate stay neutral; poor is sand; very poor and worse are coral.
 * @property outlookCaption "Next 12 h · peak 64 at 15:00", null without an outlook.
 * @property help The concentrations, for accessibility.
 */
data class WeatherAirQualityPresentation(
    val headline: String,
    val tone: CardTone,
    val bars: List<Bar>,
    val outlookCaption: String?,
    val help: String,
) {
    /** One hour of the outlook as a bar. [height] is 0..1: the European AQI against 100, the top of "very poor". */
    data class Bar(
        val time: Instant,
        val height: Double,
        val tone: CardTone,
        val help: String,
    )

    companion object {
        /** Bars never get shorter than this, so a clean hour is still visible. */
        private const val MINIMUM_BAR_HEIGHT = 0.08

        /** Null unless air quality is on for the weather location and a reading exists. */
        fun of(
            airQuality: AirQualitySnapshot?,
            settings: AppSettings,
            time: CardTime,
            now: Instant,
        ): WeatherAirQualityPresentation? {
            if (!settings.isAirQualityActive || airQuality == null) return null

            val band = EuropeanAqiBand.fromIndex(airQuality.europeanAqi)
            val headline = listOfNotNull(
                "AQI ${airQuality.europeanAqi}",
                band.title,
                airQuality.mainPollutant?.let { "mostly ${it.displayName}" },
            ).joinToString(SEPARATOR)

            val upcoming = airQuality.forecastSlots
                .filter { it.time > now.minusSeconds(3_600) }
                .sortedBy { it.time }
                .mapNotNull { slot -> slot.europeanAqi?.let { slot.time to it } }
            val bars = upcoming.map { (slotTime, aqi) ->
                val slotBand = EuropeanAqiBand.fromIndex(aqi)
                Bar(
                    time = slotTime,
                    height = min(max(aqi.toDouble() / EuropeanAqiBand.EXTREMELY_POOR_THRESHOLD, MINIMUM_BAR_HEIGHT), 1.0),
                    tone = tone(slotBand),
                    help = "${time.clock(slotTime)} · AQI $aqi · ${slotBand.title}",
                )
            }
            val outlookCaption = upcoming.maxByOrNull { it.second }
                ?.let { (peakTime, peak) -> "Next ${upcoming.size} h · peak $peak at ${time.clock(peakTime)}" }

            val concentrations = listOfNotNull(
                airQuality.pm2_5?.let { "PM2.5 ${it.roundToInt()} µg/m³" },
                airQuality.pm10?.let { "PM10 ${it.roundToInt()} µg/m³" },
                airQuality.ozone?.let { "ozone ${it.roundToInt()} µg/m³" },
                airQuality.nitrogenDioxide?.let { "NO₂ ${it.roundToInt()} µg/m³" },
            )

            return WeatherAirQualityPresentation(
                headline = headline,
                tone = tone(band),
                bars = bars,
                outlookCaption = outlookCaption,
                help = (listOf(headline) + concentrations).joinToString(SEPARATOR),
            )
        }

        fun tone(band: EuropeanAqiBand): CardTone = when (band) {
            EuropeanAqiBand.GOOD, EuropeanAqiBand.FAIR, EuropeanAqiBand.MODERATE -> CardTone.NEUTRAL
            EuropeanAqiBand.POOR -> CardTone.CAUTION
            EuropeanAqiBand.VERY_POOR, EuropeanAqiBand.EXTREMELY_POOR -> CardTone.ATTENTION
        }

        private const val SEPARATOR = " · "
    }
}
