package com.iloapps.nomaddashboard.core.travel.daylight

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class DaylightLinePresentationTest {
    private val noon = Instant.parse("2026-10-12T12:00:00Z")

    @Test
    fun `shows sunrise and sunset in the location's time zone`() {
        val zone = ZoneId.of("Europe/Paris")
        val reference = DaylightReference(48.8566, 2.3522, zone)
        val presentation = DaylightLinePresentation.of(reference, noon)!!
        val day = reference.solarDay(noon)
        val sunrise = ClockTimeFormatter.Hour24.format(day.sunrise!!.atZone(zone))
        val sunset = ClockTimeFormatter.Hour24.format(day.sunset!!.atZone(zone))

        assertThat(presentation.segments.map { it.kind }).containsExactly(
            DaylightLinePresentation.SegmentKind.SUNRISE,
            DaylightLinePresentation.SegmentKind.SUNSET,
        ).inOrder()
        assertThat(presentation.segments.map { it.text })
            .containsExactly("Sunrise $sunrise", "Sunset $sunset").inOrder()
        assertThat(presentation.summary).isEqualTo("Sunrise $sunrise · Sunset $sunset")
        // Paris in mid October: sunrise around 08:00, sunset around 19:00 local time.
        assertThat(sunrise).startsWith("08:")
        assertThat(sunset).startsWith("19:")
    }

    @Test
    fun `uses the injected formatter`() {
        val reference = DaylightReference(48.8566, 2.3522, ZoneId.of("Europe/Paris"))
        val presentation = DaylightLinePresentation.of(reference, noon) { "T" }!!

        assertThat(presentation.summary).isEqualTo("Sunrise T · Sunset T")
    }

    @Test
    fun `the localized formatter follows the locale's short time style`() {
        val zone = ZoneId.of("Europe/Paris")
        val time = Instant.parse("2026-10-12T16:12:00Z").atZone(zone)

        assertThat(ClockTimeFormatter.localized(Locale.UK).format(time)).isEqualTo("18:12")
        assertThat(ClockTimeFormatter.localized(Locale.US).format(time).replace(' ', ' ')).isEqualTo("6:12 PM")
    }

    @Test
    fun `says midnight sun and polar night`() {
        val tromso = DaylightReference(69.6492, 18.9553, ZoneId.of("Europe/Oslo"))
        val june = Instant.parse("2026-06-21T12:00:00Z")
        val december = Instant.parse("2026-12-21T12:00:00Z")

        val midnightSun = DaylightLinePresentation.of(tromso, june)!!
        assertThat(midnightSun.segments).containsExactly(
            DaylightLinePresentation.Segment(DaylightLinePresentation.SegmentKind.MIDNIGHT_SUN, "Midnight sun"),
        )

        val polarNight = DaylightLinePresentation.of(tromso, december)!!
        assertThat(polarNight.segments).containsExactly(
            DaylightLinePresentation.Segment(DaylightLinePresentation.SegmentKind.POLAR_NIGHT, "Polar night"),
        )
        assertThat(polarNight.summary).isEqualTo("Polar night")
    }
}

class DaylightOutlookTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val outlook = DaylightOutlook(
        sunrise = at(1_000),
        sunset = at(5_000),
        nextSunrise = at(9_000),
        isPolarDay = false,
        isPolarNight = false,
        zone = helsinki,
    )

    private fun at(seconds: Long): Instant = Instant.ofEpochSecond(seconds)

    @Test
    fun `phases follow the day`() {
        assertThat(outlook.phaseAt(at(0))).isEqualTo(DaylightOutlook.Phase.Dark(at(1_000)))
        assertThat(outlook.phaseAt(at(1_000))).isEqualTo(DaylightOutlook.Phase.Daylight(at(5_000)))
        assertThat(outlook.phaseAt(at(4_999))).isEqualTo(DaylightOutlook.Phase.Daylight(at(5_000)))
        assertThat(outlook.phaseAt(at(5_000))).isEqualTo(DaylightOutlook.Phase.Dark(at(9_000)))
        assertThat(outlook.phaseAt(at(8_999))).isEqualTo(DaylightOutlook.Phase.Dark(at(9_000)))
    }

    @Test
    fun `past the next sunrise the outlook has expired`() {
        assertThat(outlook.phaseAt(at(9_000))).isNull()
    }

    @Test
    fun `without a next sunrise it stays dark after sunset`() {
        val open = outlook.copy(nextSunrise = null)

        assertThat(open.phaseAt(at(50_000))).isEqualTo(DaylightOutlook.Phase.Dark(null))
    }

    @Test
    fun `polar days and nights ignore the clock`() {
        val day = DaylightOutlook(null, null, null, isPolarDay = true, isPolarNight = false, zone = ZoneId.of("UTC"))
        val night = DaylightOutlook(null, null, null, isPolarDay = false, isPolarNight = true, zone = ZoneId.of("UTC"))

        assertThat(day.phaseAt(at(0))).isEqualTo(DaylightOutlook.Phase.MidnightSun)
        assertThat(night.phaseAt(at(0))).isEqualTo(DaylightOutlook.Phase.PolarNight)
    }

    @Test
    fun `transitions are sorted and skip missing events`() {
        assertThat(outlook.transitions).containsExactly(at(1_000), at(5_000), at(9_000)).inOrder()
        val polar = DaylightOutlook(null, null, null, isPolarDay = true, isPolarNight = false, zone = ZoneId.of("UTC"))
        assertThat(polar.transitions).isEmpty()
    }

    @Test
    fun `clock times use the place's time zone`() {
        val utc = outlook.copy(zone = ZoneId.of("UTC"))

        // Helsinki is UTC+2 on 1 Jan 1970.
        assertThat(outlook.clockTime(at(0))).isEqualTo("02:00")
        assertThat(utc.clockTime(at(0))).isEqualTo("00:00")
    }

    @Test
    fun `daylight left is only given in daylight`() {
        assertThat(outlook.daylightLeft(at(4_000))?.seconds).isEqualTo(1_000L)
        assertThat(outlook.daylightLeft(at(0))).isNull()
        assertThat(outlook.daylightLeft(at(6_000))).isNull()
    }

    @Test
    fun `same day is judged in the place's time zone`() {
        // 22:30 UTC on 2 Jan 1970 is already 3 Jan in Helsinki.
        val lateUtc = Instant.parse("1970-01-02T22:30:00Z")
        val earlyHelsinki = Instant.parse("1970-01-03T00:30:00Z")

        assertThat(outlook.isSameDay(lateUtc, earlyHelsinki)).isTrue()
        assertThat(outlook.copy(zone = ZoneId.of("UTC")).isSameDay(lateUtc, earlyHelsinki)).isFalse()
    }

    @Test
    fun `computed from a location it covers today and tomorrow's sunrise`() {
        val reference = DaylightReference(60.1699, 24.9384, helsinki)
        val now = Instant.parse("2026-06-21T09:00:00Z")
        val computed = DaylightOutlook.of(reference, now)
        val today = reference.solarDay(now)
        val tomorrow = reference.solarDay(reference.localDate(now).plusDays(1))

        assertThat(computed.sunrise).isEqualTo(today.sunrise)
        assertThat(computed.sunset).isEqualTo(today.sunset)
        assertThat(computed.nextSunrise).isEqualTo(tomorrow.sunrise)
        assertThat(computed.nextSunrise!!).isGreaterThan(computed.sunset!!)
        assertThat(computed.zone).isEqualTo(helsinki)
    }
}
