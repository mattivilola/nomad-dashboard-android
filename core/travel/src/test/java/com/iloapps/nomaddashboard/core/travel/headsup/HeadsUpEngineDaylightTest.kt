package com.iloapps.nomaddashboard.core.travel.headsup

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightReference
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

/** The Road trip & RV daylight heads-up through the engine (macOS DashboardInsightEngineTests, daylight). */
class HeadsUpEngineDaylightTest {
    private val parisSnapshot = snapshot(travelContext = ParisLocation)

    private fun minutesBeforeSunset(minutes: Long): Instant = ParisSun.sunset!!.minus(Duration.ofMinutes(minutes))

    private fun clock(instant: Instant) = parisClock(instant)

    @Test
    fun `daylight is road trip only`() {
        val nearSunset = minutesBeforeSunset(30)

        assertThat(roadTripInsights(parisSnapshot, nearSunset).kinds()).containsExactly(HeadsUpKind.DAYLIGHT)
        assertThat(insights(parisSnapshot, at = nearSunset)).isEmpty()
        assertThat(insights(parisSnapshot, settings(profile = TravelProfile.NOMAD), nearSunset)).isEmpty()
    }

    @Test
    fun `within an hour before sunset is a caution`() {
        val sunset = ParisSun.sunset!!
        val dusk = ParisSun.civilDusk!!
        val item = roadTripInsights(parisSnapshot, minutesBeforeSunset(45)).single()

        assertThat(item.kind).isEqualTo(HeadsUpKind.DAYLIGHT)
        assertThat(item.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(item.title).isEqualTo("Sunset ${clock(sunset)} · 45 min of daylight")
        assertThat(item.statusText).isEqualTo("Sunset ${clock(sunset)}")
        assertThat(item.detail).isEqualTo("Dusk (civil twilight) ends ${clock(dusk)}")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.WEATHER)

        val boundary = roadTripInsights(parisSnapshot, minutesBeforeSunset(60)).single()
        assertThat(boundary.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(boundary.title).endsWith("1 h of daylight")
    }

    @Test
    fun `within two hours before sunset is an info with hours and minutes`() {
        val sunset = ParisSun.sunset!!

        val item = roadTripInsights(parisSnapshot, minutesBeforeSunset(110)).single()
        assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(item.title).isEqualTo("Sunset ${clock(sunset)} · 1 h 50 min of daylight")
        assertThat(item.statusText).isEqualTo("Sunset ${clock(sunset)}")

        val twoHours = roadTripInsights(parisSnapshot, minutesBeforeSunset(120)).single()
        assertThat(twoHours.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(twoHours.title).endsWith("2 h of daylight")

        assertThat(roadTripInsights(parisSnapshot, sunset.minusSeconds(7_201))).isEmpty()
        assertThat(roadTripInsights(parisSnapshot, sunset.minus(Duration.ofHours(6)))).isEmpty()
    }

    @Test
    fun `dark after civil dusk plus grace names tomorrow's sunrise`() {
        val dusk = ParisSun.civilDusk!!
        val tomorrowSunrise = parisSolarDay(1).sunrise!!

        val item = roadTripInsights(parisSnapshot, dusk.plus(Duration.ofMinutes(30))).single()

        assertThat(item.kind).isEqualTo(HeadsUpKind.DAYLIGHT)
        assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(item.title).isEqualTo("Dark until ${clock(tomorrowSunrise)}")
        assertThat(item.statusText).isEqualTo("Dark")
        assertThat(item.detail).isEqualTo("Sunrise tomorrow ${clock(tomorrowSunrise)}")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.WEATHER)
    }

    @Test
    fun `between sunset and the dark grace produces nothing`() {
        val sunset = ParisSun.sunset!!
        val dusk = ParisSun.civilDusk!!

        assertThat(roadTripInsights(parisSnapshot, sunset.plusSeconds(1))).isEmpty()
        assertThat(roadTripInsights(parisSnapshot, dusk)).isEmpty()
        assertThat(roadTripInsights(parisSnapshot, dusk.plusSeconds(30 * 60 - 1))).isEmpty()
    }

    @Test
    fun `stays dark after midnight until today's sunrise`() {
        val sunrise = ParisSun.sunrise!!

        val item = roadTripInsights(parisSnapshot, parisInstant(2026, 10, 12, 1, 30)).single()
        assertThat(item.title).isEqualTo("Dark until ${clock(sunrise)}")
        assertThat(item.detail).isEqualTo("Sunrise today ${clock(sunrise)}")

        assertThat(roadTripInsights(parisSnapshot, sunrise.minusSeconds(60)).kinds()).containsExactly(HeadsUpKind.DAYLIGHT)
        assertThat(roadTripInsights(parisSnapshot, sunrise.plusSeconds(60))).isEmpty()
    }

    @Test
    fun `midday produces nothing`() {
        assertThat(roadTripInsights(parisSnapshot, Now)).isEmpty()
    }

    @Test
    fun `daylight needs coordinates and uses the device then the IP location`() {
        val evening = minutesBeforeSunset(30)
        val sunset = ParisSun.sunset!!

        assertThat(roadTripInsights(snapshot(), evening)).isEmpty()
        assertThat(roadTripInsights(snapshot(travelContext = TravelContextSnapshot(deviceLatitude = 48.8566)), evening)).isEmpty()
        assertThat(
            roadTripInsights(snapshot(travelContext = ipLocation(48.8566, 2.3522, "Europe/Paris")), evening).kinds(),
        ).containsExactly(HeadsUpKind.DAYLIGHT)

        // The device location wins over a faraway IP location.
        val withFarawayIp = snapshot(
            travelContext = ParisLocation.copy(latitude = -33.8688, longitude = 151.2093, timeZoneId = "Australia/Sydney"),
        )
        assertThat(roadTripInsights(withFarawayIp, evening).single().title).startsWith("Sunset ${clock(sunset)}")
    }

    @Test
    fun `daylight formats times in the location's zone`() {
        // The device is in Lisbon, an hour behind the Paris zone used everywhere else in these tests.
        val lisbon = ZoneId.of("Europe/Lisbon")
        val reference = DaylightReference(38.7223, -9.1393, lisbon)
        val sunset = reference.solarDay(Now).sunset!!
        val snapshot = snapshot(travelContext = TravelContextSnapshot(deviceLatitude = 38.7223, deviceLongitude = -9.1393))

        val item = insights(
            snapshot,
            settings(profile = TravelProfile.ROAD_TRIP),
            at = sunset.minus(Duration.ofMinutes(30)),
            zone = lisbon,
        ).single()

        assertThat(item.title).startsWith("Sunset ${ClockTimeFormatter.Hour24.format(sunset.atZone(lisbon))}")
    }

    @Test
    fun `daylight uses the IP location's own zone when only the IP is known`() {
        // The device zone is Paris, but an IP location in Sydney knows better.
        val sydney = ZoneId.of("Australia/Sydney")
        val sunset = DaylightReference(-33.8688, 151.2093, sydney).solarDay(Now).sunset!!
        val snapshot = snapshot(travelContext = ipLocation(-33.8688, 151.2093, "Australia/Sydney"))

        val item = roadTripInsights(snapshot, sunset.minus(Duration.ofMinutes(30))).single()

        assertThat(item.title).startsWith("Sunset ${ClockTimeFormatter.Hour24.format(sunset.atZone(sydney))}")
    }

    @Test
    fun `daylight is silent in polar day and polar night`() {
        val tromso = snapshot(
            travelContext = TravelContextSnapshot(deviceLatitude = 69.6492, deviceLongitude = 18.9553),
        )
        val oslo = ZoneId.of("Europe/Oslo")
        val midnightSun = Instant.ofEpochSecond(1_782_000_000) // 2026-06-21 00:00 UTC
        val polarNight = Instant.ofEpochSecond(1_797_800_000) // about 2026-12-20 21:00 UTC

        for (hours in 0 until 24 step 3) {
            val offset = Duration.ofHours(hours.toLong())
            for (start in listOf(midnightSun, polarNight)) {
                val items = insights(tromso, settings(profile = TravelProfile.ROAD_TRIP), start.plus(offset), zone = oslo)
                assertThat(items).isEmpty()
            }
        }
    }

    @Test
    fun `without civil dusk the detail is omitted and dark is never reported`() {
        // At 64 N near midsummer the sun sets but civil twilight never ends.
        val helsinki = ZoneId.of("Europe/Helsinki")
        val highNorth = snapshot(travelContext = TravelContextSnapshot(deviceLatitude = 64.0, deviceLongitude = 25.0))
        val day = DaylightReference(64.0, 25.0, helsinki).solarDay(Instant.parse("2026-06-21T12:00:00Z"))
        val sunset = day.sunset!!
        assertThat(day.civilDusk).isNull()

        val roadTrip = settings(profile = TravelProfile.ROAD_TRIP)
        val caution = insights(highNorth, roadTrip, sunset.minus(Duration.ofMinutes(30)), zone = helsinki).single()
        assertThat(caution.detail).isNull()
        assertThat(insights(highNorth, roadTrip, sunset.plus(Duration.ofHours(2)), zone = helsinki)).isEmpty()
    }

    @Test
    fun `daylight ranks right after strong wind for road trips`() {
        val evening = minutesBeforeSunset(30) // daylight caution
        val snapshot = snapshot(
            weather = weather(windKph = 45.0, fetchedAt = evening), // wind caution
            fuel = fuel(diesel = station(FuelType.DIESEL)), // fuel info
            travelContext = ParisLocation,
        )

        assertThat(roadTripInsights(snapshot, evening).kinds()).containsExactly(
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.DAYLIGHT,
            HeadsUpKind.FUEL_NEARBY,
        ).inOrder()
    }
}
