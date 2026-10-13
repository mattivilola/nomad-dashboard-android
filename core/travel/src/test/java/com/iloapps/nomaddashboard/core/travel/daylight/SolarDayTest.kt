package com.iloapps.nomaddashboard.core.travel.daylight

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * Reference values come from the U.S. Naval Observatory Astronomical Applications API
 * (`https://aa.usno.navy.mil/api/rstt/oneday?date=<date>&coords=<lat>,<lon>&tz=<offset>&dst=false`,
 * "Rise", "Set", "Begin Civil Twilight", "End Civil Twilight"), retrieved 2026-10-12. The Helsinki
 * sunrise and sunset were cross-checked against the MET Norway sunrise API. All reference times are
 * local clock times, to the minute; tests allow +/- 2 minutes.
 */
class SolarDayTest {
    // region Helsinki (60.1699 N, 24.9384 E)

    @Test
    fun `helsinki midsummer`() {
        // USNO 2026-06-21, tz +3: Begin Civil 02:02, Rise 03:54, Set 22:50, End Civil 00:42 (the night after).
        val zone = ZoneId.of("Europe/Helsinki")
        val day = solarDay(60.1699, 24.9384, zone, 2026, 6, 21)
        assertTime(day.civilDawn, "02:02", LocalDate.of(2026, 6, 21), zone)
        assertTime(day.sunrise, "03:54", LocalDate.of(2026, 6, 21), zone)
        assertTime(day.sunset, "22:50", LocalDate.of(2026, 6, 21), zone)
        assertTime(day.civilDusk, "00:42", LocalDate.of(2026, 6, 22), zone)
        assertThat(day.isPolarDay).isFalse()
        assertThat(day.isPolarNight).isFalse()
    }

    @Test
    fun `helsinki midwinter`() {
        // USNO 2026-12-21, tz +2: Begin Civil 08:25, Rise 09:24, Set 15:13, End Civil 16:11.
        val zone = ZoneId.of("Europe/Helsinki")
        val day = solarDay(60.1699, 24.9384, zone, 2026, 12, 21)
        val date = LocalDate.of(2026, 12, 21)
        assertTime(day.civilDawn, "08:25", date, zone)
        assertTime(day.sunrise, "09:24", date, zone)
        assertTime(day.sunset, "15:13", date, zone)
        assertTime(day.civilDusk, "16:11", date, zone)
    }

    // endregion

    // region Morocco

    // Morocco returned to GMT (UTC+0, no daylight saving) on 20 Sep 2026 (Decree No. 2.26.530), so
    // Africa/Casablanca is UTC+0 on 15 Jan 2027. The JDK 21 time-zone database shipped with the build
    // JDK predates that change and still reports +01:00, so these tests use the fixed UTC+0 offset the
    // zone has from the change onwards; switch to ZoneId.of("Africa/Casablanca") once the build JDK's
    // tzdata includes the 2026 decree. The USNO queries below used tz +1 (the zone before the change);
    // the reference times here are those results minus one hour, i.e. the same UTC instants.
    private val morocco: ZoneId = ZoneOffset.UTC

    @Test
    fun `rabat in january`() {
        // USNO 2027-01-15, tz +1 (33.9716 N, 6.8498 W): Begin Civil 08:05, Rise 08:33, Set 18:41, End Civil 19:08.
        val day = solarDay(33.9716, -6.8498, morocco, 2027, 1, 15)
        val date = LocalDate.of(2027, 1, 15)
        assertTime(day.civilDawn, "07:05", date, morocco)
        assertTime(day.sunrise, "07:33", date, morocco)
        assertTime(day.sunset, "17:41", date, morocco)
        assertTime(day.civilDusk, "18:08", date, morocco)
    }

    @Test
    fun `marrakech in january`() {
        // USNO 2027-01-15, tz +1 (31.6295 N, 7.9811 W): Begin Civil 08:06, Rise 08:32, Set 18:51, End Civil 19:17.
        val day = solarDay(31.6295, -7.9811, morocco, 2027, 1, 15)
        val date = LocalDate.of(2027, 1, 15)
        assertTime(day.civilDawn, "07:06", date, morocco)
        assertTime(day.sunrise, "07:32", date, morocco)
        assertTime(day.sunset, "17:51", date, morocco)
        assertTime(day.civilDusk, "18:17", date, morocco)
    }

    // endregion

    // region Madrid (40.4168 N, 3.7038 W), DST transition 2026-03-29

    @Test
    fun `madrid day before spring forward`() {
        // USNO 2026-03-28, tz +1: Begin Civil 06:38, Rise 07:05, Set 19:35, End Civil 20:02.
        val zone = ZoneId.of("Europe/Madrid")
        val day = solarDay(40.4168, -3.7038, zone, 2026, 3, 28)
        val date = LocalDate.of(2026, 3, 28)
        assertTime(day.civilDawn, "06:38", date, zone)
        assertTime(day.sunrise, "07:05", date, zone)
        assertTime(day.sunset, "19:35", date, zone)
        assertTime(day.civilDusk, "20:02", date, zone)
    }

    @Test
    fun `madrid on spring forward day`() {
        // USNO 2026-03-29, tz +2 (clocks jump from 02:00 to 03:00): Begin Civil 07:36, Rise 08:04,
        // Set 20:36, End Civil 21:04.
        val zone = ZoneId.of("Europe/Madrid")
        val day = solarDay(40.4168, -3.7038, zone, 2026, 3, 29)
        val date = LocalDate.of(2026, 3, 29)
        assertTime(day.civilDawn, "07:36", date, zone)
        assertTime(day.sunrise, "08:04", date, zone)
        assertTime(day.sunset, "20:36", date, zone)
        assertTime(day.civilDusk, "21:04", date, zone)
    }

    // endregion

    // region Southern hemisphere

    @Test
    fun `sydney in winter`() {
        // USNO 2026-07-15, tz +10 (33.8688 S, 151.2093 E): Begin Civil 06:31, Rise 06:58, Set 17:04, End Civil 17:31.
        val zone = ZoneId.of("Australia/Sydney")
        val day = solarDay(-33.8688, 151.2093, zone, 2026, 7, 15)
        val date = LocalDate.of(2026, 7, 15)
        assertTime(day.civilDawn, "06:31", date, zone)
        assertTime(day.sunrise, "06:58", date, zone)
        assertTime(day.sunset, "17:04", date, zone)
        assertTime(day.civilDusk, "17:31", date, zone)
    }

    @Test
    fun `auckland summer crosses the UTC day boundary`() {
        // USNO 2027-01-15, tz +13 (36.8485 S, 174.7633 E): Begin Civil 05:48, Rise 06:17, Set 20:42, End Civil 21:12.
        // Sunrise is the previous day in UTC, which the local-day anchoring must handle.
        val zone = ZoneId.of("Pacific/Auckland")
        val day = solarDay(-36.8485, 174.7633, zone, 2027, 1, 15)
        val date = LocalDate.of(2027, 1, 15)
        assertTime(day.civilDawn, "05:48", date, zone)
        assertTime(day.sunrise, "06:17", date, zone)
        assertTime(day.sunset, "20:42", date, zone)
        assertTime(day.civilDusk, "21:12", date, zone)
    }

    // endregion

    // region Polar

    @Test
    fun `tromso polar night has civil twilight only`() {
        // USNO 2026-12-21, tz +1 (69.6492 N, 18.9553 E): sun continuously below the horizon,
        // Begin Civil 09:31, End Civil 13:53.
        val zone = ZoneId.of("Europe/Oslo")
        val day = solarDay(69.6492, 18.9553, zone, 2026, 12, 21)
        val date = LocalDate.of(2026, 12, 21)
        assertThat(day.isPolarNight).isTrue()
        assertThat(day.isPolarDay).isFalse()
        assertThat(day.sunrise).isNull()
        assertThat(day.sunset).isNull()
        assertTime(day.civilDawn, "09:31", date, zone)
        assertTime(day.civilDusk, "13:53", date, zone)
    }

    @Test
    fun `tromso midnight sun`() {
        // USNO 2026-06-21, tz +2: sun continuously above the horizon and the twilight limit.
        val day = solarDay(69.6492, 18.9553, ZoneId.of("Europe/Oslo"), 2026, 6, 21)
        assertThat(day.isPolarDay).isTrue()
        assertThat(day.isPolarNight).isFalse()
        assertThat(day.sunrise).isNull()
        assertThat(day.sunset).isNull()
        assertThat(day.civilDawn).isNull()
        assertThat(day.civilDusk).isNull()
    }

    @Test
    fun `tromso shoulder season still has an ordinary day`() {
        // USNO 2026-11-20, tz +1: Begin Civil 08:11, Rise 09:59, Set 13:00, End Civil 14:48.
        val zone = ZoneId.of("Europe/Oslo")
        val day = solarDay(69.6492, 18.9553, zone, 2026, 11, 20)
        val date = LocalDate.of(2026, 11, 20)
        assertTime(day.civilDawn, "08:11", date, zone)
        assertTime(day.sunrise, "09:59", date, zone)
        assertTime(day.sunset, "13:00", date, zone)
        assertTime(day.civilDusk, "14:48", date, zone)
        assertThat(day.isPolarDay).isFalse()
        assertThat(day.isPolarNight).isFalse()
    }

    // endregion

    // region Behaviour

    @Test
    fun `any instant of the local day gives the same day`() {
        val zone = ZoneId.of("Europe/Madrid")
        val morning = LocalDate.of(2026, 3, 29).atTime(0, 5).atZone(zone).toInstant()
        val night = LocalDate.of(2026, 3, 29).atTime(23, 55).atZone(zone).toInstant()
        assertThat(SolarDay.compute(40.4168, -3.7038, morning, zone))
            .isEqualTo(SolarDay.compute(40.4168, -3.7038, night, zone))
    }

    @Test
    fun `ordered events on an ordinary day`() {
        val day = solarDay(48.8566, 2.3522, ZoneId.of("Europe/Paris"), 2026, 10, 12)
        assertThat(day.civilDawn!!).isLessThan(day.sunrise!!)
        assertThat(day.sunrise!!).isLessThan(day.sunset!!)
        assertThat(day.sunset!!).isLessThan(day.civilDusk!!)
    }

    @Test
    fun `invalid coordinates produce no times`() {
        val invalid = listOf(91.0 to 0.0, 0.0 to 181.0, Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY)
        for ((latitude, longitude) in invalid) {
            val day = SolarDay.compute(latitude, longitude, Instant.EPOCH, ZoneOffset.UTC)
            assertThat(day.sunrise).isNull()
            assertThat(day.sunset).isNull()
            assertThat(day.civilDawn).isNull()
            assertThat(day.civilDusk).isNull()
            assertThat(day.isPolarDay).isFalse()
            assertThat(day.isPolarNight).isFalse()
        }
    }

    // endregion

    // region Daylight reference

    @Test
    fun `reference prefers device location over IP location`() {
        val reference = DaylightReference.resolve(
            deviceLocation = DaylightLocation(40.0, -3.0, "Europe/Madrid"),
            ipLocation = DaylightLocation(48.0, 2.0, "Europe/Paris"),
        )!!
        assertThat(reference.latitude).isEqualTo(40.0)
        assertThat(reference.longitude).isEqualTo(-3.0)
        assertThat(reference.zone).isEqualTo(ZoneId.of("Europe/Madrid"))
    }

    @Test
    fun `reference falls back to IP location and its own time zone`() {
        val reference = DaylightReference.resolve(
            deviceLocation = DaylightLocation(null, null, null),
            ipLocation = DaylightLocation(48.0, 2.0, "Europe/Paris"),
            snapshotZoneId = "America/New_York",
        )!!
        assertThat(reference.latitude).isEqualTo(48.0)
        assertThat(reference.zone).isEqualTo(ZoneId.of("Europe/Paris"))
    }

    @Test
    fun `reference falls back to snapshot time zone then the fallback zone`() {
        val withSnapshotZone = DaylightReference.resolve(
            deviceLocation = DaylightLocation(40.0, -3.0, null),
            ipLocation = null,
            snapshotZoneId = "Europe/Lisbon",
        )!!
        assertThat(withSnapshotZone.zone).isEqualTo(ZoneId.of("Europe/Lisbon"))

        val withNothing = DaylightReference.resolve(
            deviceLocation = DaylightLocation(40.0, -3.0, "Not/AZone"),
            ipLocation = null,
            snapshotZoneId = "Also/Invalid",
            fallbackZone = ZoneId.of("Asia/Tokyo"),
        )!!
        assertThat(withNothing.zone).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }

    @Test
    fun `reference is null without valid coordinates`() {
        assertThat(DaylightReference.resolve(null, null)).isNull()
        assertThat(
            DaylightReference.resolve(
                deviceLocation = DaylightLocation(40.0, null, null),
                ipLocation = DaylightLocation(null, 2.0, null),
            ),
        ).isNull()
        assertThat(DaylightReference.resolve(DaylightLocation(123.0, 2.0, null), null)).isNull()
    }

    // endregion

    private fun solarDay(latitude: Double, longitude: Double, zone: ZoneId, year: Int, month: Int, day: Int): SolarDay =
        SolarDay.compute(latitude, longitude, LocalDate.of(year, month, day).atTime(12, 0).atZone(zone).toInstant(), zone)

    private fun assertTime(actual: Instant?, clock: String, date: LocalDate, zone: ZoneId) {
        val expected = date.atTime(LocalTime.parse(clock)).atZone(zone).toInstant()
        assertWithMessage("Expected $clock on $date in $zone, got nothing").that(actual).isNotNull()
        val delta = Duration.between(expected, actual)
        assertWithMessage("Expected $clock on $date in $zone, off by ${delta.seconds / 60.0} min")
            .that(delta.abs().seconds)
            .isAtMost(ToleranceSeconds)
    }

    private companion object {
        const val ToleranceSeconds = 120L
    }
}
