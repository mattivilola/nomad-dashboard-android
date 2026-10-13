package com.iloapps.nomaddashboard.core.travel.arrival

import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.travel.glance.TimeZoneCityName
import com.iloapps.nomaddashboard.core.travel.glance.trimmedNonEmpty
import com.iloapps.nomaddashboard.core.travel.reference.CountryElectricity
import com.iloapps.nomaddashboard.core.travel.reference.DrivingSide
import com.iloapps.nomaddashboard.core.travel.reference.TravelReferenceData
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPeriod
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPlace
import com.iloapps.nomaddashboard.core.travel.safety.ConsularContacts
import com.iloapps.nomaddashboard.core.travel.safety.ConsularContactsDirectory
import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionIndex
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionMatch
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyLookup
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumbers
import com.iloapps.nomaddashboard.core.travel.safety.MissionCoordinate
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * What to tell the traveller on arrival. Build it with [ArrivalBriefingBuilder]; each section is null
 * when its data is missing or does not apply.
 *
 * There is no Data Guard hint ("On a local SIM or roaming? Turn on Data Guard monitoring."): Android
 * has no Data Guard.
 */
data class ArrivalBriefing(
    val countryCode: String,
    /** Localized country name, for example "Morocco". */
    val countryName: String,
    val time: Time?,
    val emergency: EmergencyNumbers?,
    val embassy: Embassy?,
    val road: Road?,
    val power: Power?,
    val money: Money?,
    val holiday: Holiday?,
    /**
     * Cultural or religious periods (Ramadan, Lunar New Year, ...) that are on now or start within
     * [ArrivalBriefingBuilder.RHYTHM_LOOK_AHEAD_DAYS] days, earliest first. Empty when there are none.
     */
    val rhythm: List<LocalRhythmPeriod>,
    val advisory: Advisory?,
) {
    val title: String
        get() = "Welcome to $countryName"

    /** The local time zone and how it compares with home. */
    data class Time(
        val timeZoneId: String,
        /** UTC offset at the time of the briefing, in seconds. */
        val utcOffsetSeconds: Int,
        /** Null when no home time zone is known. */
        val home: Home?,
    ) {
        /** For example "UTC+1" or "UTC+5:30". */
        val utcOffsetText: String
            get() {
                val minutes = abs(utcOffsetSeconds) / 60
                val sign = if (utcOffsetSeconds < 0) "-" else "+"
                val hours = minutes / 60
                val remainder = minutes % 60
                return if (remainder == 0) "UTC$sign$hours" else "UTC$sign$hours:${"%02d".format(Locale.ROOT, remainder)}"
            }

        data class Home(
            val timeZoneId: String,
            /** City part of the identifier, for example "Helsinki" for "Europe/Helsinki". */
            val cityName: String,
            /** Home's UTC offset minus the local one, in seconds: positive when home is ahead. */
            val offsetFromLocalSeconds: Int,
        ) {
            /** For example "Home (Helsinki) is 2 h ahead" or "Home (Helsinki) is on the same time". */
            val differenceText: String
                get() {
                    val minutes = abs(offsetFromLocalSeconds) / 60
                    if (minutes == 0) return "Home ($cityName) is on the same time"

                    val hours = minutes / 60
                    val remainder = minutes % 60
                    val amount = when {
                        hours == 0 -> "$remainder min"
                        remainder == 0 -> "$hours h"
                        else -> "$hours h $remainder min"
                    }
                    return "Home ($cityName) is $amount ${if (offsetFromLocalSeconds > 0) "ahead" else "behind"}"
                }
        }
    }

    /** The nearest mission of the traveller's own state and the hotline of its foreign ministry. */
    data class Embassy(
        /** Citizenship the mission and hotline belong to. */
        val citizenshipCode: String,
        /**
         * The nearest mission of the traveller's own state, or of a state that can help when their own
         * has none here (see [fallbackReason]).
         */
        val nearestMission: DiplomaticMissionMatch?,
        /** Set when [nearestMission] belongs to another state under an EU or Nordic arrangement. */
        val fallbackReason: EmbassyLookup.FallbackReason?,
        val consularContacts: ConsularContacts?,
    )

    data class Road(
        val side: DrivingSide,
        /** True when the previous country drove on the other side, so the traveller must switch. */
        val changesFromPreviousCountry: Boolean,
    ) {
        /** For example "Drive on the left". */
        val text: String
            get() = when (side) {
                DrivingSide.LEFT -> "Drive on the left"
                DrivingSide.RIGHT -> "Drive on the right"
            }
    }

    data class Power(
        val electricity: CountryElectricity,
        /** True when plugs, voltage or frequency differ from the previous country. */
        val differsFromPreviousCountry: Boolean,
    )

    data class Money(
        /** ISO 4217 code, for example "MAD". */
        val currencyCode: String,
        /** Localized currency name, for example "Moroccan Dirham". */
        val currencyName: String?,
        /** True when the previous country used another currency. */
        val changesFromPreviousCountry: Boolean,
    )

    /** A public holiday today or tomorrow, when shops and offices may be closed. */
    data class Holiday(
        val name: String,
        /** 0 for today, 1 for tomorrow. */
        val daysFromToday: Int,
    )

    data class Advisory(
        /** Caution or worse. */
        val severity: TravelAlertSeverity,
        val summary: String,
        val sourceName: String,
    )
}

/**
 * Composes the [ArrivalBriefing] for an [ArrivalEvent] from data the dashboard already has.
 *
 * Sections about the country (local holiday, travel advisory) are only included when the snapshot is
 * already about the arrival country, so a lagging refresh never shows the previous country's data.
 *
 * @param localRhythm the offline calendar behind the rhythm section.
 * @param locale used for the country and currency names.
 * @param missionIndex the embassy dataset; only read when the traveller holds a citizenship other than
 *   the arrival country's.
 */
class ArrivalBriefingBuilder(
    private val localRhythm: LocalRhythmCalendar,
    private val locale: Locale = Locale.getDefault(),
    private val countryNames: CountryNames = CountryNames.forLocale(locale),
    private val missionIndex: () -> DiplomaticMissionIndex = { DiplomaticMissionIndex.bundled },
) {
    /**
     * @param homeTimeZoneId the traveller's home time zone, for the "home is 2 h ahead" line.
     * @param now with [zone], decides what "today" and "tomorrow" mean for the holiday and rhythm sections.
     */
    fun build(
        event: ArrivalEvent,
        snapshot: DashboardSnapshot,
        settings: AppSettings,
        homeTimeZoneId: String?,
        now: Instant,
        zone: ZoneId,
    ): ArrivalBriefing {
        val code = event.countryCode
        val previous = event.previousCountryCode
        val travelContext = snapshot.travelContext

        val side = TravelReferenceData.drivingSide(code)
        val electricity = TravelReferenceData.electricity(code)
        val currency = TravelReferenceData.currencyCode(code)

        return ArrivalBriefing(
            countryCode = code,
            countryName = countryNames.name(code),
            time = timeSection(snapshot, homeTimeZoneId, now),
            emergency = EmergencyNumberDirectory.numbersFor(code),
            embassy = embassySection(code, settings.citizenshipCountryCodes, travelContext),
            road = side?.let {
                ArrivalBriefing.Road(it, changesFromPreviousCountry = TravelReferenceData.drivingSideChanged(previous, code))
            },
            power = electricity?.let {
                ArrivalBriefing.Power(it, differsFromPreviousCountry = TravelReferenceData.electricityChanged(previous, code))
            },
            money = currency?.let {
                ArrivalBriefing.Money(
                    currencyCode = it,
                    currencyName = currencyName(it),
                    changesFromPreviousCountry = TravelReferenceData.currencyChanged(previous, code),
                )
            },
            holiday = holidaySection(code, snapshot, now, zone),
            rhythm = localRhythm.periods(
                countryCode = code,
                places = listOf(
                    LocalRhythmPlace(travelContext.deviceCountryCode, travelContext.deviceRegion),
                    LocalRhythmPlace(travelContext.countryCode, travelContext.region),
                ),
                now = now,
                zone = zone,
                lookaheadDays = RHYTHM_LOOK_AHEAD_DAYS,
            ),
            advisory = advisorySection(code, snapshot),
        )
    }

    private fun timeSection(snapshot: DashboardSnapshot, homeTimeZoneId: String?, now: Instant): ArrivalBriefing.Time? {
        // The place's own zone first, then the device's: Android's snapshot has no zone for the device
        // coordinates themselves.
        val localZone = listOf(snapshot.travelContext.timeZoneId, snapshot.connectivity.timeZoneId)
            .firstNotNullOfOrNull(::zoneOrNull)
            ?: return null

        val localOffset = localZone.rules.getOffset(now).totalSeconds
        val home = homeTimeZoneId?.let(::zoneOrNull)?.let { homeZone ->
            ArrivalBriefing.Time.Home(
                timeZoneId = homeZone.id,
                cityName = TimeZoneCityName.of(homeZone.id),
                offsetFromLocalSeconds = homeZone.rules.getOffset(now).totalSeconds - localOffset,
            )
        }
        return ArrivalBriefing.Time(timeZoneId = localZone.id, utcOffsetSeconds = localOffset, home = home)
    }

    /** Nothing when the traveller is home (every citizenship is this country) or has no citizenship set. */
    private fun embassySection(
        countryCode: String,
        citizenships: List<String>,
        travelContext: TravelContextSnapshot,
    ): ArrivalBriefing.Embassy? {
        val abroad = citizenships
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != countryCode }
            .distinct()
        if (abroad.isEmpty()) return null

        val coordinate = travelContext.deviceLatitude?.let { latitude ->
            travelContext.deviceLongitude?.let { longitude -> MissionCoordinate(latitude, longitude) }
        }
        val lookup = EmbassyLookup.find(citizenships, countryCode, coordinate, index = missionIndex())

        val nearest = lookup.ownMatches.firstOrNull() ?: lookup.fallbackMatches.firstOrNull()
        val citizenship = lookup.ownMatches.firstOrNull()?.mission?.sendingCountry
            ?: abroad.firstOrNull { ConsularContactsDirectory.contactsFor(it) != null }
            ?: abroad.first()
        val contacts = ConsularContactsDirectory.contactsFor(citizenship)
        if (nearest == null && contacts == null) return null

        return ArrivalBriefing.Embassy(
            citizenshipCode = citizenship,
            nearestMission = nearest,
            fallbackReason = if (lookup.ownMatches.isEmpty()) lookup.fallbackReason else null,
            consularContacts = contacts,
        )
    }

    private fun holidaySection(
        countryCode: String,
        snapshot: DashboardSnapshot,
        now: Instant,
        zone: ZoneId,
    ): ArrivalBriefing.Holiday? {
        val localInfo = snapshot.localInfo
        if (localInfo.countryCode?.trim()?.uppercase() != countryCode) return null

        val period = localInfo.publicHoliday?.period ?: return null
        val today = now.atZone(zone).toLocalDate()
        val offset = daysUntil(period, today)?.takeIf { it <= 1 } ?: return null
        return ArrivalBriefing.Holiday(name = period.name, daysFromToday = offset)
    }

    /** Whole days from today to the period's start; 0 while it is on, null once it is over. */
    private fun daysUntil(period: HolidayPeriod, today: LocalDate): Int? {
        if (period.startDate <= today && today <= period.endDate) return 0
        return ChronoUnit.DAYS.between(today, period.startDate).toInt().takeIf { it > 0 }
    }

    private fun advisorySection(countryCode: String, snapshot: DashboardSnapshot): ArrivalBriefing.Advisory? {
        val alerts = snapshot.travelAlerts
        if (TravelAlertKind.ADVISORY !in alerts.enabledKinds) return null
        if (alerts.primaryCountryCode?.trim()?.uppercase() != countryCode) return null

        val state = alerts.state(TravelAlertKind.ADVISORY)?.takeIf { it.status == TravelAlertSignalStatus.READY } ?: return null
        val signal = state.signal?.takeIf { it.severity.rank >= TravelAlertSeverity.CAUTION.rank } ?: return null
        return ArrivalBriefing.Advisory(
            severity = signal.severity,
            summary = signal.summary.trim(),
            sourceName = signal.sourceName,
        )
    }

    private fun currencyName(code: String): String? =
        try {
            Currency.getInstance(code).getDisplayName(locale).trimmedNonEmpty()?.takeIf { it != code }
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun zoneOrNull(id: String?): ZoneId? =
        id.trimmedNonEmpty()?.let {
            try {
                ZoneId.of(it)
            } catch (_: DateTimeException) {
                null
            }
        }

    companion object {
        const val RHYTHM_LOOK_AHEAD_DAYS = 14

        /**
         * [settings] with the briefing for [countryCode] marked as seen, so it is not shown again until
         * the traveller arrives somewhere else. Unchanged when [countryCode] is not a two-letter code.
         */
        fun acknowledged(settings: AppSettings, countryCode: String, now: Instant): AppSettings {
            val code = AppSettings.normalizedCountryCode(countryCode) ?: return settings
            return settings.copy(arrivalBriefingAcknowledgedCountryCode = code, arrivalBriefingAcknowledgedAt = now)
        }
    }
}
