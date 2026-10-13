package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightLocation
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightReference
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.safety.CurrentCountry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// The pure content of the Weather, Local Info, Fuel Prices and Emergency Care cards: what each card
// says, decided without any UI so it can be unit tested. The composables only draw it.

/**
 * The place and locale a card writes its times and dates in: clock times and day boundaries follow
 * [zone] (the weather or daylight location's own time zone), the wording follows [formats].
 */
data class CardTime(
    val zone: ZoneId,
    val formats: HeadsUpFormats = HeadsUpFormats(),
) {
    val locale get() = formats.locale

    /** "18:12" or "6:12 PM", in [zone]. */
    fun clock(instant: Instant): String = formats.clockTime.format(instant.atZone(zone))

    /** The calendar day that contains [instant] in [zone]. */
    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** "8 Feb". */
    fun dayMonth(date: LocalDate): String = formats.dayMonth(date)
}

/** Where the traveller is, for the cards that depend on the place. */
object CardLocation {
    /**
     * The country the safety cards speak about: device location first, then IP geolocation, then the
     * Local Info card.
     */
    fun countryCode(snapshot: DashboardSnapshot): String? =
        CurrentCountry.code(
            deviceCountryCode = snapshot.travelContext.deviceCountryCode,
            ipCountryCode = snapshot.travelContext.countryCode,
            localInfoCountryCode = snapshot.localInfo.countryCode,
        )

    /**
     * Where to read the day's light: the device location, else the IP location, in that place's own
     * time zone (the IP location brings its own; the device location uses [deviceZone]).
     */
    fun daylightReference(snapshot: DashboardSnapshot, deviceZone: ZoneId): DaylightReference? {
        val context = snapshot.travelContext
        return DaylightReference.resolve(
            deviceLocation = DaylightLocation(context.deviceLatitude, context.deviceLongitude, zoneId = null),
            ipLocation = DaylightLocation(context.latitude, context.longitude, context.timeZoneId),
            snapshotZoneId = deviceZone.id,
            fallbackZone = deviceZone,
        )
    }
}
