package com.iloapps.nomaddashboard.core.travel.headsup

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightHeadsUp
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightLocation
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightReference
import com.iloapps.nomaddashboard.core.travel.environment.AirQualityHeadsUp
import com.iloapps.nomaddashboard.core.travel.environment.HourlyUvReading
import com.iloapps.nomaddashboard.core.travel.environment.LocalRhythmHeadsUp
import com.iloapps.nomaddashboard.core.travel.environment.RemainingDaylight
import com.iloapps.nomaddashboard.core.travel.environment.UvHeadsUp
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPlace
import com.iloapps.nomaddashboard.core.travel.safety.CurrentCountry
import com.iloapps.nomaddashboard.core.travel.stays.StayHeadsUpInput
import com.iloapps.nomaddashboard.core.travel.stays.StayHeadsUps
import java.time.Instant
import java.time.ZoneId

/**
 * Derives the few actionable heads-up items for a travelling remote worker from a dashboard snapshot,
 * ported from the macOS `DashboardInsightEngine`.
 *
 * A pure function of its inputs: at most [MAX_ITEMS] items, one per [HeadsUpKind], ranked by severity
 * (attention first) and then by the travel profile's tie-break order. Network and battery health are
 * intentionally excluded: the verdict strip owns those. Time-relative items must be re-evaluated as
 * time passes (about once a minute).
 *
 * @param localRhythm the offline calendar behind the local-rhythm heads-up.
 */
class HeadsUpEngine(
    private val localRhythm: LocalRhythmCalendar,
) {
    /**
     * @param countryDays the user's recorded country days, for the stay heads-ups.
     * @param zone the device's time zone: it decides today's date and formats the weather times.
     * @param limit the most items to return.
     */
    fun evaluate(
        snapshot: DashboardSnapshot,
        settings: AppSettings,
        countryDays: List<VisitedCountryDay>,
        now: Instant,
        zone: ZoneId,
        formats: HeadsUpFormats = HeadsUpFormats(),
        limit: Int = MAX_ITEMS,
    ): List<HeadsUpItem> {
        if (limit <= 0) return emptyList()

        val today = now.atZone(zone).toLocalDate()
        val deviceClock = { time: Instant -> formats.clockTime.format(time.atZone(zone)) }
        val travelContext = snapshot.travelContext
        val currentCountry = CurrentCountry.code(
            deviceCountryCode = travelContext.deviceCountryCode,
            ipCountryCode = travelContext.countryCode,
            localInfoCountryCode = snapshot.localInfo.countryCode,
        )
        val daylightReference = daylightReference(snapshot, zone)

        val items = mutableListOf<HeadsUpItem>()
        fun add(kind: HeadsUpKind, decision: HeadsUpDecision?, card: DashboardCardId) {
            if (decision != null) {
                items += HeadsUpItem(
                    kind = kind,
                    severity = decision.severity,
                    title = decision.title,
                    statusText = decision.statusText,
                    detail = decision.detail,
                    relatedCard = card,
                )
            }
        }

        // DATA_CAP is never produced: Android has no Data Guard, so there is no metered-usage signal.

        if (DashboardCardId.STAYS.isFeatureEnabled(settings)) {
            val stayInput = StayHeadsUpInput(
                countryDays = countryDays,
                today = today,
                currentCountryCode = currentCountry,
                citizenships = settings.citizenshipCountryCodes,
                stayAllowances = settings.stayAllowances,
                schengenCounterEnabled = settings.schengenCounterEnabled,
                taxResidenceDaysEnabled = settings.taxResidenceDaysEnabled,
                taxResidenceThresholdDays = settings.taxResidenceThresholdDays,
            )
            StayHeadsUps.evaluate(stayInput, formats.stay).forEach { add(it.kind, it.decision, DashboardCardId.STAYS) }
        }

        TravelAlertHeadsUps.evaluate(snapshot.travelAlerts)
            .forEach { add(it.kind, it.decision, DashboardCardId.TRAVEL_ALERTS) }

        add(HeadsUpKind.STRONG_WIND, WeatherHeadsUps.wind(snapshot.weather, now, deviceClock), DashboardCardId.WEATHER)
        add(HeadsUpKind.RAIN_SOON, WeatherHeadsUps.rain(snapshot.weather, now, deviceClock), DashboardCardId.WEATHER)
        add(HeadsUpKind.UV_INDEX, uv(snapshot, daylightReference, now, zone, formats), DashboardCardId.WEATHER)

        if (settings.isAirQualityActive) {
            add(
                HeadsUpKind.AIR_QUALITY,
                AirQualityHeadsUp.evaluate(snapshot.airQuality, now, deviceClock),
                DashboardCardId.WEATHER,
            )
        }

        add(
            HeadsUpKind.PUBLIC_HOLIDAY,
            PublicHolidayHeadsUp.evaluate(snapshot.localInfo, today, formats.weekday),
            DashboardCardId.LOCAL_INFO,
        )
        add(
            HeadsUpKind.LOCAL_RHYTHM,
            LocalRhythmHeadsUp.evaluate(
                calendar = localRhythm,
                countryCode = currentCountry,
                places = listOf(
                    LocalRhythmPlace(travelContext.deviceCountryCode, travelContext.deviceRegion),
                    LocalRhythmPlace(travelContext.countryCode, travelContext.region),
                ),
                now = now,
                zone = zone,
                formatDay = formats.dayMonth,
            ),
            DashboardCardId.LOCAL_INFO,
        )
        add(HeadsUpKind.FUEL_NEARBY, FuelHeadsUp.evaluate(snapshot.fuelPrices, formats.locale), DashboardCardId.FUEL_PRICES)

        if (settings.travelProfile == TravelProfile.ROAD_TRIP && daylightReference != null) {
            add(
                HeadsUpKind.DAYLIGHT,
                DaylightHeadsUp.evaluate(daylightReference, now, formats.clockTime)?.decision,
                DashboardCardId.WEATHER,
            )
        }

        val rank = HeadsUpKind.tieBreakOrder(settings.travelProfile).withIndex().associate { it.value to it.index }
        return items
            .sortedWith(
                compareByDescending<HeadsUpItem> { it.severity }
                    .thenBy { rank[it.kind] ?: Int.MAX_VALUE },
            )
            .distinctBy { it.kind }
            .take(limit)
    }

    /**
     * The strongest UV left in today's daylight. The daylight ends at the sunset when the location is
     * known; without a location, or in midnight sun and polar night, it is the rest of the calendar day.
     */
    private fun uv(
        snapshot: DashboardSnapshot,
        reference: DaylightReference?,
        now: Instant,
        zone: ZoneId,
        formats: HeadsUpFormats,
    ): HeadsUpDecision? {
        val weather = snapshot.weather
        val sunset = reference?.solarDay(now)?.takeUnless { it.isPolarDay || it.isPolarNight }?.sunset
        val endOfDay = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val clockZone = reference?.zone ?: zone

        return UvHeadsUp.evaluate(
            // A weather snapshot that was never fetched has no current reading to trust.
            currentUvIndex = weather.uvIndex.takeIf { weather.fetchedAt != null },
            currentFetchedAt = weather.fetchedAt ?: now,
            hourly = weather.hourlyForecast.map { HourlyUvReading(it.at, it.uvIndex) },
            now = now,
            daylight = RemainingDaylight(start = now, endExclusive = sunset ?: endOfDay),
            clock = { time -> formats.clockTime.format(time.atZone(clockZone)) },
        )
    }

    /**
     * The device location first, else the IP location. The device location has no zone of its own, so
     * it uses the device's [zone]; the IP location brings its own.
     */
    private fun daylightReference(snapshot: DashboardSnapshot, zone: ZoneId): DaylightReference? {
        val travelContext = snapshot.travelContext
        return DaylightReference.resolve(
            deviceLocation = DaylightLocation(travelContext.deviceLatitude, travelContext.deviceLongitude, zoneId = null),
            ipLocation = DaylightLocation(travelContext.latitude, travelContext.longitude, travelContext.timeZoneId),
            snapshotZoneId = zone.id,
            fallbackZone = zone,
        )
    }

    companion object {
        /** The most heads-up items shown under the glance overview. */
        const val MAX_ITEMS = 3
    }
}
