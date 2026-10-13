package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.time
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.utcInstant
import com.iloapps.nomaddashboard.core.travel.rhythm.IcuLunisolarCalendars
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmDateConfidence
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmKind
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPeriod
import org.junit.Test

class LocalRhythmPresentationTest {
    private val calendar = LocalRhythmCalendar(IcuLunisolarCalendars())

    private fun period(
        start: java.time.Instant,
        end: java.time.Instant,
        name: String = "Ramadan",
        confidence: LocalRhythmDateConfidence = LocalRhythmDateConfidence.EXPECTED,
    ) = LocalRhythmPeriod(
        kind = LocalRhythmKind.RAMADAN,
        name = name,
        countryCode = "MA",
        start = start,
        end = end,
        confidence = confidence,
        guidance = "Shops and offices keep shorter hours.",
    )

    private fun row(period: LocalRhythmPeriod, now: java.time.Instant) = LocalRhythmRow.of(period, time, now)

    @Test
    fun `an expected period shows its range and the shift note`() {
        val row = row(
            period(utcInstant(2027, 2, 8), utcInstant(2027, 3, 9, 23, 59)),
            now = utcInstant(2027, 2, 20, 12),
        )

        assertThat(row.name).isEqualTo("Ramadan")
        assertThat(row.dates).isEqualTo("8 Feb – 9 Mar (expected; may shift a day)")
        assertThat(row.timing).isEqualTo("Now")
        assertThat(row.guidance).isEqualTo("Shops and offices keep shorter hours.")
    }

    @Test
    fun `a computed or fixed period has no shift note`() {
        val row = row(
            period(
                utcInstant(2026, 8, 15),
                utcInstant(2026, 8, 15, 23, 59),
                name = "Ferragosto",
                confidence = LocalRhythmDateConfidence.FIXED,
            ),
            now = utcInstant(2026, 8, 14, 12),
        )

        assertThat(row.dates).isEqualTo("15 Aug")
        assertThat(row.timing).isEqualTo("Tomorrow")
    }

    @Test
    fun `an upcoming period says how far away it is`() {
        val row = row(
            period(utcInstant(2027, 2, 8), utcInstant(2027, 3, 9, 23, 59)),
            now = utcInstant(2027, 2, 3, 12),
        )

        assertThat(row.timing).isEqualTo("In 5 days")
    }

    @Test
    fun `the name drops the explanatory parenthetical`() {
        val row = row(
            period(utcInstant(2027, 2, 6), utcInstant(2027, 2, 10), name = "Tết (Lunar New Year)"),
            now = utcInstant(2027, 2, 6, 12),
        )

        assertThat(row.name).isEqualTo("Tết")
    }

    private fun snapshotIn(country: String, region: String? = null) = DashboardSnapshot(
        travelContext = TravelContextSnapshot(deviceCountryCode = country, deviceRegion = region),
    )

    @Test
    fun `rows come from the current country's calendar`() {
        // Holy Week 2026 runs 29 Mar - 5 Apr in Spain.
        val rows = LocalRhythmRow.rows(calendar, snapshotIn("ES"), time, utcInstant(2026, 4, 1, 12))

        assertThat(rows).hasSize(1)
        assertThat(rows.single().name).isEqualTo("Semana Santa")
        assertThat(rows.single().dates).startsWith("29 Mar – 5 Apr")
        assertThat(rows.single().timing).isEqualTo("Now")
    }

    @Test
    fun `a period within the look ahead is listed before it starts`() {
        val rows = LocalRhythmRow.rows(calendar, snapshotIn("ES"), time, utcInstant(2026, 3, 20, 12))

        assertThat(rows.single().timing).isEqualTo("In 9 days")
    }

    @Test
    fun `no rows outside the periods and the look ahead`() {
        assertThat(LocalRhythmRow.rows(calendar, snapshotIn("ES"), time, utcInstant(2026, 10, 12, 12))).isEmpty()
    }

    @Test
    fun `no rows without a country`() {
        assertThat(LocalRhythmRow.rows(calendar, DashboardSnapshot(), time, utcInstant(2026, 4, 1, 12))).isEmpty()
    }

    @Test
    fun `nyepi only shows for bali or an unknown region`() {
        val now = utcInstant(2027, 3, 4, 12)

        val bali = LocalRhythmRow.rows(calendar, snapshotIn("ID", region = "Bali"), time, now)
        val java = LocalRhythmRow.rows(calendar, snapshotIn("ID", region = "West Java"), time, now)
        val unknown = LocalRhythmRow.rows(calendar, snapshotIn("ID"), time, now)

        assertThat(bali.map { it.name }).contains("Nyepi")
        assertThat(java.map { it.name }).doesNotContain("Nyepi")
        assertThat(unknown.map { it.name }).contains("Nyepi")
    }

    @Test
    fun `the country falls back to the ip location and then local info`() {
        val ip = DashboardSnapshot(travelContext = TravelContextSnapshot(countryCode = "ES"))
        val localInfo = DashboardSnapshot(
            localInfo = com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot(countryCode = "ES"),
        )
        val now = utcInstant(2026, 4, 1, 12)

        assertThat(LocalRhythmRow.rows(calendar, ip, time, now)).hasSize(1)
        assertThat(LocalRhythmRow.rows(calendar, localInfo, time, now)).hasSize(1)
    }
}
