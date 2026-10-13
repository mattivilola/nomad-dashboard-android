package com.iloapps.nomaddashboard.core.travel.arrival

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingPresentation.Kind
import com.iloapps.nomaddashboard.core.travel.arrival.ArrivalBriefingPresentation.Tone
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.reference.CountryElectricity
import com.iloapps.nomaddashboard.core.travel.reference.DrivingSide
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmDateConfidence
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmKind
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPeriod
import com.iloapps.nomaddashboard.core.travel.safety.ConsularContacts
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMission
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionKind
import com.iloapps.nomaddashboard.core.travel.safety.DiplomaticMissionMatch
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyLookup
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumbers
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyService
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class ArrivalBriefingPresentationTest {
    // 2026-10-12 12:00 UTC.
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val zone = ZoneId.of("UTC")
    private val formats = HeadsUpFormats(locale = Locale.UK, clockTime = ClockTimeFormatter.Hour24)

    private val morocco = EmergencyNumbers(
        general = "19",
        ambulance = "15",
        fire = "15",
        other = listOf(EmergencyService("Gendarmerie", "177")),
    )

    private fun briefing(
        code: String = "MA",
        name: String = "Morocco",
        time: ArrivalBriefing.Time? = null,
        emergency: EmergencyNumbers? = null,
        embassy: ArrivalBriefing.Embassy? = null,
        road: ArrivalBriefing.Road? = null,
        power: ArrivalBriefing.Power? = null,
        money: ArrivalBriefing.Money? = null,
        holiday: ArrivalBriefing.Holiday? = null,
        rhythm: List<LocalRhythmPeriod> = emptyList(),
        advisory: ArrivalBriefing.Advisory? = null,
    ) = ArrivalBriefing(code, name, time, emergency, embassy, road, power, money, holiday, rhythm, advisory)

    private fun presentation(briefing: ArrivalBriefing, previous: String? = "ES") =
        ArrivalBriefingPresentation.of(briefing, previous, now, zone, formats)

    private fun ArrivalBriefingPresentation.row(kind: Kind) = rows.firstOrNull { it.kind == kind }

    @Test
    fun `title and subtitle name the country and where the traveller came from`() {
        assertThat(presentation(briefing()).title).isEqualTo("Welcome to Morocco")
        assertThat(presentation(briefing()).countryCode).isEqualTo("MA")
        assertThat(presentation(briefing()).subtitle).isEqualTo("Arrived from Spain")
        assertThat(presentation(briefing(), previous = "MA").subtitle).isNull()
        assertThat(presentation(briefing(), previous = null).subtitle).isNull()
    }

    @Test
    fun `an empty briefing has no rows`() {
        val empty = presentation(briefing())

        assertThat(empty.rows).isEmpty()
        assertThat(empty.collapsedSummary).isEmpty()
    }

    @Test
    fun `time shows the local clock the offset and home`() {
        val time = ArrivalBriefing.Time(
            timeZoneId = "UTC",
            utcOffsetSeconds = 0,
            home = ArrivalBriefing.Time.Home("Europe/Helsinki", "Helsinki", offsetFromLocalSeconds = 7_200),
        )

        assertThat(presentation(briefing(time = time)).row(Kind.TIME)!!.text)
            .isEqualTo("Local 12:00 (UTC+0) · Home (Helsinki) is 2 h ahead")
    }

    @Test
    fun `time without a usable zone still shows the offset`() {
        val time = ArrivalBriefing.Time(timeZoneId = "Not/AZone", utcOffsetSeconds = 3_600, home = null)

        assertThat(presentation(briefing(time = time)).row(Kind.TIME)!!.text).isEqualTo("Local time (UTC+1)")
    }

    @Test
    fun `emergency numbers are selectable and list the others`() {
        val row = presentation(briefing(emergency = morocco)).row(Kind.EMERGENCY)!!

        assertThat(row.text).isEqualTo("19")
        assertThat(row.detail).isEqualTo(morocco.othersText)
        assertThat(row.isSelectable).isTrue()
    }

    @Test
    fun `driving side is sand only when it changes`() {
        fun road(changes: Boolean) =
            presentation(briefing("GB", "United Kingdom", road = ArrivalBriefing.Road(DrivingSide.LEFT, changes))).row(Kind.ROAD)!!

        assertThat(road(true).text).isEqualTo("Drive on the left")
        assertThat(road(true).tone).isEqualTo(Tone.CAUTION)
        assertThat(road(true).flag).isEqualTo("Changed")
        assertThat(road(false).tone).isEqualTo(Tone.NEUTRAL)
        assertThat(road(false).flag).isNull()
    }

    @Test
    fun `power and currency carry a change flag`() {
        val result = presentation(
            briefing(
                power = ArrivalBriefing.Power(CountryElectricity(listOf("C", "E"), voltage = 220, frequencyHertz = 50), differsFromPreviousCountry = true),
                money = ArrivalBriefing.Money("MAD", "Moroccan Dirham", changesFromPreviousCountry = true),
            ),
        )

        assertThat(result.row(Kind.POWER)!!.text).isEqualTo("Plug C, E · 220 V · 50 Hz")
        assertThat(result.row(Kind.POWER)!!.flag).isEqualTo("Changed")
        assertThat(result.row(Kind.MONEY)!!.text).isEqualTo("Moroccan Dirham (MAD)")
        assertThat(result.row(Kind.MONEY)!!.flag).isEqualTo("Changed")
    }

    @Test
    fun `a known currency without a name shows the code`() {
        val money = presentation(briefing(money = ArrivalBriefing.Money("MAD", null, changesFromPreviousCountry = false))).row(Kind.MONEY)!!

        assertThat(money.text).isEqualTo("MAD")
        assertThat(money.flag).isNull()
    }

    @Test
    fun `holiday and advisory rows appear when present`() {
        val result = presentation(
            briefing(
                holiday = ArrivalBriefing.Holiday("Throne Day", daysFromToday = 1),
                advisory = ArrivalBriefing.Advisory(TravelAlertSeverity.WARNING, "Exercise a high degree of caution.", "Smartraveller"),
            ),
        )

        assertThat(result.row(Kind.HOLIDAY)!!.text).isEqualTo("Throne Day tomorrow")
        assertThat(result.row(Kind.HOLIDAY)!!.detail).isEqualTo("Shops and offices may be closed")
        val advisory = result.row(Kind.ADVISORY)!!
        assertThat(advisory.tone).isEqualTo(Tone.ATTENTION)
        assertThat(advisory.detail).isEqualTo("Smartraveller")
    }

    @Test
    fun `a caution advisory is sand and today's holiday says today`() {
        val result = presentation(
            briefing(
                holiday = ArrivalBriefing.Holiday("Throne Day", daysFromToday = 0),
                advisory = ArrivalBriefing.Advisory(TravelAlertSeverity.CAUTION, "Be careful.", "FCDO"),
            ),
        )

        assertThat(result.row(Kind.HOLIDAY)!!.text).isEqualTo("Throne Day today")
        assertThat(result.row(Kind.ADVISORY)!!.tone).isEqualTo(Tone.CAUTION)
    }

    @Test
    fun `rhythm periods show name dates and guidance`() {
        val ramadan = LocalRhythmPeriod(
            kind = LocalRhythmKind.RAMADAN,
            name = "Ramadan",
            countryCode = "MA",
            start = Instant.parse("2026-10-12T00:00:00Z"),
            end = Instant.parse("2026-11-10T23:59:59Z"),
            confidence = LocalRhythmDateConfidence.EXPECTED,
            guidance = "Shops keep shorter hours.",
        )
        val rhythm = presentation(briefing(rhythm = listOf(ramadan))).row(Kind.RHYTHM)!!

        assertThat(rhythm.title).isEqualTo("Ramadan")
        assertThat(rhythm.text).isEqualTo("12 Oct – 10 Nov (expected; may shift a day)")
        assertThat(rhythm.detail).isEqualTo("Shops keep shorter hours.")
        assertThat(rhythm.flag).isEqualTo("Now")
    }

    private val rabat = DiplomaticMission(
        id = "Q1",
        sendingCountry = "FI",
        hostCountry = "MA",
        kind = DiplomaticMissionKind.Embassy,
        name = "Embassy of Finland, Rabat",
        city = "Rabat",
        latitude = 34.0,
        longitude = -6.8,
    )
    private val finnishLine = ConsularContacts(
        ministryName = "Ministry for Foreign Affairs of Finland",
        emergencyPhone = "+358 9 1605 5555",
    )

    @Test
    fun `the embassy row leads with the mission and keeps the hotline selectable`() {
        val embassy = ArrivalBriefing.Embassy("FI", DiplomaticMissionMatch(rabat, 3.0), null, finnishLine)
        val row = presentation(briefing(embassy = embassy)).row(Kind.EMBASSY)!!

        assertThat(row.text).isEqualTo("Embassy of Finland, Rabat")
        assertThat(row.detail).isEqualTo("Ministry for Foreign Affairs +358 9 1605 5555")
        assertThat(row.isSelectable).isTrue()
    }

    @Test
    fun `the embassy row adds the city when the name lacks it and explains a fallback`() {
        val nameOnly = rabat.copy(name = "Embassy of Sweden", sendingCountry = "SE")
        val embassy = ArrivalBriefing.Embassy(
            citizenshipCode = "LV",
            nearestMission = DiplomaticMissionMatch(nameOnly, null),
            fallbackReason = EmbassyLookup.FallbackReason.EuConsularProtection,
            consularContacts = null,
        )
        val row = presentation(briefing(embassy = embassy)).row(Kind.EMBASSY)!!

        assertThat(row.text).isEqualTo("Embassy of Sweden · Rabat")
        assertThat(row.detail).isEqualTo("Under EU consular protection")
    }

    @Test
    fun `the embassy row falls back to the hotline alone and is omitted without anything`() {
        val hotlineOnly = ArrivalBriefing.Embassy("FI", null, null, finnishLine)
        assertThat(presentation(briefing(embassy = hotlineOnly)).row(Kind.EMBASSY)!!.text)
            .isEqualTo("Ministry for Foreign Affairs +358 9 1605 5555")
        assertThat(presentation(briefing(embassy = hotlineOnly)).row(Kind.EMBASSY)!!.detail).isNull()

        val nothing = ArrivalBriefing.Embassy("FI", null, null, null)
        assertThat(presentation(briefing(embassy = nothing)).row(Kind.EMBASSY)).isNull()
    }

    @Test
    fun `rows appear in reading order`() {
        val result = presentation(
            briefing(
                time = ArrivalBriefing.Time("UTC", 0, null),
                emergency = morocco,
                road = ArrivalBriefing.Road(DrivingSide.RIGHT, false),
                money = ArrivalBriefing.Money("MAD", null, false),
            ),
        )

        assertThat(result.rows.map { it.id }).containsExactly("time", "emergency", "road", "money").inOrder()
    }

    @Test
    fun `the collapsed line has the emergency number and a changed driving side`() {
        val result = presentation(
            briefing(
                "GB",
                "United Kingdom",
                emergency = EmergencyNumbers(general = "999"),
                road = ArrivalBriefing.Road(DrivingSide.LEFT, changesFromPreviousCountry = true),
            ),
        )

        assertThat(result.collapsedSummary).isEqualTo("Emergency 999 · Drive on the left")
    }

    @Test
    fun `an unchanged driving side stays out of the collapsed line`() {
        val result = presentation(
            briefing(emergency = EmergencyNumbers(general = "112"), road = ArrivalBriefing.Road(DrivingSide.RIGHT, false)),
        )

        assertThat(result.collapsedSummary).isEqualTo("Emergency 112")
    }
}
