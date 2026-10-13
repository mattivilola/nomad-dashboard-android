package com.iloapps.nomaddashboard.core.data.openmeteo

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Open-Meteo returns local wall-clock times without an offset ("2026-10-12T14:00") plus the
 * location's time zone and UTC offset.
 */
internal object OpenMeteoTimestamps {
    /**
     * Parses every value in the location's time zone, falling back to the UTC offset and then to UTC.
     * Returns null when any value cannot be parsed so callers never pair a value with the wrong hour.
     */
    fun parseAll(
        rawValues: List<String>,
        timezone: String?,
        utcOffsetSeconds: Int?,
    ): List<Instant>? {
        val zone = zone(timezone, utcOffsetSeconds)
        return rawValues.map { raw ->
            runCatching { LocalDateTime.parse(raw).atZone(zone).toInstant() }.getOrNull() ?: return null
        }
    }

    private fun zone(timezone: String?, utcOffsetSeconds: Int?): ZoneId =
        timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: utcOffsetSeconds?.let { runCatching { ZoneOffset.ofTotalSeconds(it) }.getOrNull() }
            ?: ZoneOffset.UTC
}
