package com.iloapps.nomaddashboard.core.travel.stays

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.LocalDate
import org.junit.Test

class StayCalculatorTest {
    // region Schengen membership

    @Test
    fun `schengen member states are the twenty nine verified countries`() {
        // Verified 2026-10: 25 EU states (all but Cyprus and Ireland) plus IS, LI, NO and CH.
        val expected = listOf(
            "AT", "BE", "BG", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GR", "HR", "HU", "IS",
            "IT", "LI", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK",
        )
        assertThat(SchengenArea.memberStates).containsExactlyElementsIn(expected).inOrder()
        assertThat(SchengenArea.memberStates).hasSize(29)
        assertThat(SchengenArea.isMember("bg")).isTrue()
        assertThat(SchengenArea.isMember("RO")).isTrue()
        assertThat(SchengenArea.isMember("IE")).isFalse()
        assertThat(SchengenArea.isMember("CY")).isFalse()
        assertThat(SchengenArea.isMember("GB")).isFalse()
    }

    @Test
    fun `free movement covers EU EEA and Switzerland including Ireland and Cyprus`() {
        assertThat(SchengenArea.euMemberStates).hasSize(27)
        assertThat(SchengenArea.freeMovementStates).hasSize(27 + 3 + 1)
        for (code in listOf("FI", "IE", "CY", "DE", "IS", "LI", "NO", "CH")) {
            assertThat(SchengenArea.isExempt(listOf(code))).isTrue()
        }
        assertThat(SchengenArea.isExempt(listOf(" fi "))).isTrue()
        assertThat(SchengenArea.isExempt(listOf("US", "DE"))).isTrue()
        assertThat(SchengenArea.isExempt(listOf("US"))).isFalse()
        assertThat(SchengenArea.isExempt(listOf("GB", "AU"))).isFalse()
        assertThat(SchengenArea.isExempt(emptyList())).isFalse()
    }

    // endregion

    // region Schengen 90/180

    // Reference: today is Mon 2026-10-12. FR every day 2026-08-01 ... 2026-10-12 is 31 + 30 + 12 = 73
    // days: used 73, remaining 17, the window starts 2026-04-16 (today - 179), and staying 17 more days
    // makes the 90th day 2026-10-29.
    @Test
    fun `counts days in the window and finds the last permitted day`() {
        val days = run(2026, 8, 1, through(2026, 10, 12), "FR")
        val status = schengen(days, Today, listOf("US"))
        val allowance = status.allowance!!

        assertThat(status).isEqualTo(SchengenStatus.Counting(allowance))
        assertThat(allowance.usedDays).isEqualTo(73)
        assertThat(allowance.remainingDays).isEqualTo(17)
        assertThat(allowance.mustLeaveBy).isEqualTo(date(2026, 10, 29))
        assertThat(allowance.windowStart).isEqualTo(date(2026, 4, 16))
    }

    // Reference: FR 2026-07-15 ... 2026-10-12 is 17 + 31 + 30 + 12 = 90 days: exactly 90 used, 0 left,
    // and today is the last permitted day.
    @Test
    fun `exactly ninety days leaves nothing and today is the last day`() {
        val allowance = schengen(run(2026, 7, 15, through(2026, 10, 12), "FR"), Today, listOf("US")).allowance!!

        assertThat(allowance.usedDays).isEqualTo(90)
        assertThat(allowance.remainingDays).isEqualTo(0)
        assertThat(allowance.mustLeaveBy).isEqualTo(date(2026, 10, 12))
    }

    // Reference: 95 days (2026-07-10 ... 2026-10-12) is an overstay of 5: used 95, remaining clamped to 0.
    @Test
    fun `an overstay reports the real count and no remaining days`() {
        val allowance = schengen(run(2026, 7, 10, through(2026, 10, 12), "ES"), Today, listOf("US")).allowance!!

        assertThat(allowance.usedDays).isEqualTo(95)
        assertThat(allowance.remainingDays).isEqualTo(0)
        assertThat(allowance.mustLeaveBy).isEqualTo(date(2026, 10, 12))
    }

    // Reference: FR 2026-01-01 ... 2026-03-31 is 90 days. On 2026-06-29 the window [2026-01-01, 2026-06-29]
    // still holds all 90 (used 90); one day later 2026-01-01 has slid out (used 89); and by 2026-09-29 all
    // of it is gone. Outside Schengen there is no leave-by date.
    @Test
    fun `the window slides one day at a time`() {
        val days = run(2026, 1, 1, through(2026, 3, 31), "FR") + run(2026, 4, 1, through(2026, 9, 29), "GB")

        val onLastFullDay = schengen(days, date(2026, 6, 29), listOf("US")).allowance!!
        assertThat(onLastFullDay.usedDays).isEqualTo(90)
        assertThat(onLastFullDay.windowStart).isEqualTo(date(2026, 1, 1))
        assertThat(onLastFullDay.mustLeaveBy).isNull()

        val nextDay = schengen(days, date(2026, 6, 30), listOf("US")).allowance!!
        assertThat(nextDay.usedDays).isEqualTo(89)
        assertThat(nextDay.remainingDays).isEqualTo(1)
        assertThat(nextDay.windowStart).isEqualTo(date(2026, 1, 2))

        val later = schengen(days, date(2026, 9, 29), listOf("US")).allowance!!
        assertThat(later.usedDays).isEqualTo(0)
        assertThat(later.remainingDays).isEqualTo(90)
    }

    // Reference: FR 2026-01-01 ... 2026-02-28 (59 days), then outside, then FR 2026-06-20 ... 2026-06-29
    // (10 days). On 2026-06-29 used is 69. Staying on, the old block slides out as fast as new days are
    // added, so the total stays 69 for 59 days; after that it is 10 + d. The 90th day is d = 80, which is
    // 2026-09-17 (a naive 90 - 69 = 21 days would say 2026-07-20).
    @Test
    fun `leave by accounts for old days sliding out of the window`() {
        val days = run(2026, 1, 1, through(2026, 2, 28), "FR") +
            run(2026, 3, 1, through(2026, 6, 19), "GB") +
            run(2026, 6, 20, through(2026, 6, 29), "FR")
        val allowance = schengen(days, date(2026, 6, 29), listOf("US")).allowance!!

        assertThat(allowance.usedDays).isEqualTo(69)
        assertThat(allowance.remainingDays).isEqualTo(21)
        assertThat(allowance.mustLeaveBy).isEqualTo(date(2026, 9, 17))
    }

    // Reference: FR 2026-06-01 ... 06-30 (30), GB 07-01 ... 08-31 (62 days outside), FR 09-01 ... 10-12 (42):
    // used 72, the days outside do not count, 18 left, last permitted day 2026-10-30.
    @Test
    fun `re-entry after days outside only counts schengen days`() {
        val days = run(2026, 6, 1, through(2026, 6, 30), "FR") +
            run(2026, 7, 1, through(2026, 8, 31), "GB") +
            run(2026, 9, 1, through(2026, 10, 12), "FR")
        val allowance = schengen(days, Today, listOf("US")).allowance!!

        assertThat(allowance.usedDays).isEqualTo(72)
        assertThat(allowance.remainingDays).isEqualTo(18)
        assertThat(allowance.mustLeaveBy).isEqualTo(date(2026, 10, 30))
    }

    // Reference: GB 10-01 ... 10-10, with 10-10 also recorded in FR (a border day), FR 10-11 ... 10-12.
    // Entry day counts as a full Schengen day: used 3 (10-10, 10-11, 10-12). The GB stay seen from
    // 2026-10-10 is 10 days and the FR stay then is 1 day, so the border day belongs to both.
    @Test
    fun `a border day with two countries counts for both`() {
        val days = run(2026, 10, 1, through(2026, 10, 10), "GB") +
            visit(2026, 10, 10, "FR") +
            run(2026, 10, 11, through(2026, 10, 12), "FR")

        assertThat(schengen(days, Today, listOf("US")).allowance!!.usedDays).isEqualTo(3)

        val onBorderDay = StayCalculator(days, date(2026, 10, 10))
        assertThat(onBorderDay.currentStay("GB")?.dayCount).isEqualTo(10)
        assertThat(onBorderDay.currentStay("FR")?.dayCount).isEqualTo(1)

        // A day recorded twice in the same country is still one day.
        val duplicated = run(2026, 10, 10, through(2026, 10, 12), "FR") + visit(2026, 10, 12, "FR") + visit(2026, 10, 12, "GB")
        assertThat(schengen(duplicated, Today, listOf("US")).allowance!!.usedDays).isEqualTo(3)
    }

    @Test
    fun `exempt citizenships are never counted`() {
        val days = run(2026, 6, 1, through(2026, 10, 12), "FR")

        assertThat(schengen(days, Today, listOf("FI"))).isEqualTo(SchengenStatus.Exempt)
        assertThat(schengen(days, Today, listOf("US", "NO"))).isEqualTo(SchengenStatus.Exempt)
        assertThat(schengen(days, Today, listOf("IE"))).isEqualTo(SchengenStatus.Exempt)
        assertThat(schengen(days, Today, listOf("CH"))).isEqualTo(SchengenStatus.Exempt)
        assertThat(schengen(days, Today, listOf("FI")).allowance).isNull()
    }

    @Test
    fun `without a citizenship the counts are still provided`() {
        val days = run(2026, 8, 1, through(2026, 10, 12), "FR")
        val status = schengen(days, Today, emptyList())

        assertThat(status).isInstanceOf(SchengenStatus.UnknownCitizenship::class.java)
        val allowance = status.allowance!!
        assertThat(allowance.usedDays).isEqualTo(73)
        assertThat(allowance.remainingDays).isEqualTo(17)
        assertThat(allowance.mustLeaveBy).isEqualTo(date(2026, 10, 29))
        assertThat(schengen(days, Today, listOf("US")).allowance).isEqualTo(allowance)
    }

    @Test
    fun `inferred and manual days count like observed ones`() {
        val days = listOf(
            visit(2026, 10, 8, "DE", inferred = true),
            visit(2026, 10, 9, "DE", inferred = true),
            visit(2026, 10, 10, "DE", manual = true),
            visit(2026, 10, 11, "DE"),
            visit(2026, 10, 12, "DE"),
        )

        assertThat(schengen(days, Today, listOf("US")).allowance!!.usedDays).isEqualTo(5)
        assertThat(StayCalculator(days, Today).currentStay("DE")?.dayCount).isEqualTo(5)
    }

    @Test
    fun `days without a country code and future days are ignored`() {
        val days = listOf(
            VisitedCountryDay(date(2026, 10, 11), "Atlantis", null, VisitedPlaceSource.DEVICE_LOCATION, isInferred = false),
            visit(2026, 10, 12, "FR"),
            visit(2026, 10, 13, "FR"),
            visit(2026, 10, 14, "FR"),
        )

        assertThat(schengen(days, Today, listOf("US")).allowance!!.usedDays).isEqualTo(1)
        assertThat(StayCalculator(days, Today).currentStay("FR")?.dayCount).isEqualTo(1)
    }

    // Reference: Paris falls back on 2026-10-25 (a 25-hour day). DE 2026-10-20 ... 10-26, now 2026-10-26
    // 23:30: 7 days used and 7-day stay, 83 left, last day 2027-01-17, window starts 2026-04-30 (CEST).
    @Test
    fun `daylight saving end does not lose or add a day`() {
        val days = run(2026, 10, 20, through(2026, 10, 26), "DE")
        val now = parisDay(2026, 10, 26, 23, 30)
        val allowance = schengen(days, now, listOf("US")).allowance!!

        assertThat(allowance.usedDays).isEqualTo(7)
        assertThat(allowance.remainingDays).isEqualTo(83)
        assertThat(allowance.mustLeaveBy).isEqualTo(date(2027, 1, 17))
        assertThat(allowance.windowStart).isEqualTo(date(2026, 4, 30))
        assertThat(StayCalculator(days, now).currentStay("DE")?.dayCount).isEqualTo(7)
    }

    // Reference: Paris springs forward on 2026-03-29 (a 23-hour day). ES 2026-03-27 ... 03-29, now
    // 2026-03-29 00:30 (the day in Paris, still the previous UTC day): 3 days, last day 2026-06-24.
    @Test
    fun `daylight saving start and late night local time stay on the local day`() {
        val days = run(2026, 3, 27, through(2026, 3, 29), "ES")
        val now = parisDay(2026, 3, 29, 0, 30)
        val allowance = schengen(days, now, listOf("US")).allowance!!

        assertThat(now).isEqualTo(date(2026, 3, 29))
        assertThat(allowance.usedDays).isEqualTo(3)
        assertThat(allowance.mustLeaveBy).isEqualTo(date(2026, 6, 24))
        assertThat(StayCalculator(days, now).currentStay("ES")?.startDate).isEqualTo(date(2026, 3, 27))
    }

    // endregion

    // region Current stay

    @Test
    fun `a stay is the unbroken run ending today`() {
        val days = run(2026, 9, 20, through(2026, 9, 30), "FR") + run(2026, 10, 1, through(2026, 10, 12), "DE")
        val calculator = StayCalculator(days, Today)
        val stay = calculator.currentStay("DE")!!

        assertThat(stay.countryCode).isEqualTo("DE")
        assertThat(stay.startDate).isEqualTo(date(2026, 10, 1))
        assertThat(stay.dayCount).isEqualTo(12)
        assertThat(stay.unrecordedGapDays).isEqualTo(0)
        assertThat(calculator.currentStay("FR")).isNull()
        assertThat(calculator.currentStay("de")?.dayCount).isEqualTo(12)
        assertThat(StayCalculator(emptyList(), Today).currentStay("DE")).isNull()
    }

    @Test
    fun `a single unrecorded day is bridged and reported`() {
        val days = run(2026, 10, 1, through(2026, 10, 5), "DE") + run(2026, 10, 7, through(2026, 10, 12), "DE")
        val stay = StayCalculator(days, Today).currentStay("DE")!!

        assertThat(stay.startDate).isEqualTo(date(2026, 10, 1))
        assertThat(stay.dayCount).isEqualTo(12)
        assertThat(stay.unrecordedGapDays).isEqualTo(1)
    }

    @Test
    fun `two unrecorded days in a row end the stay`() {
        val days = run(2026, 10, 1, through(2026, 10, 5), "DE") + run(2026, 10, 8, through(2026, 10, 12), "DE")
        val stay = StayCalculator(days, Today).currentStay("DE")!!

        assertThat(stay.startDate).isEqualTo(date(2026, 10, 8))
        assertThat(stay.dayCount).isEqualTo(5)
        assertThat(stay.unrecordedGapDays).isEqualTo(0)
    }

    @Test
    fun `a day in another country ends the stay even without a gap`() {
        val days = run(2026, 10, 1, through(2026, 10, 5), "DE") +
            visit(2026, 10, 6, "TR") +
            run(2026, 10, 7, through(2026, 10, 12), "DE")
        val stay = StayCalculator(days, Today).currentStay("DE")!!

        assertThat(stay.startDate).isEqualTo(date(2026, 10, 7))
        assertThat(stay.dayCount).isEqualTo(6)
    }

    @Test
    fun `today may be the one unrecorded day but not two`() {
        val throughYesterday = run(2026, 10, 1, through(2026, 10, 11), "DE")
        val stay = StayCalculator(throughYesterday, Today).currentStay("DE")!!
        assertThat(stay.dayCount).isEqualTo(12)
        assertThat(stay.unrecordedGapDays).isEqualTo(1)

        val throughTwoDaysAgo = run(2026, 10, 1, through(2026, 10, 10), "DE")
        assertThat(StayCalculator(throughTwoDaysAgo, Today).currentStay("DE")).isNull()
    }

    @Test
    fun `the live country stands in for an unrecorded today`() {
        val throughYesterday = run(2026, 10, 1, through(2026, 10, 11), "DE")
        val live = StayCalculator(throughYesterday, Today, liveCountryCode = "de")
        val stay = live.currentStay("DE")!!
        assertThat(stay.dayCount).isEqualTo(12)
        assertThat(stay.unrecordedGapDays).isEqualTo(0)
        assertThat(live.schengenStatus(listOf("US")).allowance?.usedDays).isEqualTo(12)
        assertThat(live.taxYearDays(2026).daysIn("DE")).isEqualTo(12)

        // A record for today wins over the live country.
        val recorded = StayCalculator(throughYesterday + visit(2026, 10, 12, "TR"), Today, liveCountryCode = "DE")
        assertThat(recorded.currentStay("DE")).isNull()
        assertThat(recorded.currentStay("TR")?.dayCount).isEqualTo(1)
    }

    // endregion

    // region Calendar year

    // Reference for 2026 on 2026-10-12 (285 days elapsed): ES 01-01 ... 06-30 (181), PT 07-01 ... 08-31 without
    // 08-15 (61), ES 09-01 ... 10-12 (42), plus PT also recorded on the border day 10-01. ES 223, PT 62,
    // unrecorded 1 (08-15), so the order is ES then PT.
    @Test
    fun `tax year counts days per country and unrecorded days`() {
        val days = run(2026, 1, 1, through(2026, 6, 30), "ES") +
            run(2026, 7, 1, through(2026, 8, 14), "PT") +
            run(2026, 8, 16, through(2026, 8, 31), "PT") +
            run(2026, 9, 1, through(2026, 10, 12), "ES") +
            visit(2026, 10, 1, "PT")
        val summary = StayCalculator(days, Today).taxYearDays(2026)

        assertThat(summary.year).isEqualTo(2026)
        assertThat(summary.countries.map { it.countryCode }).containsExactly("ES", "PT").inOrder()
        assertThat(summary.countries.map { it.days }).containsExactly(223, 62).inOrder()
        assertThat(summary.unrecordedDays).isEqualTo(1)
        assertThat(summary.daysIn("es")).isEqualTo(223)
        assertThat(summary.daysIn("FR")).isEqualTo(0)
    }

    // Reference: 2025 on 2026-10-12 is over, so all 365 days count: FR on 12-30 and 12-31 only gives
    // FR 2 and 363 unrecorded. 2024 is a leap year with every one of its 366 days recorded in IT: IT 366,
    // 0 unrecorded. 2027 has not started: nothing, nothing unrecorded.
    @Test
    fun `tax year handles past leap and future years`() {
        val days = run(2024, 1, 1, through(2024, 12, 31), "IT") +
            run(2025, 12, 30, through(2025, 12, 31), "FR") +
            visit(2026, 10, 12, "FR")
        val calculator = StayCalculator(days, Today)

        val leap = calculator.taxYearDays(2024)
        assertThat(leap.countries.map { it.days }).containsExactly(366)
        assertThat(leap.unrecordedDays).isEqualTo(0)

        val past = calculator.taxYearDays(2025)
        assertThat(past.countries.map { it.days }).containsExactly(2)
        assertThat(past.unrecordedDays).isEqualTo(363)

        val future = calculator.taxYearDays(2027)
        assertThat(future.countries).isEmpty()
        assertThat(future.unrecordedDays).isEqualTo(0)

        // Today is day 285 of 2026 and the only recorded day.
        assertThat(calculator.taxYearDays(2026).unrecordedDays).isEqualTo(284)
    }

    @Test
    fun `tax year groups countries without a code by name and breaks ties by name`() {
        val days = listOf(
            VisitedCountryDay(date(2026, 10, 10), "Atlantis", null, VisitedPlaceSource.DEVICE_LOCATION, isInferred = false),
            VisitedCountryDay(date(2026, 10, 11), "atlantis", null, VisitedPlaceSource.DEVICE_LOCATION, isInferred = false),
            visit(2026, 10, 12, "FR"),
        )
        val summary = StayCalculator(days, Today).taxYearDays(2026)

        assertThat(summary.countries).hasSize(2)
        assertThat(summary.countries.first().days).isEqualTo(2)
        assertThat(summary.countries.first().countryCode).isNull()
    }

    @Test
    fun `tax year ties are broken by name ignoring case`() {
        val days = listOf(
            VisitedCountryDay(date(2026, 10, 11), "zambia", "ZM", VisitedPlaceSource.DEVICE_LOCATION, isInferred = false),
            VisitedCountryDay(date(2026, 10, 12), "Austria", "AT", VisitedPlaceSource.DEVICE_LOCATION, isInferred = false),
        )

        assertThat(StayCalculator(days, Today).taxYearDays(2026).countries.map { it.countryCode })
            .containsExactly("AT", "ZM").inOrder()
    }

    // endregion

    // region Allowance

    // Reference: MA 2026-07-22 ... 10-12 is 10 + 31 + 30 + 12 = 83 days of a 90-day allowance: 7 left, so
    // the last permitted day is 2026-10-19.
    @Test
    fun `allowance counts from the arrival day`() {
        val status = allowance(run(2026, 7, 22, through(2026, 10, 12), "MA"), Today, "MA", 90)!!

        assertThat(status.usedDays).isEqualTo(83)
        assertThat(status.remainingDays).isEqualTo(7)
        assertThat(status.overstayDays).isEqualTo(0)
        assertThat(status.lastPermittedDay).isEqualTo(date(2026, 10, 19))
        assertThat(status.stay.startDate).isEqualTo(date(2026, 7, 22))
    }

    // Reference: arriving 2026-01-01 with 90 days, the 90th day is 2026-03-31; on 03-15 (day 74) 16 are left.
    @Test
    fun `the arrival day is day one of the allowance`() {
        val status = allowance(run(2026, 1, 1, through(2026, 3, 15), "MA"), date(2026, 3, 15), "MA", 90)!!

        assertThat(status.usedDays).isEqualTo(74)
        assertThat(status.remainingDays).isEqualTo(16)
        assertThat(status.lastPermittedDay).isEqualTo(date(2026, 3, 31))
    }

    // Reference: MA 2026-07-14 ... 10-12 is 91 days: 1 day over a 90-day allowance, which ended 2026-10-11.
    @Test
    fun `an exceeded allowance reports the overstay`() {
        val status = allowance(run(2026, 7, 14, through(2026, 10, 12), "MA"), Today, "MA", 90)!!

        assertThat(status.usedDays).isEqualTo(91)
        assertThat(status.remainingDays).isEqualTo(0)
        assertThat(status.overstayDays).isEqualTo(1)
        assertThat(status.lastPermittedDay).isEqualTo(date(2026, 10, 11))

        val lastDay = allowance(run(2026, 7, 15, through(2026, 10, 12), "MA"), Today, "MA", 90)!!
        assertThat(lastDay.remainingDays).isEqualTo(0)
        assertThat(lastDay.overstayDays).isEqualTo(0)
        assertThat(lastDay.lastPermittedDay).isEqualTo(date(2026, 10, 12))
    }

    @Test
    fun `no allowance status without a stay or a positive allowance`() {
        val days = run(2026, 7, 22, through(2026, 10, 12), "MA")

        assertThat(allowance(days, Today, "ES", 90)).isNull()
        assertThat(allowance(days, Today, "MA", 0)).isNull()
        assertThat(allowance(emptyList(), Today, "MA", 90)).isNull()
    }

    // endregion

    private fun schengen(days: List<VisitedCountryDay>, today: LocalDate, citizenships: List<String>) =
        StayCalculator(days, today).schengenStatus(citizenships)

    private fun allowance(days: List<VisitedCountryDay>, today: LocalDate, country: String, allowanceDays: Int) =
        StayCalculator(days, today).allowanceStatus(country, allowanceDays)
}
