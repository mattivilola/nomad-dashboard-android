package com.iloapps.nomaddashboard.core.travel.headsup

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.TravelProfile
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.travel.stays.run
import com.iloapps.nomaddashboard.core.travel.stays.through
import com.iloapps.nomaddashboard.core.travel.stays.trailingDays
import com.iloapps.nomaddashboard.core.travel.stays.yearStart
import java.time.ZoneId
import org.junit.Test

/** The stay heads-ups through the engine (macOS StayInsightTests, engine integration). */
class HeadsUpEngineStaysTest {
    /** A device in [country], the way the engine finds the current country. */
    private fun inCountry(country: String?) = snapshot(travelContext = TravelContextSnapshot(deviceCountryCode = country))

    private fun stayItems(
        days: List<VisitedCountryDay>,
        settings: AppSettings,
        snapshot: DashboardSnapshot,
    ) = insights(snapshot, settings, countryDays = days)

    // Reference: MA 2026-07-27 ... 2026-10-12 is 5 + 31 + 30 + 12 = 78 days of 90: 12 left, last day 24 Oct.
    @Test
    fun `an allowance with two weeks or less left is a caution`() {
        val item = stayItems(
            days = run(2026, 7, 27, through(2026, 10, 12), "MA"),
            settings = settings(stayAllowances = mapOf("MA" to 90)),
            snapshot = inCountry("MA"),
        ).single()

        assertThat(item.kind).isEqualTo(HeadsUpKind.STAY_ALLOWANCE)
        assertThat(item.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(item.title).isEqualTo("Morocco: 12 days left")
        assertThat(item.detail).isEqualTo("Allowance 90 days · leave by 24 Oct")
        assertThat(item.statusText).isEqualTo("12 days left")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.STAYS)
    }

    // Reference: FR for the last 80 days up to 2026-10-12: used 80, 10 left, last day 22 Oct.
    @Test
    fun `schengen with two weeks or less left is a caution`() {
        val item = stayItems(trailingDays(80, "FR"), settings(citizenships = listOf("US")), inCountry("FR")).single()

        assertThat(item.kind).isEqualTo(HeadsUpKind.SCHENGEN_LIMIT)
        assertThat(item.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(item.title).isEqualTo("Schengen: 10 of 90 days left")
        assertThat(item.detail).isEqualTo("Leave by 22 Oct")
        assertThat(item.statusText).isEqualTo("Schengen 10d")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.STAYS)
    }

    // Reference: ES on 160 days of 2026 (159 recorded 01-01 ... 06-08, plus today from the live location).
    @Test
    fun `tax residence days appear a month before the threshold`() {
        val item = stayItems(yearStart(159, "ES"), settings(), inCountry("ES")).single()

        assertThat(item.kind).isEqualTo(HeadsUpKind.TAX_RESIDENCE_DAYS)
        assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(item.title).isEqualTo("Spain: 160 days this year")
        assertThat(item.detail).isEqualTo("183-day tax residence threshold · not tax advice")
        assertThat(item.statusText).isEqualTo("160 days")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.STAYS)
    }

    @Test
    fun `the stay settings gate each stay heads-up`() {
        val schengenDays = trailingDays(85, "FR")
        val us = listOf("US")

        // Schengen: exempt citizens, no citizenship and the switched-off counter stay quiet.
        assertThat(stayItems(schengenDays, settings(citizenships = listOf("FI")), inCountry("FR"))).isEmpty()
        assertThat(stayItems(schengenDays, settings(citizenships = listOf("US", "DE")), inCountry("FR"))).isEmpty()
        assertThat(stayItems(schengenDays, settings(), inCountry("FR"))).isEmpty()
        assertThat(
            stayItems(schengenDays, settings(citizenships = us, schengenCounterEnabled = false), inCountry("FR")),
        ).isEmpty()

        // Tax days: switched off, another country, last year's days, no current country.
        val spain = yearStart(170, "ES")
        assertThat(stayItems(spain, settings(taxResidenceDaysEnabled = false), inCountry("ES"))).isEmpty()
        assertThat(stayItems(spain, settings(), inCountry("PT"))).isEmpty()
        assertThat(stayItems(spain, settings(), inCountry(null))).isEmpty()
        assertThat(stayItems(run(2025, 1, 1, through(2025, 12, 31), "ES"), settings(), inCountry("ES"))).isEmpty()

        // Allowance: needs one for the current country.
        val morocco = trailingDays(80, "MA")
        assertThat(stayItems(morocco, settings(stayAllowances = mapOf("ES" to 90)), inCountry("MA"))).isEmpty()
        assertThat(stayItems(morocco, settings(), inCountry("MA"))).isEmpty()
        assertThat(stayItems(morocco, settings(stayAllowances = mapOf("MA" to 90)), inCountry(null))).isEmpty()
    }

    @Test
    fun `no stay days and no country means no stay heads-ups`() {
        assertThat(insights(DashboardSnapshot(), settings(stayAllowances = mapOf("MA" to 90)))).isEmpty()
    }

    @Test
    fun `stays are silent when the stays card is off`() {
        val settings = settings(stayAllowances = mapOf("MA" to 90), visitedPlacesEnabled = false)

        assertThat(stayItems(trailingDays(85, "MA"), settings, inCountry("MA"))).isEmpty()
    }

    @Test
    fun `the current country comes from the device then the IP then local info`() {
        val days = trailingDays(80, "MA")
        val settings = settings(stayAllowances = mapOf("MA" to 90))

        assertThat(stayItems(days, settings, snapshot(travelContext = TravelContextSnapshot(countryCode = "ma"))).kinds())
            .containsExactly(HeadsUpKind.STAY_ALLOWANCE)
        assertThat(stayItems(days, settings, snapshot(localInfo = localInfo(countryCode = "MA"))).kinds())
            .containsExactly(HeadsUpKind.STAY_ALLOWANCE)
        // The device location wins over a stale IP country.
        val deviceWins = TravelContextSnapshot(deviceCountryCode = "ES", countryCode = "MA")
        assertThat(stayItems(days, settings, snapshot(travelContext = deviceWins))).isEmpty()
    }

    @Test
    fun `today follows the injected zone`() {
        val days = trailingDays(80, "MA") // the last record is 12 Oct
        val settings = settings(stayAllowances = mapOf("MA" to 90))
        val justAfterMidnightInParis = parisInstant(2026, 10, 12, 0, 30)

        val paris = insights(inCountry("MA"), settings, justAfterMidnightInParis, days)
        val losAngeles = insights(inCountry("MA"), settings, justAfterMidnightInParis, days, zone = ZoneId.of("America/Los_Angeles"))

        // In Los Angeles it is still 11 Oct, so the 12 Oct record lies in the future and is ignored.
        assertThat(paris.single().title).isEqualTo("Morocco: 10 days left")
        assertThat(losAngeles.single().title).isEqualTo("Morocco: 11 days left")
    }

    @Test
    fun `country names and days follow the injected formats`() {
        val formats = HeadsUpFormats(
            countryName = { "<$it>" },
            dayMonth = { "D${it.dayOfMonth}" },
        )

        val item = insights(
            inCountry("MA"),
            settings(stayAllowances = mapOf("MA" to 90)),
            countryDays = run(2026, 7, 27, through(2026, 10, 12), "MA"),
            formats = formats,
        ).single()

        assertThat(item.title).isEqualTo("<MA>: 12 days left")
        assertThat(item.detail).isEqualTo("Allowance 90 days · leave by D24")
    }

    @Test
    fun `attention outranks caution across stay and other heads-ups`() {
        // Schengen has 2 days left (attention) while the allowance has 12 (caution) and tax days are info.
        val items = stayItems(
            days = trailingDays(88, "FR"),
            settings = settings(citizenships = listOf("US"), stayAllowances = mapOf("FR" to 100)),
            snapshot = inCountry("FR"),
        )

        assertThat(items.kinds()).containsExactly(HeadsUpKind.SCHENGEN_LIMIT, HeadsUpKind.STAY_ALLOWANCE).inOrder()
        assertThat(items.map { it.severity }).containsExactly(HeadsUpSeverity.ATTENTION, HeadsUpSeverity.CAUTION).inOrder()
    }

    @Test
    fun `stay heads-ups rank by the profile's tie-break order`() {
        // Allowance caution, Schengen caution, advisory caution and wind caution all tie on severity.
        val snapshot = snapshot(
            alerts = alerts(TravelAlertKind.ADVISORY to TravelAlertSeverity.CAUTION),
            weather = weather(windKph = 45.0),
            travelContext = TravelContextSnapshot(deviceCountryCode = "FR"),
        )
        val days = trailingDays(80, "FR")
        val base = settings(citizenships = listOf("US"), stayAllowances = mapOf("FR" to 92))

        val nomad = insights(snapshot, base, countryDays = days).filter { it.severity == HeadsUpSeverity.CAUTION }
        val roadTrip = insights(snapshot, base.copy(travelProfile = TravelProfile.ROAD_TRIP), countryDays = days)
            .filter { it.severity == HeadsUpSeverity.CAUTION }

        assertThat(nomad.kinds()).containsExactly(
            HeadsUpKind.STAY_ALLOWANCE,
            HeadsUpKind.SCHENGEN_LIMIT,
            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.STRONG_WIND,
        ).inOrder()
        assertThat(roadTrip.kinds()).containsExactly(
            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.STAY_ALLOWANCE,
            HeadsUpKind.SCHENGEN_LIMIT,
            HeadsUpKind.TRAVEL_ADVISORY,
        ).inOrder()
    }
}
