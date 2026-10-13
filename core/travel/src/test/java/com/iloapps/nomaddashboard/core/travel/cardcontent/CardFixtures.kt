package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Shared fixtures: noon UTC on 12 Oct 2026, written as a British traveller on a 24-hour clock. */
internal object CardFixtures {
    val utc: ZoneId = ZoneId.of("UTC")
    val now: Instant = Instant.parse("2026-10-12T12:00:00Z")

    val time = CardTime(
        zone = utc,
        formats = HeadsUpFormats(locale = Locale.UK, clockTime = ClockTimeFormatter.Hour24),
    )

    fun at(hours: Double): Instant = now.plusMillis((hours * 3_600_000).toLong())

    fun utcInstant(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute, second).atZone(utc).toInstant()

    fun hourly(
        hours: Double,
        uv: Int? = null,
        temperature: Double? = 20.0,
        rain: Int? = null,
        windKph: Double? = null,
        windDegrees: Double? = null,
        code: Int? = 0,
    ) = WeatherHourlyForecastSlot(
        at = at(hours),
        summary = "Clear",
        temperatureCelsius = temperature,
        rainChancePercent = rain,
        windSpeedKph = windKph,
        windDirectionDegrees = windDegrees,
        weatherCode = code,
        uvIndex = uv,
    )
}
