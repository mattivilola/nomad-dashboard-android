package com.iloapps.nomaddashboard.core.travel.arrival

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.ConnectivitySnapshot
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.travel.reference.DrivingSide
import com.iloapps.nomaddashboard.core.travel.rhythm.IcuLunisolarCalendars
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmKind
import com.iloapps.nomaddashboard.core.travel.safety.ConsularContactsDirectory
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMission
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionIndex
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyLookup
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class ArrivalBriefingTest {
    // 2026-10-12 12:00 UTC, a Monday.
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    // 2026-06-15 12:00 UTC: Madrid is UTC+2, Casablanca UTC+1 and Helsinki UTC+3. Morocco's clock follows
    // the tz database (which moves it to UTC+0 in autumn 2026), so the time assertions that name
    // Morocco's offset pin a date on which every tz database agrees.
    private val summer = Instant.parse("2026-06-15T12:00:00Z")
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 12)

    private val missions = DiplomaticMissionIndex(
        missions = listOf(
            mission("FI-RABAT", sending = "FI", host = "MA", name = "Embassy of Finland in Rabat", latitude = 34.02, longitude = -6.84),
            mission("SE-PRETORIA", sending = "SE", host = "ZA", name = "Embassy of Sweden in Pretoria", latitude = -25.74, longitude = 28.19),
        ),
    )
    private val builder = ArrivalBriefingBuilder(
        localRhythm = LocalRhythmCalendar(IcuLunisolarCalendars()),
        locale = Locale.US,
        missionIndex = { missions },
    )

    private fun mission(id: String, sending: String, host: String, name: String, latitude: Double, longitude: Double) =
        DiplomaticMission(
            id = id,
            sendingCountry = sending,
            hostCountry = host,
            kind = DiplomaticMissionKind.Embassy,
            name = name,
            city = "Capital",
            latitude = latitude,
            longitude = longitude,
        )

    private fun event(country: String = "MA", previous: String = "ES") = ArrivalEvent(country, previous, today)

    private fun briefing(
        snapshot: DashboardSnapshot = moroccoSnapshot(),
        settings: AppSettings = AppSettings(),
        home: String? = null,
        at: Instant = now,
        event: ArrivalEvent = event(),
    ) = builder.build(event, snapshot, settings, home, at, zone)

    // region Spain to Morocco

    @Test
    fun `spain to morocco briefing`() {
        val briefing = briefing(settings = AppSettings(citizenshipCountryCodes = listOf("FI")), home = "Europe/Helsinki", at = summer)

        assertThat(briefing.countryCode).isEqualTo("MA")
        assertThat(briefing.countryName).isEqualTo("Morocco")
        assertThat(briefing.title).isEqualTo("Welcome to Morocco")

        val time = briefing.time!!
        assertThat(time.timeZoneId).isEqualTo("Africa/Casablanca")
        assertThat(time.utcOffsetSeconds).isEqualTo(3_600)
        assertThat(time.utcOffsetText).isEqualTo("UTC+1")
        assertThat(time.home?.cityName).isEqualTo("Helsinki")
        assertThat(time.home?.offsetFromLocalSeconds).isEqualTo(7_200)
        assertThat(time.home?.differenceText).isEqualTo("Home (Helsinki) is 2 h ahead")

        val emergency = briefing.emergency!!
        assertThat(emergency).isEqualTo(EmergencyNumberDirectory.numbersFor("MA"))
        assertThat(emergency.other).containsExactly(EmergencyService("Gendarmerie", "177"))

        // Spain and Morocco both drive on the right: nothing to switch.
        assertThat(briefing.road).isEqualTo(ArrivalBriefing.Road(DrivingSide.RIGHT, changesFromPreviousCountry = false))
        assertThat(briefing.road?.text).isEqualTo("Drive on the right")

        // C and F in Spain, C and E in Morocco.
        val power = briefing.power!!
        assertThat(power.electricity.plugTypes).containsExactly("C", "E").inOrder()
        assertThat(power.electricity.voltage).isEqualTo(220)
        assertThat(power.differsFromPreviousCountry).isTrue()

        val money = briefing.money!!
        assertThat(money.currencyCode).isEqualTo("MAD")
        assertThat(money.currencyName).isEqualTo("Moroccan Dirham")
        assertThat(money.changesFromPreviousCountry).isTrue()
        assertThat(briefing.rhythm).isEmpty()
    }

    @Test
    fun `sections describing changes are calm when nothing changed`() {
        // Portugal to Spain: same side, same currency, same plugs, same supply.
        val briefing = briefing(
            snapshot = snapshot(countryCode = "ES", timeZone = "Europe/Madrid"),
            event = event(country = "ES", previous = "PT"),
        )

        assertThat(briefing.road?.changesFromPreviousCountry).isFalse()
        assertThat(briefing.money?.changesFromPreviousCountry).isFalse()
        assertThat(briefing.power?.differsFromPreviousCountry).isFalse()
    }

    // endregion

    // region Time

    @Test
    fun `time compares with home ahead behind and same`() {
        fun home(id: String?) = briefing(home = id, at = summer).time!!

        assertThat(home("Europe/Helsinki").home?.differenceText).isEqualTo("Home (Helsinki) is 2 h ahead")
        assertThat(home("America/New_York").home?.differenceText).isEqualTo("Home (New York) is 5 h behind")
        assertThat(home("Asia/Kolkata").home?.differenceText).isEqualTo("Home (Kolkata) is 4 h 30 min ahead")
        assertThat(home("Africa/Casablanca").home?.differenceText).isEqualTo("Home (Casablanca) is on the same time")
        assertThat(home(null).home).isNull()
        assertThat(home("Not/AZone").home).isNull()
        assertThat(home(null).timeZoneId).isEqualTo("Africa/Casablanca")
    }

    @Test
    fun `difference text covers minutes only`() {
        val home = ArrivalBriefing.Time.Home("Asia/Kathmandu", "Kathmandu", offsetFromLocalSeconds = 45 * 60)

        assertThat(home.differenceText).isEqualTo("Home (Kathmandu) is 45 min ahead")
    }

    @Test
    fun `utc offset text covers fractional and negative offsets`() {
        fun text(seconds: Int) = ArrivalBriefing.Time("X", seconds, home = null).utcOffsetText

        assertThat(text(0)).isEqualTo("UTC+0")
        assertThat(text(3_600)).isEqualTo("UTC+1")
        assertThat(text(19_800)).isEqualTo("UTC+5:30")
        assertThat(text(-18_000)).isEqualTo("UTC-5")
        assertThat(text(-12_600)).isEqualTo("UTC-3:30")
    }

    @Test
    fun `time zone follows the place's own zone then the device zone`() {
        val place = snapshot(countryCode = "MA", timeZone = "Africa/Casablanca", deviceZone = "Europe/Madrid")
        assertThat(briefing(place).time?.timeZoneId).isEqualTo("Africa/Casablanca")

        // A location without a usable zone falls back to the device's.
        val noPlaceZone = snapshot(countryCode = "MA", timeZone = "Invalid/Zone", deviceZone = "Africa/Casablanca")
        assertThat(briefing(noPlaceZone).time?.timeZoneId).isEqualTo("Africa/Casablanca")
        val missingPlaceZone = snapshot(countryCode = "MA", timeZone = null, deviceZone = "Africa/Casablanca")
        assertThat(briefing(missingPlaceZone).time?.timeZoneId).isEqualTo("Africa/Casablanca")

        // Nothing usable: no time section.
        val none = snapshot(countryCode = "MA", timeZone = "Invalid/Zone", deviceZone = "Also/Invalid")
        assertThat(briefing(none).time).isNull()
    }

    // endregion

    // region Emergency

    @Test
    fun `emergency is omitted for countries without verified numbers`() {
        val briefing = briefing(
            snapshot = snapshot(countryCode = "LA", timeZone = "Asia/Vientiane"),
            event = event(country = "LA", previous = "TH"),
        )

        assertThat(briefing.emergency).isNull()
    }

    // endregion

    // region Embassy

    @Test
    fun `embassy is the nearest own mission with the hotline`() {
        val embassy = briefing(settings = AppSettings(citizenshipCountryCodes = listOf("FI"))).embassy!!

        assertThat(embassy.citizenshipCode).isEqualTo("FI")
        assertThat(embassy.fallbackReason).isNull()
        val mission = embassy.nearestMission!!
        assertThat(mission.mission.sendingCountry).isEqualTo("FI")
        assertThat(mission.mission.hostCountry).isEqualTo("MA")
        assertThat(mission.distanceKilometers).isNotNull()
        assertThat(embassy.consularContacts).isEqualTo(ConsularContactsDirectory.contactsFor("FI"))
        assertThat(embassy.consularContacts?.emergencyPhone).isEqualTo("+358 9 1605 5555")
    }

    @Test
    fun `embassy is omitted without citizenship or at home`() {
        assertThat(briefing(settings = AppSettings()).embassy).isNull()
        assertThat(briefing(settings = AppSettings(citizenshipCountryCodes = listOf("MA"))).embassy).isNull()
    }

    @Test
    fun `embassy keeps the hotline when no mission is known`() {
        val briefing = briefing(
            snapshot = snapshot(countryCode = "ZZ", timeZone = "UTC"),
            settings = AppSettings(citizenshipCountryCodes = listOf("FI")),
            event = event(country = "ZZ"),
        )

        val embassy = briefing.embassy!!
        assertThat(embassy.nearestMission).isNull()
        assertThat(embassy.consularContacts).isNotNull()
    }

    @Test
    fun `embassy falls back to a nordic mission when the own state has none`() {
        val briefing = briefing(
            snapshot = snapshot(countryCode = "ZA", timeZone = "Africa/Johannesburg"),
            settings = AppSettings(citizenshipCountryCodes = listOf("FI")),
            event = event(country = "ZA"),
        )

        val embassy = briefing.embassy!!
        assertThat(embassy.nearestMission?.mission?.sendingCountry).isEqualTo("SE")
        assertThat(embassy.fallbackReason).isEqualTo(EmbassyLookup.FallbackReason.NordicCooperation)
        assertThat(embassy.citizenshipCode).isEqualTo("FI")
    }

    @Test
    fun `a dual citizen at home for one passport still gets the other passport's help`() {
        val embassy = briefing(settings = AppSettings(citizenshipCountryCodes = listOf("MA", "FI"))).embassy!!

        assertThat(embassy.citizenshipCode).isEqualTo("FI")
    }

    // endregion

    // region Road, power and money

    @Test
    fun `driving side change is flagged`() {
        val briefing = briefing(
            snapshot = snapshot(countryCode = "GB", timeZone = "Europe/London"),
            event = event(country = "GB", previous = "FR"),
        )

        assertThat(briefing.road).isEqualTo(ArrivalBriefing.Road(DrivingSide.LEFT, changesFromPreviousCountry = true))
        assertThat(briefing.road?.text).isEqualTo("Drive on the left")
        assertThat(briefing.power?.electricity?.plugTypes).containsExactly("G")
        assertThat(briefing.power?.differsFromPreviousCountry).isTrue()
        assertThat(briefing.money?.currencyCode).isEqualTo("GBP")
        assertThat(briefing.money?.changesFromPreviousCountry).isTrue()
    }

    @Test
    fun `unknown previous country data never flags a change`() {
        val briefing = briefing(
            snapshot = snapshot(countryCode = "GB", timeZone = "Europe/London"),
            event = event(country = "GB", previous = "ZZ"),
        )

        assertThat(briefing.road?.changesFromPreviousCountry).isFalse()
        assertThat(briefing.power?.differsFromPreviousCountry).isFalse()
        assertThat(briefing.money?.changesFromPreviousCountry).isFalse()
    }

    @Test
    fun `sections without reference data are omitted`() {
        val briefing = briefing(
            snapshot = snapshot(countryCode = "JP", timeZone = "Asia/Tokyo"),
            event = event(country = "JP"),
        )

        assertThat(briefing.road?.side).isEqualTo(DrivingSide.LEFT)
        // 50 Hz east, 60 Hz west: not verifiable as one supply.
        assertThat(briefing.power).isNull()
        assertThat(briefing.money?.currencyCode).isEqualTo("JPY")
    }

    // endregion

    // region Holiday

    @Test
    fun `holiday today or tomorrow is included`() {
        val today = briefing(moroccoSnapshot(holiday = holiday(0, "Green March")))
        assertThat(today.holiday).isEqualTo(ArrivalBriefing.Holiday("Green March", 0))

        val tomorrow = briefing(moroccoSnapshot(holiday = holiday(1, "Independence Day")))
        assertThat(tomorrow.holiday).isEqualTo(ArrivalBriefing.Holiday("Independence Day", 1))
    }

    @Test
    fun `a holiday that is on counts as today`() {
        val running = HolidayPeriod("Eid", this.today.minusDays(1), this.today.plusDays(1))

        assertThat(briefing(moroccoSnapshot(holiday = running)).holiday).isEqualTo(ArrivalBriefing.Holiday("Eid", 0))
    }

    @Test
    fun `holidays further out or from another country are omitted`() {
        assertThat(briefing(moroccoSnapshot(holiday = holiday(2))).holiday).isNull()
        assertThat(briefing(moroccoSnapshot(holiday = holiday(-1))).holiday).isNull()
        assertThat(briefing(moroccoSnapshot(holiday = null)).holiday).isNull()

        // Local info that still belongs to Spain must not leak into the Morocco briefing.
        val stale = snapshot(
            countryCode = "MA",
            timeZone = "Africa/Casablanca",
            localInfo = localInfo("ES", holiday(0, "Fiesta")),
        )
        assertThat(briefing(stale).holiday).isNull()
    }

    @Test
    fun `holiday uses the injected zone's calendar day`() {
        // 23:00 UTC on the eve of a holiday that starts on the 12th: tomorrow in UTC, but already the
        // holiday itself in Auckland (UTC+13 in October).
        val snapshot = moroccoSnapshot(holiday = holiday(0, "Green March"))
        val evening = now.minusSeconds(13 * 3_600)

        fun daysFromToday(zone: ZoneId) =
            builder.build(event(), snapshot, AppSettings(), null, evening, zone).holiday?.daysFromToday

        assertThat(daysFromToday(zone)).isEqualTo(1)
        assertThat(daysFromToday(ZoneId.of("Pacific/Auckland"))).isEqualTo(0)
    }

    // endregion

    // region Local rhythm

    // Reference (2027): Ramadan 8 Feb - 8 Mar, Eid al-Fitr 9-11 Mar, Nyepi 8 Mar.

    private fun rhythmBriefing(
        country: String,
        year: Int,
        month: Int,
        day: Int,
        snapshot: DashboardSnapshot = snapshot(countryCode = country, timeZone = "UTC"),
    ): ArrivalBriefing =
        builder.build(
            event = event(country = country, previous = "FR"),
            snapshot = snapshot,
            settings = AppSettings(),
            homeTimeZoneId = null,
            now = LocalDate.of(year, month, day).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC),
            zone = zone,
        )

    private fun rhythmKinds(
        country: String,
        year: Int,
        month: Int,
        day: Int,
        snapshot: DashboardSnapshot = snapshot(countryCode = country, timeZone = "UTC"),
    ) = rhythmBriefing(country, year, month, day, snapshot).rhythm.map { it.kind }

    @Test
    fun `rhythm lists the periods on now or starting within fourteen days`() {
        val during = rhythmBriefing("MA", 2027, 2, 19).rhythm.first()
        assertThat(during.kind).isEqualTo(LocalRhythmKind.RAMADAN)
        assertThat(during.countryCode).isEqualTo("MA")

        assertThat(rhythmKinds("MA", 2027, 1, 25)).containsExactly(LocalRhythmKind.RAMADAN) // starts in 14 days
        assertThat(rhythmKinds("MA", 2027, 1, 24)).isEmpty() // 15 days
        assertThat(rhythmKinds("MA", 2026, 10, 12)).isEmpty()
        assertThat(rhythmKinds("ES", 2027, 2, 19)).isEmpty()
    }

    @Test
    fun `rhythm is earliest first and follows the arrival country not the snapshot`() {
        // The snapshot still describes Morocco while the traveller has just arrived in Saudi Arabia.
        val lagging = rhythmBriefing("SA", 2027, 3, 7, snapshot = moroccoSnapshot())

        assertThat(lagging.rhythm.map { it.kind })
            .containsExactly(LocalRhythmKind.RAMADAN, LocalRhythmKind.EID_AL_FITR).inOrder()
        assertThat(lagging.rhythm.all { it.countryCode == "SA" }).isTrue()
    }

    @Test
    fun `Nyepi is only listed for Bali or an unknown region`() {
        fun kinds(context: TravelContextSnapshot) =
            rhythmKinds("ID", 2027, 3, 5, snapshot = DashboardSnapshot(travelContext = context))
        fun device(region: String?) = TravelContextSnapshot(deviceCountryCode = "ID", deviceRegion = region)
        fun assertKinds(context: TravelContextSnapshot, expected: List<LocalRhythmKind>) =
            assertThat(kinds(context)).containsExactlyElementsIn(expected).inOrder()
        val withNyepi = listOf(LocalRhythmKind.RAMADAN, LocalRhythmKind.NYEPI, LocalRhythmKind.EID_AL_FITR)
        val withoutNyepi = listOf(LocalRhythmKind.RAMADAN, LocalRhythmKind.EID_AL_FITR)

        assertKinds(device("Bali"), withNyepi)
        assertKinds(device("Jakarta"), withoutNyepi)
        // Unknown region: kept, as its name already says "Bali".
        assertKinds(device(null), withNyepi)
        assertKinds(TravelContextSnapshot(), withNyepi)
        // A region of the previous country (a refresh that lags behind the crossing) says nothing about Indonesia.
        assertKinds(TravelContextSnapshot(deviceCountryCode = "ES", deviceRegion = "Andalusia"), withNyepi)
    }

    // endregion

    // region Advisory

    @Test
    fun `advisory at caution or worse is included`() {
        val caution = briefing(moroccoSnapshot(alerts = alerts(TravelAlertSeverity.CAUTION, "MA"))).advisory!!
        assertThat(caution.severity).isEqualTo(TravelAlertSeverity.CAUTION)
        assertThat(caution.summary).isEqualTo("Exercise a high degree of caution.")
        assertThat(caution.sourceName).isEqualTo("Smartraveller")

        val critical = briefing(moroccoSnapshot(alerts = alerts(TravelAlertSeverity.CRITICAL, "MA"))).advisory
        assertThat(critical?.severity).isEqualTo(TravelAlertSeverity.CRITICAL)
    }

    @Test
    fun `advisory below caution not ready or for another country is omitted`() {
        listOf(TravelAlertSeverity.CLEAR, TravelAlertSeverity.INFO).forEach { severity ->
            assertThat(briefing(moroccoSnapshot(alerts = alerts(severity, "MA"))).advisory).isNull()
        }
        listOf(TravelAlertSignalStatus.CHECKING, TravelAlertSignalStatus.STALE, TravelAlertSignalStatus.UNAVAILABLE).forEach { status ->
            assertThat(briefing(moroccoSnapshot(alerts = alerts(TravelAlertSeverity.WARNING, "MA", status = status))).advisory).isNull()
        }
        // Alerts that are still about Spain.
        assertThat(briefing(moroccoSnapshot(alerts = alerts(TravelAlertSeverity.WARNING, "ES"))).advisory).isNull()
        // Advisories switched off.
        val weatherOnly = alerts(TravelAlertSeverity.WARNING, "MA", enabledKinds = listOf(TravelAlertKind.SECURITY))
        assertThat(briefing(moroccoSnapshot(alerts = weatherOnly)).advisory).isNull()
    }

    // endregion

    // region Fixtures

    private fun holiday(dayOffset: Int, name: String = "Holiday"): HolidayPeriod {
        val start = today.plusDays(dayOffset.toLong())
        return HolidayPeriod(name, start, start)
    }

    private fun localInfo(countryCode: String, holiday: HolidayPeriod) = LocalInfoSnapshot(
        status = LocalInfoStatus.READY,
        countryCode = countryCode,
        publicHoliday = LocalHolidayStatus(LocalHolidayPhase.NEXT, holiday),
        detail = null,
    )

    private fun alerts(
        severity: TravelAlertSeverity,
        countryCode: String,
        status: TravelAlertSignalStatus = TravelAlertSignalStatus.READY,
        enabledKinds: List<TravelAlertKind> = listOf(TravelAlertKind.ADVISORY),
    ) = TravelAlertsSnapshot(
        enabledKinds = enabledKinds,
        primaryCountryCode = countryCode,
        coverageCountryCodes = listOf(countryCode),
        states = listOf(
            TravelAlertSignalState(
                kind = TravelAlertKind.ADVISORY,
                status = status,
                signal = TravelAlertSignalSnapshot(
                    kind = TravelAlertKind.ADVISORY,
                    severity = severity,
                    title = "Travel advisory",
                    summary = " Exercise a high degree of caution. ",
                    sourceName = "Smartraveller",
                    updatedAt = now,
                    affectedCountryCodes = listOf(countryCode),
                ),
                sourceName = "Smartraveller",
            ),
        ),
    )

    private fun moroccoSnapshot(holiday: HolidayPeriod? = null, alerts: TravelAlertsSnapshot? = null) =
        snapshot(
            countryCode = "MA",
            timeZone = "Africa/Casablanca",
            deviceLatitude = 33.5731,
            deviceLongitude = -7.5898,
            localInfo = holiday?.let { localInfo("MA", it) },
            alerts = alerts,
        )

    private fun snapshot(
        countryCode: String,
        timeZone: String?,
        deviceZone: String = "UTC",
        deviceLatitude: Double? = null,
        deviceLongitude: Double? = null,
        localInfo: LocalInfoSnapshot? = null,
        alerts: TravelAlertsSnapshot? = null,
    ) = DashboardSnapshot(
        connectivity = ConnectivitySnapshot(timeZoneId = deviceZone),
        travelContext = TravelContextSnapshot(
            countryCode = countryCode,
            timeZoneId = timeZone,
            deviceLatitude = deviceLatitude,
            deviceLongitude = deviceLongitude,
        ),
        localInfo = localInfo ?: LocalInfoSnapshot(),
        travelAlerts = alerts ?: TravelAlertsSnapshot(),
    )

    // endregion
}
