package com.iloapps.nomaddashboard.core.travel.rhythm

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Test

class LocalRhythmRowTest {
    private val zone = ZoneId.of("UTC")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
    private val format: (LocalDate) -> String = dayMonth::format

    private fun instant(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    private fun period(
        start: Instant,
        end: Instant,
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

    private fun row(period: LocalRhythmPeriod, now: Instant) = LocalRhythmRow.of(period, now, zone, format)

    @Test
    fun `an expected period shows its range and the shift note`() {
        val row = row(
            period(instant(2027, 2, 8), instant(2027, 3, 9, 23, 59)),
            now = instant(2027, 2, 20, 12),
        )

        assertThat(row.name).isEqualTo("Ramadan")
        assertThat(row.dates).isEqualTo("8 Feb – 9 Mar (expected; may shift a day)")
        assertThat(row.timing).isEqualTo("Now")
        assertThat(row.guidance).isEqualTo("Shops and offices keep shorter hours.")
    }

    @Test
    fun `a computed or fixed period has no shift note`() {
        val row = row(
            period(instant(2026, 8, 15), instant(2026, 8, 15, 23, 59), "Ferragosto", LocalRhythmDateConfidence.FIXED),
            now = instant(2026, 8, 14, 12),
        )

        assertThat(row.dates).isEqualTo("15 Aug")
        assertThat(row.timing).isEqualTo("Tomorrow")
    }

    @Test
    fun `an upcoming period says how far away it is`() {
        val row = row(
            period(instant(2027, 2, 8), instant(2027, 3, 9, 23, 59)),
            now = instant(2027, 2, 3, 12),
        )

        assertThat(row.timing).isEqualTo("In 5 days")
    }

    @Test
    fun `the name drops the explanatory parenthetical`() {
        val row = row(
            period(instant(2027, 2, 6), instant(2027, 2, 10), "Tết (Lunar New Year)"),
            now = instant(2027, 2, 6, 12),
        )

        assertThat(row.name).isEqualTo("Tết")
    }

    @Test
    fun `days are read in the given time zone`() {
        // 22:30 UTC on 7 Feb is already 8 Feb in Helsinki (UTC+2).
        val helsinki = ZoneId.of("Europe/Helsinki")
        val period = period(
            LocalDateTime.of(2027, 2, 8, 0, 0).atZone(helsinki).toInstant(),
            LocalDateTime.of(2027, 2, 8, 23, 59).atZone(helsinki).toInstant(),
        )

        val row = LocalRhythmRow.of(period, instant(2027, 2, 7, 22, 30), helsinki, format)

        assertThat(row.timing).isEqualTo("Now")
        assertThat(row.dates).startsWith("8 Feb")
    }
}
