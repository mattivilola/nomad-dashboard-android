package com.iloapps.nomaddashboard.core.travel.daylight

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** A place a daylight reference can be built from: coordinates and, when known, its own IANA time zone. */
data class DaylightLocation(
    val latitude: Double?,
    val longitude: Double?,
    val zoneId: String?,
)

/** Where, and in which time zone, to read the day's light for a dashboard snapshot. */
data class DaylightReference(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
) {
    fun solarDay(on: LocalDate): SolarDay = SolarDay.compute(latitude, longitude, on, zone)

    /** The solar day for the calendar day that contains [instant] in the place's time zone. */
    fun solarDay(at: Instant): SolarDay = SolarDay.compute(latitude, longitude, at, zone)

    /** The calendar day that contains [instant] in the place's time zone. */
    fun localDate(at: Instant): LocalDate = at.atZone(zone).toLocalDate()

    companion object {
        /**
         * The device location first, else the IP location; null when neither has valid coordinates.
         *
         * The time zone is the chosen location's own, then [snapshotZoneId], then [fallbackZone]. (The
         * snapshot's zone follows the device location only, so an IP-only location needs its own.)
         */
        fun resolve(
            deviceLocation: DaylightLocation?,
            ipLocation: DaylightLocation?,
            snapshotZoneId: String? = null,
            fallbackZone: ZoneId = ZoneId.systemDefault(),
        ): DaylightReference? {
            val source = listOfNotNull(deviceLocation, ipLocation)
                .firstOrNull { it.latitude != null && it.longitude != null }
            val latitude = source?.latitude
            val longitude = source?.longitude
            if (latitude == null || longitude == null ||
                !latitude.isFinite() || !longitude.isFinite() ||
                abs(latitude) > 90 || abs(longitude) > 180
            ) {
                return null
            }

            return DaylightReference(
                latitude = latitude,
                longitude = longitude,
                zone = zoneOrNull(source.zoneId) ?: zoneOrNull(snapshotZoneId) ?: fallbackZone,
            )
        }

        private fun zoneOrNull(id: String?): ZoneId? =
            id?.takeIf(String::isNotBlank)?.let {
                try {
                    ZoneId.of(it)
                } catch (_: DateTimeException) {
                    null
                }
            }
    }
}
