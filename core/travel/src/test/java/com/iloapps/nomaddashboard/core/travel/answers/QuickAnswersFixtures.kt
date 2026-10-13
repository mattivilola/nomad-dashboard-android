package com.iloapps.nomaddashboard.core.travel.answers

import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.InternetState
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.PowerSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpEngine
import com.iloapps.nomaddashboard.core.travel.rhythm.IcuLunisolarCalendars
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMission
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionIndex
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

// Builds dashboard snapshots for the quick-answer and widget tests, mirroring the macOS QuickAnswersFixtures.

internal object QuickAnswersFixtures {
    val locale: Locale = Locale.US

    val UTC: ZoneId = ZoneId.of("UTC")

    val helsinkiZone: ZoneId = ZoneId.of("Europe/Helsinki")
    val osloZone: ZoneId = ZoneId.of("Europe/Oslo")
    val casablancaZone: ZoneId = ZoneId.of("Africa/Casablanca")

    /** 2027-01-15 08:00 UTC, an arbitrary fixed clock. */
    val now: Instant = Instant.ofEpochSecond(1_800_000_000)

    /** The calendar day [now] falls on in [UTC]. */
    val utcToday: LocalDate = now.atZone(UTC).toLocalDate()

    /** 21 June 2026, 12:00 in Helsinki. */
    val helsinkiMidday: Instant = instant(2026, 6, 21, 12, 0, helsinkiZone)

    val engine = HeadsUpEngine(LocalRhythmCalendar(IcuLunisolarCalendars()))

    /** One recorded day in [code] for each of the [count] days up to and including [endingOn]. */
    fun consecutiveDays(count: Int, endingOn: LocalDate, code: String): List<VisitedCountryDay> =
        (0 until count).map { offset ->
            VisitedCountryDay(
                date = endingOn.minusDays(offset.toLong()),
                country = code,
                countryCode = code,
                source = VisitedPlaceSource.DEVICE_LOCATION,
                isInferred = false,
            )
        }

    fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    /** A location the way the dashboard knows it: a city, a country and optional coordinates. */
    data class Place(
        val city: String? = null,
        val country: String? = null,
        val countryCode: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val timeZoneId: String? = null,
    )

    fun place(
        city: String? = null,
        country: String? = null,
        countryCode: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        timeZoneId: String? = null,
    ) = Place(city, country, countryCode, latitude, longitude, timeZoneId)

    /** A healthy, online snapshot with the given device and IP places. */
    fun snapshot(
        device: Place? = null,
        ip: Place? = null,
        publicIp: String? = null,
        offline: Boolean = false,
        localInfo: LocalInfoSnapshot = LocalInfoSnapshot(),
    ): DashboardSnapshot = DashboardSnapshot(
        connectivity = if (offline) {
            ConnectivitySnapshot(internetState = InternetState.OFFLINE, isOnline = false)
        } else {
            ConnectivitySnapshot(internetState = InternetState.ONLINE, isOnline = true, latencyMs = 20.0, jitterMs = 2.0)
        },
        power = PowerSnapshot(batteryPercent = 90, statusLabel = "Discharging"),
        travelContext = TravelContextSnapshot(
            publicIp = publicIp,
            city = ip?.city,
            country = ip?.country,
            countryCode = ip?.countryCode,
            latitude = ip?.latitude,
            longitude = ip?.longitude,
            timeZoneId = ip?.timeZoneId,
            deviceCity = device?.city,
            deviceCountry = device?.country,
            deviceCountryCode = device?.countryCode,
            deviceLatitude = device?.latitude,
            deviceLongitude = device?.longitude,
        ),
        localInfo = localInfo,
    )

    fun morocco(): DashboardSnapshot = snapshot(
        device = place(city = "Rabat", country = "Morocco", countryCode = "MA", latitude = 34.02, longitude = -6.84),
    )

    fun helsinki(): DashboardSnapshot = snapshot(
        device = place(city = "Helsinki", country = "Finland", countryCode = "FI", latitude = 60.1699, longitude = 24.9384),
    )

    fun tromso(): DashboardSnapshot = snapshot(device = place(latitude = 69.65, longitude = 18.96))

    private fun mission(
        id: String,
        sending: String,
        host: String,
        name: String,
        city: String?,
        latitude: Double,
        longitude: Double,
        approximate: Boolean = true,
    ) = DiplomaticMission(
        id = id,
        sendingCountry = sending,
        hostCountry = host,
        kind = DiplomaticMissionKind.Embassy,
        name = name,
        city = city,
        latitude = latitude,
        longitude = longitude,
        isApproximateLocation = approximate,
    )

    val missionIndex = DiplomaticMissionIndex(
        listOf(
            mission("FI-RAB", "FI", "MA", "Embassy of Finland in Rabat", "Rabat", 34.05, -6.84, approximate = false),
            mission("SE-MAD", "SE", "ES", "Embassy of Sweden in Madrid", "Madrid", 40.4, -3.7),
            mission("SE-RAB", "SE", "MA", "Embassy of Sweden", "Rabat", 34.02, -6.84),
        ),
    )

    fun answers(locale: Locale = this.locale) = QuickAnswers(locale = locale, indexProvider = { missionIndex })
}
