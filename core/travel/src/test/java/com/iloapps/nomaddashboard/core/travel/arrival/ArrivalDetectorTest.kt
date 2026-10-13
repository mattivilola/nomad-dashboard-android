package com.iloapps.nomaddashboard.core.travel.arrival

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class ArrivalDetectorTest {
    // 2026-10-12 12:00 UTC, a Monday.
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 12)

    private fun day(daysAgo: Int, code: String?, country: String = code.orEmpty()) = VisitedCountryDay(
        date = today.minusDays(daysAgo.toLong()),
        country = country,
        countryCode = code,
        source = VisitedPlaceSource.DEVICE_LOCATION,
        isInferred = false,
    )

    private fun detect(
        days: List<VisitedCountryDay>,
        current: String?,
        settings: AppSettings = AppSettings(),
    ): ArrivalEvent? = ArrivalDetector.arrival(days, current, settings, now, zone)

    @Test
    fun `first run without history is not an arrival`() {
        assertThat(detect(emptyList(), "ES")).isNull()
    }

    @Test
    fun `today's own record is not history`() {
        assertThat(detect(listOf(day(0, "ES")), "ES")).isNull()
    }

    @Test
    fun `staying in the same country is not an arrival`() {
        assertThat(detect(listOf(day(3, "ES"), day(1, "ES")), "ES")).isNull()
    }

    @Test
    fun `a new country is an arrival from the most recent previous day`() {
        val event = detect(listOf(day(5, "FR"), day(1, "ES")), "MA")!!

        assertThat(event.countryCode).isEqualTo("MA")
        assertThat(event.previousCountryCode).isEqualTo("ES")
        assertThat(event.arrivedOn).isEqualTo(today)
    }

    @Test
    fun `today's record does not hide the arrival`() {
        val event = detect(listOf(day(1, "ES"), day(0, "MA")), "MA")!!

        assertThat(event.previousCountryCode).isEqualTo("ES")
    }

    @Test
    fun `the current country code is normalized`() {
        assertThat(detect(listOf(day(1, "ES")), " ma ")!!.countryCode).isEqualTo("MA")
    }

    @Test
    fun `a missing or invalid current country is not an arrival`() {
        assertThat(detect(listOf(day(1, "ES")), null)).isNull()
        assertThat(detect(listOf(day(1, "ES")), "")).isNull()
        assertThat(detect(listOf(day(1, "ES")), "Morocco")).isNull()
    }

    @Test
    fun `the acknowledged country is not briefed again`() {
        val acknowledged = AppSettings(arrivalBriefingAcknowledgedCountryCode = "MA")
        assertThat(detect(listOf(day(1, "ES")), "MA", acknowledged)).isNull()

        // Having acknowledged another country does not suppress this one.
        val other = AppSettings(arrivalBriefingAcknowledgedCountryCode = "FR")
        assertThat(detect(listOf(day(1, "ES")), "MA", other)!!.countryCode).isEqualTo("MA")
    }

    @Test
    fun `disabled briefings never detect`() {
        assertThat(detect(listOf(day(1, "ES")), "MA", AppSettings(arrivalBriefingEnabled = false))).isNull()
    }

    @Test
    fun `stale history is not an arrival`() {
        // Exactly 30 days is still a crossing; 31 is stale history.
        assertThat(detect(listOf(day(30, "ES")), "MA")!!.previousCountryCode).isEqualTo("ES")
        assertThat(detect(listOf(day(31, "ES")), "MA")).isNull()
        assertThat(detect(listOf(day(200, "ES")), "MA")).isNull()
    }

    @Test
    fun `the most recent day decides staleness not older ones`() {
        val event = detect(listOf(day(90, "FR"), day(2, "ES")), "MA")!!

        assertThat(event.previousCountryCode).isEqualTo("ES")
    }

    // region Arrival window

    /** ES until `daysSinceCrossing + 1` days ago, then MA every day through today. */
    private fun crossing(daysSinceCrossing: Int, previous: String = "ES") =
        listOf(day(daysSinceCrossing + 1, previous)) + (0..daysSinceCrossing).map { day(it, "MA") }

    @Test
    fun `the briefing is offered on the crossing day and for three days after`() {
        assertThat(ArrivalDetector.ARRIVAL_WINDOW_DAYS).isEqualTo(3)
        for (daysSince in 0..3) {
            val event = detect(crossing(daysSince), "MA")!!

            assertThat(event.countryCode).isEqualTo("MA")
            assertThat(event.previousCountryCode).isEqualTo("ES")
            assertThat(event.arrivedOn).isEqualTo(today.minusDays(daysSince.toLong()))
        }
    }

    @Test
    fun `the briefing is gone four days after crossing`() {
        assertThat(detect(crossing(4), "MA")).isNull()
        assertThat(detect(crossing(20), "MA")).isNull()
    }

    @Test
    fun `opening two days after crossing still shows until acknowledged`() {
        val days = crossing(2)
        assertThat(detect(days, "MA")).isNotNull()

        val acknowledged = ArrivalBriefingBuilder.acknowledged(AppSettings(), "MA", now)
        assertThat(detect(days, "MA", acknowledged)).isNull()
    }

    @Test
    fun `a missing record inside the first days does not hide the arrival`() {
        // Crossed 3 days ago; the phone was off the day after.
        val days = listOf(day(4, "ES"), day(3, "MA"), day(1, "MA"), day(0, "MA"))

        assertThat(detect(days, "MA")!!.arrivedOn).isEqualTo(today.minusDays(3))
    }

    @Test
    fun `the stale limit counts from the start of the stay not from today`() {
        // Previous record 30 days before the crossing, two days ago: still an arrival.
        val ok = listOf(day(32, "ES"), day(2, "MA"), day(1, "MA"), day(0, "MA"))
        assertThat(detect(ok, "MA")!!.previousCountryCode).isEqualTo("ES")

        val stale = listOf(day(33, "ES"), day(2, "MA"), day(1, "MA"), day(0, "MA"))
        assertThat(detect(stale, "MA")).isNull()
    }

    @Test
    fun `coming back to a country after a trip elsewhere is an arrival again`() {
        // Morocco, a week in Spain, back in Morocco today.
        val days = (20..30).map { day(it, "MA") } + (1..19).map { day(it, "ES") }

        val event = detect(days, "MA")!!
        assertThat(event.previousCountryCode).isEqualTo("ES")
        assertThat(event.arrivedOn).isEqualTo(today)
    }

    @Test
    fun `a stay that started after a gap in the same country is not an arrival`() {
        // Morocco, nothing recorded for a week, Morocco again: no crossing happened.
        assertThat(detect(listOf(day(9, "MA"), day(8, "MA"), day(0, "MA")), "MA")).isNull()
    }

    // endregion

    @Test
    fun `a border day containing the current country is not an arrival`() {
        // Crossed ES to MA yesterday: the traveller was already in Morocco.
        assertThat(detect(listOf(day(1, "ES"), day(1, "MA")), "MA")).isNull()
        assertThat(detect(listOf(day(1, "MA"), day(1, "ES")), "MA")).isNull()
    }

    @Test
    fun `a border day uses the last recorded country as previous`() {
        assertThat(detect(listOf(day(1, "ES"), day(1, "MA")), "PT")!!.previousCountryCode).isEqualTo("MA")
        assertThat(detect(listOf(day(1, "MA"), day(1, "ES")), "PT")!!.previousCountryCode).isEqualTo("ES")
    }

    @Test
    fun `days without a country code are skipped`() {
        val unknown = day(1, null, country = "Somewhere")

        assertThat(detect(listOf(day(3, "ES"), unknown), "MA")!!.previousCountryCode).isEqualTo("ES")
        assertThat(detect(listOf(unknown), "MA")).isNull()
    }

    @Test
    fun `today is the calendar day in the injected zone`() {
        // 23:30 UTC on 11 Oct is already 12 Oct in Auckland, so the 11 Oct record is yesterday there.
        val lateEvening = Instant.parse("2026-10-11T23:30:00Z")
        val auckland = ZoneId.of("Pacific/Auckland")
        val days = listOf(day(1, "ES"))

        assertThat(ArrivalDetector.arrival(days, "MA", AppSettings(), lateEvening, auckland)!!.arrivedOn).isEqualTo(today)
        // In UTC it is still 11 Oct, and the 11 Oct record is today's own.
        assertThat(ArrivalDetector.arrival(days, "MA", AppSettings(), lateEvening, zone)).isNull()
    }

    // region Acknowledgement

    @Test
    fun `acknowledging stores the country and time`() {
        val updated = ArrivalBriefingBuilder.acknowledged(AppSettings(), "ma", now)

        assertThat(updated.arrivalBriefingAcknowledgedCountryCode).isEqualTo("MA")
        assertThat(updated.arrivalBriefingAcknowledgedAt).isEqualTo(now)
        assertThat(detect(listOf(day(1, "ES")), "MA", updated)).isNull()
    }

    @Test
    fun `acknowledging leaves everything else and invalid codes alone`() {
        val settings = AppSettings(citizenshipCountryCodes = listOf("FI"), arrivalBriefingAcknowledgedCountryCode = "ES")

        val updated = ArrivalBriefingBuilder.acknowledged(settings, "MA", now)
        assertThat(updated.citizenshipCountryCodes).containsExactly("FI")

        assertThat(ArrivalBriefingBuilder.acknowledged(settings, "Morocco", now)).isEqualTo(settings)
        assertThat(settings.arrivalBriefingAcknowledgedCountryCode).isEqualTo("ES")
    }

    // endregion
}
