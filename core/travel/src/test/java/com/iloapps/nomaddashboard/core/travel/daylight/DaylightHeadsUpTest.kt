package com.iloapps.nomaddashboard.core.travel.daylight

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class DaylightHeadsUpTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val parisReference = DaylightReference(48.8566, 2.3522, paris)

    /** 2026-10-12 12:00 UTC, which is 14:00 in Paris. */
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val parisSun = parisReference.solarDay(now)
    private val tomorrowSun = parisReference.solarDay(parisReference.localDate(now).plusDays(1))

    private fun clock(instant: Instant) = ClockTimeFormatter.Hour24.format(instant.atZone(paris))

    private fun headsUp(at: Instant, reference: DaylightReference = parisReference) =
        DaylightHeadsUp.evaluate(reference, at)

    @Test
    fun `within an hour before sunset is a caution`() {
        val sunset = parisSun.sunset!!
        val dusk = parisSun.civilDusk!!
        val headsUp = headsUp(sunset.minus(Duration.ofMinutes(45)))!!

        assertThat(headsUp.kind).isEqualTo(DaylightHeadsUp.Kind.SUNSET_APPROACHING)
        assertThat(headsUp.decision.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(headsUp.decision.title).isEqualTo("Sunset ${clock(sunset)} · 45 min of daylight")
        assertThat(headsUp.decision.statusText).isEqualTo("Sunset ${clock(sunset)}")
        assertThat(headsUp.decision.detail).isEqualTo("Dusk (civil twilight) ends ${clock(dusk)}")

        val boundary = headsUp(sunset.minus(Duration.ofHours(1)))!!
        assertThat(boundary.decision.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(boundary.decision.title).endsWith("1 h of daylight")
    }

    @Test
    fun `within two hours before sunset is info with hours and minutes`() {
        val sunset = parisSun.sunset!!

        val headsUp = headsUp(sunset.minus(Duration.ofMinutes(110)))!!
        assertThat(headsUp.decision.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(headsUp.decision.title).isEqualTo("Sunset ${clock(sunset)} · 1 h 50 min of daylight")
        assertThat(headsUp.decision.statusText).isEqualTo("Sunset ${clock(sunset)}")

        val twoHours = headsUp(sunset.minus(Duration.ofHours(2)))!!
        assertThat(twoHours.decision.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(twoHours.decision.title).endsWith("2 h of daylight")

        assertThat(headsUp(sunset.minusSeconds(7_201))).isNull()
        assertThat(headsUp(sunset.minus(Duration.ofHours(6)))).isNull()
    }

    @Test
    fun `dark after civil dusk plus grace names tomorrow's sunrise`() {
        val dusk = parisSun.civilDusk!!
        val tomorrowSunrise = tomorrowSun.sunrise!!

        val headsUp = headsUp(dusk.plus(Duration.ofMinutes(30)))!!
        assertThat(headsUp.kind).isEqualTo(DaylightHeadsUp.Kind.DARK)
        assertThat(headsUp.decision.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(headsUp.decision.title).isEqualTo("Dark until ${clock(tomorrowSunrise)}")
        assertThat(headsUp.decision.statusText).isEqualTo("Dark")
        assertThat(headsUp.decision.detail).isEqualTo("Sunrise tomorrow ${clock(tomorrowSunrise)}")
    }

    @Test
    fun `between sunset and the dark grace produces nothing`() {
        val sunset = parisSun.sunset!!
        val dusk = parisSun.civilDusk!!

        assertThat(headsUp(sunset.plusSeconds(1))).isNull()
        assertThat(headsUp(dusk)).isNull()
        assertThat(headsUp(dusk.plusSeconds(30 * 60 - 1))).isNull()
    }

    @Test
    fun `stays dark after midnight until today's sunrise`() {
        val sunrise = parisSun.sunrise!!
        val afterMidnight = java.time.LocalDate.of(2026, 10, 12).atTime(1, 30).atZone(paris).toInstant()

        val headsUp = headsUp(afterMidnight)!!
        assertThat(headsUp.decision.title).isEqualTo("Dark until ${clock(sunrise)}")
        assertThat(headsUp.decision.detail).isEqualTo("Sunrise today ${clock(sunrise)}")

        assertThat(headsUp(sunrise.minusSeconds(60))?.kind).isEqualTo(DaylightHeadsUp.Kind.DARK)
        assertThat(headsUp(sunrise.plusSeconds(60))).isNull()
    }

    @Test
    fun `midday produces nothing`() {
        assertThat(headsUp(now)).isNull()
    }

    @Test
    fun `formats times with the injected formatter in the location's time zone`() {
        // Lisbon is an hour behind Paris; the formatter receives the time in the location's own zone.
        val lisbon = ZoneId.of("Europe/Lisbon")
        val reference = DaylightReference(38.7223, -9.1393, lisbon)
        val sunset = reference.solarDay(now).sunset!!
        val seen = mutableListOf<ZonedDateTime>()

        val headsUp = DaylightHeadsUp.evaluate(reference, sunset.minus(Duration.ofMinutes(30))) {
            seen += it
            "T"
        }!!

        assertThat(headsUp.decision.statusText).isEqualTo("Sunset T")
        assertThat(seen.map { it.zone }.toSet()).containsExactly(lisbon)
        assertThat(
            DaylightHeadsUp.evaluate(reference, sunset.minus(Duration.ofMinutes(30)))!!.decision.statusText,
        ).isEqualTo("Sunset ${ClockTimeFormatter.Hour24.format(sunset.atZone(lisbon))}")
    }

    @Test
    fun `is silent in polar day and polar night`() {
        val tromso = DaylightReference(69.6492, 18.9553, ZoneId.of("Europe/Oslo"))
        val midnightSun = Instant.ofEpochSecond(1_782_000_000) // 2026-06-21 00:00 UTC
        val polarNight = Instant.ofEpochSecond(1_797_800_000) // about 2026-12-20 21:00 UTC

        for (hours in 0 until 24 step 3) {
            val offset = Duration.ofHours(hours.toLong())
            assertThat(headsUp(midnightSun.plus(offset), tromso)).isNull()
            assertThat(headsUp(polarNight.plus(offset), tromso)).isNull()
        }
    }

    @Test
    fun `without civil dusk the detail is omitted and dark is never reported`() {
        // At 64 N near midsummer the sun sets but civil twilight never ends.
        val reference = DaylightReference(64.0, 25.0, ZoneId.of("Europe/Helsinki"))
        val june = Instant.parse("2026-06-21T12:00:00Z")
        val day = reference.solarDay(june)
        val sunset = day.sunset!!
        assertThat(day.civilDusk).isNull()

        val caution = headsUp(sunset.minus(Duration.ofMinutes(30)), reference)!!
        assertThat(caution.decision.detail).isNull()
        assertThat(headsUp(sunset.plus(Duration.ofHours(2)), reference)).isNull()
    }
}
