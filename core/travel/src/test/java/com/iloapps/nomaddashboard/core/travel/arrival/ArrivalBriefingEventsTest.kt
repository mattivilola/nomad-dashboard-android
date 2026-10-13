package com.iloapps.nomaddashboard.core.travel.arrival

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class ArrivalBriefingEventsTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 12)

    private fun day(daysAgo: Int, code: String?) = VisitedCountryDay(
        date = today.minusDays(daysAgo.toLong()),
        country = code.orEmpty(),
        countryCode = code,
        source = VisitedPlaceSource.DEVICE_LOCATION,
        isInferred = false,
    )

    private fun reopened(days: List<VisitedCountryDay>, current: String?) =
        ArrivalBriefingEvents.reopened(days, current, now, zone)

    private val detected = ArrivalEvent("MA", "ES", today)

    @Test
    fun `the detected arrival is shown when nothing was reopened`() {
        assertThat(ArrivalBriefingEvents.event(null, detected, "MA")).isEqualTo(detected)
        assertThat(ArrivalBriefingEvents.event(null, null, "MA")).isNull()
    }

    @Test
    fun `a reopened briefing wins even when nothing is detected`() {
        val reopened = reopened(listOf(day(5, "ES"), day(1, "MA")), "ma")!!

        assertThat(ArrivalBriefingEvents.event(reopened, null, "MA")).isEqualTo(reopened)
    }

    @Test
    fun `a reopened briefing of another country is dropped`() {
        val reopened = reopened(listOf(day(1, "MA")), "MA")!!

        assertThat(ArrivalBriefingEvents.event(reopened, null, "ES")).isNull()
        assertThat(ArrivalBriefingEvents.event(reopened, null, null)).isNull()
    }

    @Test
    fun `reopening names the most recent different country as the previous one`() {
        val event = reopened(listOf(day(5, "ES"), day(1, "MA"), day(0, "MA")), "ma")!!

        assertThat(event.countryCode).isEqualTo("MA")
        assertThat(event.previousCountryCode).isEqualTo("ES")
        assertThat(event.arrivedOn).isEqualTo(today.minusDays(1))
    }

    @Test
    fun `reopening without a different previous country flags nothing as changed`() {
        val event = reopened(listOf(day(1, "MA")), "MA")!!

        assertThat(event.previousCountryCode).isEqualTo("MA")
    }

    @Test
    fun `reopening with no history at all briefs a stay that starts today`() {
        val event = reopened(emptyList(), "MA")!!

        assertThat(event.previousCountryCode).isEqualTo("MA")
        assertThat(event.arrivedOn).isEqualTo(today)
    }

    @Test
    fun `reopening needs a current country`() {
        assertThat(reopened(emptyList(), null)).isNull()
        assertThat(reopened(emptyList(), "Morocco")).isNull()
    }

    @Test
    fun `reopening is offered only within the arrival window`() {
        fun stay(length: Int) = (0 until length).map { day(it, "MA") } + day(length, "ES")

        assertThat(reopened(stay(ArrivalDetector.ARRIVAL_WINDOW_DAYS + 1), "MA")).isNotNull()
        assertThat(reopened(stay(ArrivalDetector.ARRIVAL_WINDOW_DAYS + 2), "MA")).isNull()
    }
}
