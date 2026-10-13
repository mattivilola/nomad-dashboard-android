package com.iloapps.nomaddashboard.core.travel.rhythm

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * Reference dates (all checked on 2026-10-12):
 * - Umm al-Qura (Ramadan, Eid al-Fitr, Eid al-Adha): the AlAdhan.com tables
 *   `https://aladhan.com/hijri-gregorian-calendar/<month>/<hijri year>`: 1 Ramadan 1447 = 18 Feb 2026 (30 days,
 *   last day 19 Mar), 1 Shawwal 1447 = 20 Mar 2026, 10 Dhu al-Hijjah 1447 = 27 May 2026; 1 Ramadan 1448 =
 *   8 Feb 2027 (29 days, last day 8 Mar), 1 Shawwal 1448 = 9 Mar 2027, 10 Dhu al-Hijjah 1448 = 16 May 2027.
 *   National calendars follow local moon sighting and can be a day later: the Singapore MOM list has
 *   Hari Raya Puasa on 21 Mar 2026 and 10 Mar 2027 and Hari Raya Haji on 27 May 2026 and 17 May 2027.
 * - Lunar New Year: Singapore MOM public holidays (17-18 Feb 2026, 6-7 Feb 2027,
 *   `https://www.mom.gov.sg/employment-practices/public-holidays`), Hong Kong general holidays (17-19 Feb 2026;
 *   6 Feb 2027, `https://www.gov.hk/en/about/abouthk/holiday/2026.htm` and `.../2027.htm`), Indonesian SKB 2027
 *   (Imlek 6 Feb 2027). China's State Council notice on 2026 holidays has Spring Festival 15-23 Feb 2026.
 * - Easter: Indonesian SKB 2027 (Kebangkitan Yesus Kristus 28 Mar 2027) and Philippine Proclamation 1006 of 2025
 *   (Maundy Thursday 2 Apr and Good Friday 3 Apr 2026, so Easter 5 Apr 2026). Other years (2024 31 Mar,
 *   2025 20 Apr, 2028 16 Apr, 2029 1 Apr, 2030 21 Apr) are the well-known published Easter Sundays.
 * - Yom Kippur: publicholidays.com/yom-kippur and timeanddate.com/holidays/israel/yom-kippur (21 Sep 2026, 11 Oct 2027).
 * - Nyepi: SKB 3 Menteri on national holidays 2026 and 2027 (19 Mar 2026, 8 Mar 2027), `setneg.go.id`.
 * - Diwali (Lakshmi Puja): Drik Panchang (8 Nov 2026, 29 Oct 2027), Singapore MOM (8 Nov 2026, 28 Oct 2027).
 */
class LocalRhythmCalendarTest {
    private val rhythm = LocalRhythmCalendar(IcuLunisolarCalendars())

    // Helpers

    private fun zone(id: String = "UTC"): ZoneId = ZoneId.of(id)

    private fun date(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        zone: ZoneId = zone(),
    ): Instant = LocalDateTime.of(year, month, day, hour, minute, second).atZone(zone).toInstant()

    private fun periods(
        country: String?,
        year: Int,
        month: Int,
        day: Int,
        lookahead: Int = 14,
        zone: String = "UTC",
    ): List<LocalRhythmPeriod> {
        val zoneId = zone(zone)
        return rhythm.periods(country, date(year, month, day, 12, zone = zoneId), zoneId, lookahead)
    }

    private fun List<LocalRhythmPeriod>.of(kind: LocalRhythmKind): LocalRhythmPeriod? = firstOrNull { it.kind == kind }

    /** Expects [period] to span [from] 00:00:00 through [to] 23:59:59 in [zone]. */
    private fun expectSpan(
        period: LocalRhythmPeriod?,
        from: Triple<Int, Int, Int>,
        to: Triple<Int, Int, Int>,
        zone: String = "UTC",
    ) {
        assertThat(period).isNotNull()
        val zoneId = zone(zone)
        assertThat(period!!.start).isEqualTo(date(from.first, from.second, from.third, zone = zoneId))
        assertThat(period.end).isEqualTo(date(to.first, to.second, to.third, 23, 59, 59, zoneId))
    }

    private fun ymd(year: Int, month: Int, day: Int) = Triple(year, month, day)

    // Ramadan and the Eids

    @Test
    fun `ramadan dates`() {
        expectSpan(periods("SA", 2026, 3, 1).of(LocalRhythmKind.RAMADAN), ymd(2026, 2, 18), ymd(2026, 3, 19))
        expectSpan(periods("SA", 2027, 2, 20).of(LocalRhythmKind.RAMADAN), ymd(2027, 2, 8), ymd(2027, 3, 8))
    }

    @Test
    fun `eid al fitr dates`() {
        expectSpan(periods("SA", 2026, 3, 21).of(LocalRhythmKind.EID_AL_FITR), ymd(2026, 3, 20), ymd(2026, 3, 22))
        expectSpan(periods("SA", 2027, 3, 10).of(LocalRhythmKind.EID_AL_FITR), ymd(2027, 3, 9), ymd(2027, 3, 11))
    }

    @Test
    fun `eid al adha dates`() {
        expectSpan(periods("SA", 2026, 5, 28).of(LocalRhythmKind.EID_AL_ADHA), ymd(2026, 5, 27), ymd(2026, 5, 30))
        expectSpan(periods("SA", 2027, 5, 17).of(LocalRhythmKind.EID_AL_ADHA), ymd(2027, 5, 16), ymd(2027, 5, 19))
    }

    @Test
    fun `islamic periods are expected dates`() {
        val periods = periods("MA", 2027, 2, 20, lookahead = 120)
        for (kind in listOf(LocalRhythmKind.RAMADAN, LocalRhythmKind.EID_AL_FITR, LocalRhythmKind.EID_AL_ADHA)) {
            assertThat(periods.of(kind)?.confidence).isEqualTo(LocalRhythmDateConfidence.EXPECTED)
        }
    }

    @Test
    fun `ramadan starting in the next hijri year is found`() {
        // 1 Ramadan 1449 falls in January 2028, in a different Hijri and Gregorian year than 2027.
        val start = periods("SA", 2027, 12, 25, lookahead = 60).of(LocalRhythmKind.RAMADAN)?.start
        val parts = start?.atZone(zone())?.toLocalDate()
        assertThat(parts?.year).isEqualTo(2028)
        assertThat(parts?.monthValue).isEqualTo(1)
    }

    @Test
    fun `ramadan guidance differs between countries`() {
        val saudi = periods("SA", 2027, 2, 10).of(LocalRhythmKind.RAMADAN)!!
        val turkey = periods("TR", 2027, 2, 10).of(LocalRhythmKind.RAMADAN)!!
        val indonesia = periods("ID", 2027, 2, 10).of(LocalRhythmKind.RAMADAN)!!
        assertThat(saudi.guidance).contains("Avoid eating, drinking or smoking in public")
        assertThat(turkey.guidance).isNotEqualTo(saudi.guidance)
        assertThat(turkey.guidance).contains("most restaurants and cafés in cities and tourist areas stay open")
        assertThat(indonesia.guidance).isNotEqualTo(saudi.guidance)
        assertThat(indonesia.guidance).isNotEqualTo(turkey.guidance)
    }

    @Test
    fun `morocco ramadan guidance is the mixed tier`() {
        val morocco = periods("MA", 2027, 2, 10).of(LocalRhythmKind.RAMADAN)!!
        assertThat(morocco.guidance).isEqualTo(
            "Shops and offices keep shorter hours and traffic peaks just before sunset, while evenings are lively. " +
                "Some cafés and restaurants close or screen their windows by day, though hotels and tourist areas " +
                "mostly keep serving. Be discreet eating, drinking or smoking in public during the day.",
        )
    }

    // Lunar New Year

    @Test
    fun `lunar new year dates in singapore`() {
        expectSpan(periods("SG", 2026, 2, 10).of(LocalRhythmKind.LUNAR_NEW_YEAR), ymd(2026, 2, 17), ymd(2026, 2, 18))
        expectSpan(periods("SG", 2027, 2, 1).of(LocalRhythmKind.LUNAR_NEW_YEAR), ymd(2027, 2, 6), ymd(2027, 2, 7))
    }

    @Test
    fun `lunar new year spans per country`() {
        // New Year's Day 17 Feb 2026 and 6 Feb 2027; spans are offsets from it (see the file header).
        val expectations = listOf(
            Triple("CN", ymd(2026, 2, 16), ymd(2026, 2, 23)),
            Triple("TW", ymd(2026, 2, 15), ymd(2026, 2, 19)),
            Triple("VN", ymd(2026, 2, 16), ymd(2026, 2, 20)),
            Triple("KR", ymd(2026, 2, 16), ymd(2026, 2, 18)),
            Triple("SG", ymd(2026, 2, 17), ymd(2026, 2, 18)),
            Triple("MY", ymd(2026, 2, 17), ymd(2026, 2, 18)),
            Triple("HK", ymd(2026, 2, 17), ymd(2026, 2, 19)),
            Triple("MO", ymd(2026, 2, 17), ymd(2026, 2, 19)),
        )
        for ((country, from, to) in expectations) {
            expectSpan(periods(country, 2026, 2, 12).of(LocalRhythmKind.LUNAR_NEW_YEAR), from, to)
        }
    }

    @Test
    fun `lunar new year 2027 in china starts on the eve`() {
        expectSpan(periods("CN", 2027, 2, 1).of(LocalRhythmKind.LUNAR_NEW_YEAR), ymd(2027, 2, 5), ymd(2027, 2, 12))
    }

    @Test
    fun `lunar new year names are localised`() {
        fun name(country: String) = periods(country, 2026, 2, 12).of(LocalRhythmKind.LUNAR_NEW_YEAR)?.name
        assertThat(name("VN")).isEqualTo("Tết (Lunar New Year)")
        assertThat(name("KR")).isEqualTo("Seollal (Lunar New Year)")
        assertThat(name("CN")).isEqualTo("Spring Festival (Lunar New Year)")
        assertThat(name("SG")).isEqualTo("Lunar New Year")
    }

    @Test
    fun `lunar new year in january of the next year is found`() {
        // Lunar New Year 2028 is 26 Jan, so the eve (25 Jan) is within 14 days of 20 Jan but the year differs from 2027.
        val found = periods("CN", 2028, 1, 20).of(LocalRhythmKind.LUNAR_NEW_YEAR)
        expectSpan(found, ymd(2028, 1, 25), ymd(2028, 2, 1))
        assertThat(periods("CN", 2027, 12, 20, lookahead = 60).of(LocalRhythmKind.LUNAR_NEW_YEAR)?.start)
            .isEqualTo(found?.start)
    }

    // Holy Week

    @Test
    fun `holy week dates`() {
        expectSpan(periods("ES", 2026, 3, 25).of(LocalRhythmKind.HOLY_WEEK), ymd(2026, 3, 29), ymd(2026, 4, 5))
        expectSpan(periods("ES", 2027, 3, 15).of(LocalRhythmKind.HOLY_WEEK), ymd(2027, 3, 21), ymd(2027, 3, 28))
    }

    @Test
    fun `easter sunday is the last day of holy week`() {
        val easters = listOf(
            ymd(2024, 3, 31), ymd(2025, 4, 20), ymd(2026, 4, 5), ymd(2027, 3, 28),
            ymd(2028, 4, 16), ymd(2029, 4, 1), ymd(2030, 4, 21),
        )
        for ((year, month, day) in easters) {
            val holyWeek = periods("MT", year, month, day, lookahead = 0).of(LocalRhythmKind.HOLY_WEEK)
            assertThat(holyWeek?.end).isEqualTo(date(year, month, day, 23, 59, 59))
            assertWithMessage("Palm Sunday is a Sunday")
                .that(holyWeek?.start?.atZone(zone())?.dayOfWeek)
                .isEqualTo(DayOfWeek.SUNDAY)
        }
    }

    @Test
    fun `holy week names and guidance`() {
        fun holyWeek(country: String) = periods(country, 2027, 3, 21).of(LocalRhythmKind.HOLY_WEEK)!!
        assertThat(holyWeek("ES").name).isEqualTo("Semana Santa (Holy Week)")
        assertThat(holyWeek("IT").name).isEqualTo("Settimana Santa (Holy Week)")
        assertThat(holyWeek("MX").guidance).isNotEqualTo(holyWeek("ES").guidance)
    }

    // Yom Kippur and fixed dates

    @Test
    fun `yom kippur dates`() {
        expectSpan(periods("IL", 2026, 9, 20).of(LocalRhythmKind.YOM_KIPPUR), ymd(2026, 9, 21), ymd(2026, 9, 21))
        expectSpan(periods("IL", 2027, 10, 11).of(LocalRhythmKind.YOM_KIPPUR), ymd(2027, 10, 11), ymd(2027, 10, 11))
        assertThat(periods("IL", 2027, 10, 1).of(LocalRhythmKind.YOM_KIPPUR)?.guidance)
            .startsWith("Starts the evening before.")
    }

    @Test
    fun `fixed dates`() {
        for (year in listOf(2026, 2027)) {
            expectSpan(periods("TH", year, 4, 10).of(LocalRhythmKind.SONGKRAN), ymd(year, 4, 13), ymd(year, 4, 15))
            expectSpan(periods("JP", year, 4, 20).of(LocalRhythmKind.GOLDEN_WEEK), ymd(year, 4, 29), ymd(year, 5, 5))
            expectSpan(periods("IT", year, 8, 10).of(LocalRhythmKind.FERRAGOSTO), ymd(year, 8, 15), ymd(year, 8, 15))
        }
        assertThat(periods("TH", 2026, 4, 10).of(LocalRhythmKind.SONGKRAN)?.confidence)
            .isEqualTo(LocalRhythmDateConfidence.FIXED)
        assertThat(periods("IT", 2026, 8, 10).of(LocalRhythmKind.FERRAGOSTO)?.guidance).contains("mid-August")
    }

    @Test
    fun `golden week is active while it runs`() {
        val periods = periods("JP", 2026, 5, 3, lookahead = 0)
        expectSpan(periods.of(LocalRhythmKind.GOLDEN_WEEK), ymd(2026, 4, 29), ymd(2026, 5, 5))
    }

    // Tables: Nyepi and Diwali

    @Test
    fun `nyepi dates`() {
        expectSpan(periods("ID", 2026, 3, 10).of(LocalRhythmKind.NYEPI), ymd(2026, 3, 19), ymd(2026, 3, 19))
        expectSpan(periods("ID", 2027, 3, 1).of(LocalRhythmKind.NYEPI), ymd(2027, 3, 8), ymd(2027, 3, 8))
        assertThat(periods("ID", 2027, 3, 1).of(LocalRhythmKind.NYEPI)?.guidance).contains("airport")
    }

    @Test
    fun `diwali dates in india`() {
        // Lakshmi Puja 8 Nov 2026 and 29 Oct 2027; the period runs from the day before to the day after.
        expectSpan(periods("IN", 2026, 11, 1).of(LocalRhythmKind.DIWALI), ymd(2026, 11, 7), ymd(2026, 11, 9))
        expectSpan(periods("IN", 2027, 10, 20).of(LocalRhythmKind.DIWALI), ymd(2027, 10, 28), ymd(2027, 10, 30))
        assertThat(periods("IN", 2026, 11, 1).of(LocalRhythmKind.DIWALI)?.confidence)
            .isEqualTo(LocalRhythmDateConfidence.EXPECTED)
    }

    @Test
    fun `diwali dates in other countries`() {
        expectSpan(periods("SG", 2026, 11, 1).of(LocalRhythmKind.DIWALI), ymd(2026, 11, 8), ymd(2026, 11, 8))
        expectSpan(periods("SG", 2027, 10, 20).of(LocalRhythmKind.DIWALI), ymd(2027, 10, 28), ymd(2027, 10, 28))
        expectSpan(periods("MY", 2026, 11, 1).of(LocalRhythmKind.DIWALI), ymd(2026, 11, 8), ymd(2026, 11, 8))
        expectSpan(periods("NP", 2026, 11, 1).of(LocalRhythmKind.DIWALI), ymd(2026, 11, 8), ymd(2026, 11, 10))
        assertThat(periods("NP", 2026, 11, 1).of(LocalRhythmKind.DIWALI)?.name).isEqualTo("Tihar (Festival of Lights)")
    }

    @Test
    fun `table years outside the table return nothing`() {
        // Nyepi has a decree for 2026 and 2027 only.
        for ((year, month) in listOf(2025 to 3, 2028 to 3, 2029 to 3, 2030 to 3)) {
            assertWithMessage("Nyepi $year")
                .that(periods("ID", year, month, 1, lookahead = 60).of(LocalRhythmKind.NYEPI)).isNull()
        }
        // India is tabled for 2026-2032 only.
        for ((year, month) in listOf(2025 to 10, 2033 to 10, 2033 to 11)) {
            assertWithMessage("Diwali $year")
                .that(periods("IN", year, month, 1, lookahead = 60).of(LocalRhythmKind.DIWALI)).isNull()
        }
        // Singapore 2028, Malaysia 2027 and Nepal 2028 are not tabled.
        assertThat(periods("SG", 2028, 10, 1, lookahead = 60).of(LocalRhythmKind.DIWALI)).isNull()
        assertThat(periods("MY", 2027, 10, 1, lookahead = 60).of(LocalRhythmKind.DIWALI)).isNull()
        assertThat(periods("NP", 2028, 10, 1, lookahead = 60).of(LocalRhythmKind.DIWALI)).isNull()
    }

    @Test
    fun `computed dates are not limited by a table`() {
        // Easter 2035 is 25 Mar; Holy Week is computed, not tabled.
        expectSpan(periods("ES", 2035, 3, 15).of(LocalRhythmKind.HOLY_WEEK), ymd(2035, 3, 18), ymd(2035, 3, 25))
    }

    // Country applicability

    @Test
    fun `countries without a visible period return nothing`() {
        for (country in listOf("FI", "DE", "US", "GB", "CH", "AZ", "UZ", "KZ")) {
            for (month in 1..12) {
                assertWithMessage("$country month $month")
                    .that(periods(country, 2027, month, 1, lookahead = 31)).isEmpty()
            }
        }
    }

    @Test
    fun `every kind applies to its documented countries`() {
        val documented = mapOf(
            LocalRhythmKind.RAMADAN to listOf(
                "MA", "DZ", "TN", "LY", "EG", "JO", "SA", "AE", "QA", "KW", "BH", "OM", "TR", "ID", "MY", "BN", "PK",
                "BD", "SN", "MV", "IR", "IQ", "MR",
            ),
            LocalRhythmKind.EID_AL_FITR to listOf("MA", "SA", "AE", "TR", "ID", "MY", "PK", "BD"),
            LocalRhythmKind.EID_AL_ADHA to listOf("MA", "SA", "AE", "TR", "ID", "MY", "PK", "BD"),
            LocalRhythmKind.LUNAR_NEW_YEAR to listOf("CN", "HK", "MO", "TW", "SG", "MY", "VN", "KR"),
            LocalRhythmKind.HOLY_WEEK to listOf(
                "ES", "PT", "IT", "MT", "MX", "GT", "CO", "PE", "EC", "PH", "CR", "SV", "VE", "PY",
            ),
            LocalRhythmKind.YOM_KIPPUR to listOf("IL"),
            LocalRhythmKind.SONGKRAN to listOf("TH"),
            LocalRhythmKind.GOLDEN_WEEK to listOf("JP"),
            LocalRhythmKind.NYEPI to listOf("ID"),
            LocalRhythmKind.DIWALI to listOf("IN", "NP", "SG", "MY"),
            LocalRhythmKind.FERRAGOSTO to listOf("IT"),
        )
        assertThat(documented.keys).isEqualTo(LocalRhythmKind.entries.toSet())

        // Query a day inside each kind's 2026 period, with a window wide enough to find it.
        val probes = mapOf(
            LocalRhythmKind.RAMADAN to ymd(2026, 2, 25), LocalRhythmKind.EID_AL_FITR to ymd(2026, 3, 21),
            LocalRhythmKind.EID_AL_ADHA to ymd(2026, 5, 28), LocalRhythmKind.LUNAR_NEW_YEAR to ymd(2026, 2, 17),
            LocalRhythmKind.HOLY_WEEK to ymd(2026, 4, 2), LocalRhythmKind.YOM_KIPPUR to ymd(2026, 9, 21),
            LocalRhythmKind.SONGKRAN to ymd(2026, 4, 14), LocalRhythmKind.GOLDEN_WEEK to ymd(2026, 5, 1),
            LocalRhythmKind.NYEPI to ymd(2026, 3, 19), LocalRhythmKind.DIWALI to ymd(2026, 11, 8),
            LocalRhythmKind.FERRAGOSTO to ymd(2026, 8, 15),
        )
        for ((kind, countries) in documented) {
            val (year, month, day) = probes.getValue(kind)
            for (country in countries) {
                assertWithMessage("$kind in $country")
                    .that(periods(country, year, month, day, lookahead = 0).of(kind)).isNotNull()
            }
        }
    }

    @Test
    fun `country code is normalised`() {
        val reference = periods("SA", 2027, 2, 20, lookahead = 0)
        assertThat(reference).isNotEmpty()
        assertThat(periods(" sa\n", 2027, 2, 20, lookahead = 0)).isEqualTo(reference)
        assertThat(periods("sA", 2027, 2, 20, lookahead = 0)).isEqualTo(reference)
        assertThat(reference.all { it.countryCode == "SA" }).isTrue()
    }

    @Test
    fun `missing or unknown country returns nothing`() {
        assertThat(periods(null, 2027, 2, 20)).isEmpty()
        assertThat(periods("", 2027, 2, 20)).isEmpty()
        assertThat(periods("   ", 2027, 2, 20)).isEmpty()
        assertThat(periods("ZZ", 2027, 2, 20)).isEmpty()
        assertThat(periods("SAU", 2027, 2, 20)).isEmpty()
    }

    // Lookahead window

    @Test
    fun `upcoming period within the lookahead is returned`() {
        // Ramadan 2027 starts at 00:00 on 8 Feb. The helper asks at noon, so the window ends at noon.
        assertThat(periods("SA", 2027, 1, 24, lookahead = 14).of(LocalRhythmKind.RAMADAN)).isNull()
        assertThat(periods("SA", 2027, 1, 25, lookahead = 14).of(LocalRhythmKind.RAMADAN)).isNotNull()
        assertThat(periods("SA", 2027, 1, 23, lookahead = 15).of(LocalRhythmKind.RAMADAN)).isNull()
        assertThat(periods("SA", 2027, 1, 24, lookahead = 15).of(LocalRhythmKind.RAMADAN)).isNotNull()
        assertThat(periods("SA", 2027, 1, 1, lookahead = 14).of(LocalRhythmKind.RAMADAN)).isNull()
    }

    @Test
    fun `lookahead boundary is exact to the second`() {
        val utc = zone()
        val start = date(2027, 2, 8)
        val exactlyAt = start.atZone(utc).minusDays(14).toInstant()
        val exactly = rhythm.periods("SA", exactlyAt, utc, 14)
        val oneSecondEarlier = rhythm.periods("SA", exactlyAt.minusSeconds(1), utc, 14)
        assertThat(exactly.of(LocalRhythmKind.RAMADAN)).isNotNull()
        assertThat(oneSecondEarlier.of(LocalRhythmKind.RAMADAN)).isNull()
    }

    @Test
    fun `active period is returned without lookahead`() {
        val periods = periods("SA", 2027, 2, 20, lookahead = 0)
        expectSpan(periods.of(LocalRhythmKind.RAMADAN), ymd(2027, 2, 8), ymd(2027, 3, 8))
        assertThat(periods).hasSize(1)
    }

    @Test
    fun `period already active stays in the list until its last second`() {
        val utc = zone()
        val lastSecond = date(2027, 3, 8, 23, 59, 59)
        val during = rhythm.periods("SA", lastSecond, utc, 0)
        val after = rhythm.periods("SA", lastSecond.plusSeconds(1), utc, 0)
        assertThat(during.map { it.kind }).containsExactly(LocalRhythmKind.RAMADAN)
        assertThat(after.map { it.kind }).containsExactly(LocalRhythmKind.EID_AL_FITR)
    }

    @Test
    fun `ended periods are not returned`() {
        val periods = periods("SA", 2027, 4, 1, lookahead = 7)
        assertThat(periods.of(LocalRhythmKind.RAMADAN)).isNull()
        assertThat(periods.of(LocalRhythmKind.EID_AL_FITR)).isNull()
    }

    @Test
    fun `negative lookahead behaves like zero`() {
        assertThat(periods("SA", 2027, 2, 1, lookahead = -5)).isEqualTo(periods("SA", 2027, 2, 1, lookahead = 0))
    }

    @Test
    fun `results are sorted by start`() {
        // Mid-March 2027 in Indonesia: Nyepi (8 Mar), Ramadan (ended 8 Mar), Eid al-Fitr (9 Mar) all lie around the window.
        val periods = periods("ID", 2027, 3, 8, lookahead = 60)
        assertThat(periods.map { it.kind }).containsExactly(
            LocalRhythmKind.RAMADAN,
            LocalRhythmKind.NYEPI,
            LocalRhythmKind.EID_AL_FITR,
        ).inOrder()
        assertThat(periods.map { it.start }).isInStrictOrder()
        assertThat(periods.map { it.id }.toSet()).hasSize(periods.size)
    }

    @Test
    fun `overlapping periods are all returned`() {
        // Malaysia, 8 Mar 2027: Ramadan is on its last day and Eid al-Fitr starts tomorrow.
        val kinds = periods("MY", 2027, 3, 8, lookahead = 14).map { it.kind }
        assertThat(kinds).contains(LocalRhythmKind.RAMADAN)
        assertThat(kinds).contains(LocalRhythmKind.EID_AL_FITR)
    }

    // Time zones

    @Test
    fun `period boundaries follow the time zone`() {
        val auckland = zone("Pacific/Auckland")
        val honolulu = zone("Pacific/Honolulu")
        val aucklandPeriod = periods("SA", 2027, 2, 10, zone = "Pacific/Auckland").of(LocalRhythmKind.RAMADAN)!!
        val honoluluPeriod = periods("SA", 2027, 2, 10, zone = "Pacific/Honolulu").of(LocalRhythmKind.RAMADAN)!!

        assertThat(aucklandPeriod.start).isEqualTo(date(2027, 2, 8, zone = auckland))
        assertThat(honoluluPeriod.start).isEqualTo(date(2027, 2, 8, zone = honolulu))
        // The same civil day starts 23 hours apart (UTC+13 in February against UTC-10).
        assertThat(Duration.between(aucklandPeriod.start, honoluluPeriod.start)).isEqualTo(Duration.ofHours(23))
        assertThat(aucklandPeriod.end).isEqualTo(date(2027, 3, 8, 23, 59, 59, auckland))
        assertThat(honoluluPeriod.end).isEqualTo(date(2027, 3, 8, 23, 59, 59, honolulu))
    }

    @Test
    fun `same instant can be active in one zone only`() {
        // 8 Feb 2027 00:30 in Auckland is still 7 Feb in Honolulu (UTC-10).
        val auckland = zone("Pacific/Auckland")
        val instant = date(2027, 2, 8, 0, 30, zone = auckland)
        val inAuckland = rhythm.periods("SA", instant, auckland, 0)
        val inHonolulu = rhythm.periods("SA", instant, zone("Pacific/Honolulu"), 0)
        assertThat(inAuckland.of(LocalRhythmKind.RAMADAN)).isNotNull()
        assertThat(inHonolulu.of(LocalRhythmKind.RAMADAN)).isNull()
    }

    @Test
    fun `midnight is the exact start of the first day`() {
        val tokyo = zone("Asia/Tokyo")
        val midnight = date(2026, 4, 29, zone = tokyo)
        val atMidnight = rhythm.periods("JP", midnight, tokyo, 0)
        val aSecondBefore = rhythm.periods("JP", midnight.minusSeconds(1), tokyo, 0)
        assertThat(atMidnight.map { it.kind }).containsExactly(LocalRhythmKind.GOLDEN_WEEK)
        assertThat(aSecondBefore).isEmpty()
    }

    @Test
    fun `daylight saving time does not shift the days`() {
        // Spanish clocks change on the first day of Holy Week 2026 (29 Mar).
        val madrid = zone("Europe/Madrid")
        val period = periods("ES", 2026, 3, 25, zone = "Europe/Madrid").of(LocalRhythmKind.HOLY_WEEK)!!
        assertThat(period.start).isEqualTo(date(2026, 3, 29, zone = madrid))
        assertThat(period.end).isEqualTo(date(2026, 4, 5, 23, 59, 59, madrid))
        // 8 civil days, one of them 23 hours long.
        assertThat(Duration.between(period.start, period.end))
            .isEqualTo(Duration.ofSeconds(8 * 86_400L - 3_600 - 1))
    }

    // Calendars

    @Test
    fun `calendar conversions match the reference dates`() {
        val calendars = IcuLunisolarCalendars()
        assertThat(calendars.ummAlQuraToGregorian(1448, 9, 1)).isEqualTo(LocalDate.of(2027, 2, 8))
        assertThat(calendars.hebrewToGregorian(2026 + 3761, 1, 10)).isEqualTo(LocalDate.of(2026, 9, 21))
        assertThat(calendars.hebrewToGregorian(2027 + 3761, 1, 10)).isEqualTo(LocalDate.of(2027, 10, 11))
        assertThat(calendars.hijriYear(LocalDate.of(2027, 2, 8))).isEqualTo(1448)
    }

    @Test
    fun `lunar new year table has the published dates`() {
        val expected = mapOf(
            2000 to LocalDate.of(2000, 2, 5), 2024 to LocalDate.of(2024, 2, 10), 2025 to LocalDate.of(2025, 1, 29),
            2026 to LocalDate.of(2026, 2, 17), 2027 to LocalDate.of(2027, 2, 6), 2028 to LocalDate.of(2028, 1, 26),
            2029 to LocalDate.of(2029, 2, 13), 2030 to LocalDate.of(2030, 2, 3), 2100 to LocalDate.of(2100, 2, 9),
        )
        for ((year, date) in expected) {
            assertThat(LunarNewYearDates.dateIn(year)).isEqualTo(date)
        }
        assertThat(LunarNewYearDates.dateIn(1999)).isNull()
        assertThat(LunarNewYearDates.dateIn(2101)).isNull()
    }

    @Test
    fun `lunar new year is always between 21 January and 21 February`() {
        for (year in 2000..2100) {
            val date = LunarNewYearDates.dateIn(year)!!
            assertWithMessage("$year").that(date.isBefore(LocalDate.of(year, 1, 21))).isFalse()
            assertWithMessage("$year").that(date.isAfter(LocalDate.of(year, 2, 21))).isFalse()
        }
    }

    @Test
    fun `lunar new year after the table is not guessed`() {
        assertThat(periods("CN", 2101, 2, 1).of(LocalRhythmKind.LUNAR_NEW_YEAR)).isNull()
    }

    // Nyepi region

    @Test
    fun `nyepi is kept in bali or when the region is unknown`() {
        val day = date(2027, 3, 7, 12)
        for (region in listOf("Bali", "Provinsi Bali", null)) {
            val periods = rhythm.periods("ID", listOf(LocalRhythmPlace("ID", region)), day, zone(), 14)
            assertWithMessage("region $region").that(periods.of(LocalRhythmKind.NYEPI)).isNotNull()
        }
    }

    @Test
    fun `nyepi is dropped outside bali`() {
        val day = date(2027, 3, 7, 12)
        for (region in listOf("Jakarta", "Special Region of Yogyakarta", "Balikpapan")) {
            val periods = rhythm.periods("ID", listOf(LocalRhythmPlace("ID", region)), day, zone(), 14)
            assertWithMessage("region $region").that(periods.of(LocalRhythmKind.NYEPI)).isNull()
            assertThat(periods.of(LocalRhythmKind.RAMADAN)).isNotNull()
        }
    }

    @Test
    fun `nyepi region comes from the first place that has one in the country`() {
        val day = date(2027, 3, 7, 12)
        fun hasNyepi(vararg places: LocalRhythmPlace) =
            rhythm.periods("ID", places.toList(), day, zone(), 14).of(LocalRhythmKind.NYEPI) != null

        val jakarta = LocalRhythmPlace("ID", "Jakarta")
        val bali = LocalRhythmPlace("ID", "Bali")
        assertThat(hasNyepi(LocalRhythmPlace("ID", " "), jakarta)).isFalse()
        assertThat(hasNyepi(jakarta, bali)).isFalse()
        assertThat(hasNyepi(bali, jakarta)).isTrue()
        // A location that lags behind a border crossing says nothing about the region.
        assertThat(hasNyepi(LocalRhythmPlace("MY", "Johor"))).isTrue()
        assertThat(hasNyepi(LocalRhythmPlace(null, "Jakarta"))).isFalse()
    }

    // Content

    @Test
    fun `short names drop the parenthetical`() {
        fun shortName(country: String, kind: LocalRhythmKind, month: Int, day: Int) =
            periods(country, 2027, month, day, lookahead = 31).of(kind)?.shortName
        assertThat(shortName("VN", LocalRhythmKind.LUNAR_NEW_YEAR, 2, 1)).isEqualTo("Tết")
        assertThat(shortName("ID", LocalRhythmKind.NYEPI, 3, 1)).isEqualTo("Nyepi")
        assertThat(shortName("SG", LocalRhythmKind.LUNAR_NEW_YEAR, 2, 1)).isEqualTo("Lunar New Year")
    }

    @Test
    fun `every period has short guidance`() {
        val countries = listOf(
            "SA", "AE", "TR", "ID", "MY", "MA", "EG", "IL", "TH", "JP", "IT", "ES", "MX", "PH", "MT", "CN", "VN", "KR",
            "TW", "HK", "IN", "NP", "SG",
        )
        val days = listOf(1 to 15, 2 to 10, 3 to 10, 4 to 10, 5 to 20, 8 to 10, 9 to 15, 10 to 20, 11 to 5)
        val kindsSeen = mutableSetOf<LocalRhythmKind>()
        for (country in countries) {
            for ((month, day) in days) {
                for (period in periods(country, 2027, month, day, lookahead = 31) +
                    periods(country, 2026, month, day, lookahead = 31)) {
                    kindsSeen += period.kind
                    assertThat(period.name).isNotEmpty()
                    assertThat(period.guidance).isNotEmpty()
                    assertWithMessage("${period.kind} in $country is too long")
                        .that(period.guidance.length).isAtMost(330)
                    assertWithMessage("${period.kind} in $country").that(period.guidance).endsWith(".")
                    assertThat(period.start).isLessThan(period.end)
                    assertThat(period.countryCode).isEqualTo(country)
                }
            }
        }
        assertThat(kindsSeen).isEqualTo(LocalRhythmKind.entries.toSet())
    }
}
