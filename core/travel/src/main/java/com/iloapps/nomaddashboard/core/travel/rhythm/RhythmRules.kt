package com.iloapps.nomaddashboard.core.travel.rhythm

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One run of consecutive civil days of a [LocalRhythmKind] in a country. */
internal data class RhythmSpan(
    val kind: LocalRhythmKind,
    val name: String,
    val first: LocalDate,
    val dayCount: Int,
    val confidence: LocalRhythmDateConfidence,
    val guidance: String,
)

private data class MonthDay(
    val month: Int,
    val day: Int,
)

internal class RhythmRules(
    private val calendars: LunisolarCalendars,
) {
    fun spans(country: String, years: IntRange): List<RhythmSpan> {
        val spans = mutableListOf<RhythmSpan>()

        ramadanStyles[country]?.let { spans += islamicSpans(it, years) }
        lunarNewYearRules[country]?.let { spans += lunarNewYearSpans(it, years) }
        if (country in holyWeekCountries) {
            spans += holyWeekSpans(country, years)
        }
        diwaliRules[country]?.let { spans += diwaliSpans(it, years) }

        for (year in years) {
            when (country) {
                "IL" -> calendars.hebrewToGregorian(year + HEBREW_YEAR_OFFSET, month = 1, day = 10)?.let { day ->
                    spans += RhythmSpan(
                        kind = LocalRhythmKind.YOM_KIPPUR,
                        name = "Yom Kippur",
                        first = day,
                        dayCount = 1,
                        confidence = LocalRhythmDateConfidence.COMPUTED,
                        guidance = YOM_KIPPUR_GUIDANCE,
                    )
                }

                "TH" -> spans += RhythmSpan(
                    kind = LocalRhythmKind.SONGKRAN,
                    name = "Songkran (Thai New Year)",
                    first = LocalDate.of(year, 4, 13),
                    dayCount = 3,
                    confidence = LocalRhythmDateConfidence.FIXED,
                    guidance = SONGKRAN_GUIDANCE,
                )

                "JP" -> spans += RhythmSpan(
                    kind = LocalRhythmKind.GOLDEN_WEEK,
                    name = "Golden Week",
                    first = LocalDate.of(year, 4, 29),
                    dayCount = 7,
                    confidence = LocalRhythmDateConfidence.FIXED,
                    guidance = GOLDEN_WEEK_GUIDANCE,
                )

                "IT" -> spans += RhythmSpan(
                    kind = LocalRhythmKind.FERRAGOSTO,
                    name = "Ferragosto",
                    first = LocalDate.of(year, 8, 15),
                    dayCount = 1,
                    confidence = LocalRhythmDateConfidence.FIXED,
                    guidance = FERRAGOSTO_GUIDANCE,
                )

                "ID" -> nyepiDates[year]?.let { date ->
                    spans += RhythmSpan(
                        kind = LocalRhythmKind.NYEPI,
                        name = "Nyepi (Bali Day of Silence)",
                        first = LocalDate.of(year, date.month, date.day),
                        dayCount = 1,
                        confidence = LocalRhythmDateConfidence.COMPUTED,
                        guidance = NYEPI_GUIDANCE,
                    )
                }
            }
        }

        return spans
    }

    private fun islamicSpans(style: RamadanStyle, years: IntRange): List<RhythmSpan> {
        val hijriYears = calendars.hijriYear(LocalDate.of(years.first, 1, 1))..
            calendars.hijriYear(LocalDate.of(years.last, 12, 31))

        val spans = mutableListOf<RhythmSpan>()
        for (hijriYear in hijriYears) {
            val first = calendars.ummAlQuraToGregorian(hijriYear, month = 9, day = 1)
            val shawwal = calendars.ummAlQuraToGregorian(hijriYear, month = 10, day = 1)
            if (first != null && shawwal != null) {
                spans += RhythmSpan(
                    kind = LocalRhythmKind.RAMADAN,
                    name = "Ramadan",
                    first = first,
                    dayCount = ChronoUnit.DAYS.between(first, shawwal).toInt(),
                    confidence = LocalRhythmDateConfidence.EXPECTED,
                    guidance = ramadanGuidance(style),
                )
                spans += RhythmSpan(
                    kind = LocalRhythmKind.EID_AL_FITR,
                    name = "Eid al-Fitr",
                    first = shawwal,
                    dayCount = 3,
                    confidence = LocalRhythmDateConfidence.EXPECTED,
                    guidance = EID_AL_FITR_GUIDANCE,
                )
            }
            calendars.ummAlQuraToGregorian(hijriYear, month = 12, day = 10)?.let { adha ->
                spans += RhythmSpan(
                    kind = LocalRhythmKind.EID_AL_ADHA,
                    name = "Eid al-Adha",
                    first = adha,
                    dayCount = 4,
                    confidence = LocalRhythmDateConfidence.EXPECTED,
                    guidance = EID_AL_ADHA_GUIDANCE,
                )
            }
        }
        return spans
    }

    private fun lunarNewYearSpans(rule: LunarNewYearRule, years: IntRange): List<RhythmSpan> =
        years.mapNotNull { year ->
            LunarNewYearDates.dateIn(year)?.let { newYear ->
                RhythmSpan(
                    kind = LocalRhythmKind.LUNAR_NEW_YEAR,
                    name = rule.name,
                    first = newYear.plusDays(rule.firstDayOffset.toLong()),
                    dayCount = rule.dayCount,
                    confidence = LocalRhythmDateConfidence.COMPUTED,
                    guidance = rule.guidance,
                )
            }
        }

    private fun holyWeekSpans(country: String, years: IntRange): List<RhythmSpan> =
        years.map { year ->
            RhythmSpan(
                kind = LocalRhythmKind.HOLY_WEEK,
                name = holyWeekName(country),
                first = westernEaster(year).minusDays(7),
                dayCount = 8,
                confidence = LocalRhythmDateConfidence.COMPUTED,
                guidance = holyWeekGuidance(country),
            )
        }

    private fun diwaliSpans(rule: DiwaliRule, years: IntRange): List<RhythmSpan> =
        years.mapNotNull { year ->
            rule.dates[year]?.let { date ->
                RhythmSpan(
                    kind = LocalRhythmKind.DIWALI,
                    name = rule.name,
                    first = LocalDate.of(year, date.month, date.day).plusDays(rule.firstDayOffset.toLong()),
                    dayCount = rule.dayCount,
                    confidence = rule.confidence,
                    guidance = rule.guidance,
                )
            }
        }

    private enum class RamadanStyle {
        /** Daytime eating is discouraged or restricted and many venues close. */
        CONSERVATIVE,

        /** Many local venues close by day, but tourist areas and hotels keep serving. */
        MIXED,

        /** Daily life carries on; hours shrink and evenings are busy. */
        RELAXED,

        /** Shorter hours, lively evenings, discretion outside tourist areas. */
        REGIONAL,
    }

    private class LunarNewYearRule(
        val name: String,
        /** Offset in days from the first day of the new year to the first holiday day (-1 is the eve). */
        val firstDayOffset: Int,
        val dayCount: Int,
        val guidance: String,
    )

    private class DiwaliRule(
        val name: String,
        /** Offset in days from the main day to the first day of the period. */
        val firstDayOffset: Int,
        val dayCount: Int,
        val confidence: LocalRhythmDateConfidence,
        val guidance: String,
        val dates: Map<Int, MonthDay>,
    )

    private companion object {
        /** The Hebrew year that starts in the autumn of Gregorian year `y` is `y + 3761`. */
        const val HEBREW_YEAR_OFFSET = 3761

        // Ramadan and Eids

        val ramadanStyles: Map<String, RamadanStyle> = mapOf(
            "SA" to RamadanStyle.CONSERVATIVE, "KW" to RamadanStyle.CONSERVATIVE, "QA" to RamadanStyle.CONSERVATIVE,
            "BH" to RamadanStyle.CONSERVATIVE, "OM" to RamadanStyle.CONSERVATIVE, "IR" to RamadanStyle.CONSERVATIVE,
            "IQ" to RamadanStyle.CONSERVATIVE, "LY" to RamadanStyle.CONSERVATIVE, "MR" to RamadanStyle.CONSERVATIVE,
            "PK" to RamadanStyle.CONSERVATIVE, "JO" to RamadanStyle.CONSERVATIVE,
            "MA" to RamadanStyle.MIXED, "DZ" to RamadanStyle.MIXED, "TN" to RamadanStyle.MIXED, "EG" to RamadanStyle.MIXED,
            "AE" to RamadanStyle.RELAXED, "TR" to RamadanStyle.RELAXED,
            "ID" to RamadanStyle.REGIONAL, "MY" to RamadanStyle.REGIONAL, "BN" to RamadanStyle.REGIONAL,
            "BD" to RamadanStyle.REGIONAL, "MV" to RamadanStyle.REGIONAL, "SN" to RamadanStyle.REGIONAL,
        )

        fun ramadanGuidance(style: RamadanStyle): String =
            when (style) {
                RamadanStyle.CONSERVATIVE ->
                    "Many cafés and restaurants close during daylight and shops keep shorter hours; traffic peaks just before sunset. Avoid eating, drinking or smoking in public during the day."

                RamadanStyle.MIXED ->
                    "Shops and offices keep shorter hours and traffic peaks just before sunset, while evenings are lively. Some cafés and restaurants close or screen their windows by day, though hotels and tourist areas mostly keep serving. Be discreet eating, drinking or smoking in public during the day."

                RamadanStyle.RELAXED ->
                    "Everyday life carries on and most restaurants and cafés in cities and tourist areas stay open, but office hours are shorter and traffic peaks before sunset. Be discreet about eating, drinking or smoking in public during the day, especially outside big cities."

                RamadanStyle.REGIONAL ->
                    "Expect shorter working hours and heavy traffic before sunset. Some local eateries close or screen their windows by day, while evening food stalls and markets are lively. Be discreet eating, drinking or smoking in public by day, especially outside tourist areas."
            }

        const val EID_AL_FITR_GUIDANCE =
            "Offices, banks and many shops close for several days, and cities empty as people travel to family. Bus, train and flight tickets sell out early, so book ahead and check opening hours; some places stay closed for a week or longer."

        const val EID_AL_ADHA_GUIDANCE =
            "Offices, banks and many shops close for several days while families gather and travel, so book transport early. Expect livestock markets in the days before and quiet streets on the day itself."

        // Lunar New Year

        const val REGIONAL_LUNAR_NEW_YEAR_GUIDANCE =
            "Offices and banks close and many small shops and restaurants shut for a few days, while malls and tourist areas mostly stay open. Transport is busy as people travel to family, so book ahead."

        val lunarNewYearRules: Map<String, LunarNewYearRule> = mapOf(
            "CN" to LunarNewYearRule(
                name = "Spring Festival (Lunar New Year)",
                firstDayOffset = -1,
                dayCount = 8,
                guidance = "The biggest travel period of the year: trains, flights and roads are packed, so book early. Many shops and restaurants close for a week or more, and fireworks are common on New Year's Eve and the first days where permitted.",
            ),
            "TW" to LunarNewYearRule("Lunar New Year", -2, 5, REGIONAL_LUNAR_NEW_YEAR_GUIDANCE),
            "HK" to LunarNewYearRule("Lunar New Year", 0, 3, REGIONAL_LUNAR_NEW_YEAR_GUIDANCE),
            "MO" to LunarNewYearRule("Lunar New Year", 0, 3, REGIONAL_LUNAR_NEW_YEAR_GUIDANCE),
            "SG" to LunarNewYearRule("Lunar New Year", 0, 2, REGIONAL_LUNAR_NEW_YEAR_GUIDANCE),
            "MY" to LunarNewYearRule("Lunar New Year", 0, 2, REGIONAL_LUNAR_NEW_YEAR_GUIDANCE),
            "VN" to LunarNewYearRule(
                name = "Tết (Lunar New Year)",
                firstDayOffset = -1,
                dayCount = 5,
                guidance = "Vietnam's biggest holiday: many shops, restaurants and services close for several days or a week, and trains, buses and flights sell out well ahead. Stock up on essentials and book transport early.",
            ),
            "KR" to LunarNewYearRule(
                name = "Seollal (Lunar New Year)",
                firstDayOffset = -1,
                dayCount = 3,
                guidance = "Offices, banks and many small shops and restaurants close for the three-day holiday, and trains and expressways are very busy. Convenience stores and large chains usually stay open.",
            ),
        )

        // Holy Week

        val holyWeekCountries: Set<String> = setOf(
            "ES", "PT", "IT", "MT", "MX", "GT", "CO", "PE", "EC", "PH", "CR", "SV", "VE", "PY",
        )

        fun holyWeekName(country: String): String =
            when (country) {
                "IT" -> "Settimana Santa (Holy Week)"
                "MT" -> "Holy Week"
                else -> "Semana Santa (Holy Week)"
            }

        fun holyWeekGuidance(country: String): String =
            when (country) {
                "IT" ->
                    "Processions and church services fill historic centres and close some streets. Shops and offices mostly stay open, but Easter Monday is a public holiday and trains and hotels are busy, so book ahead."

                "MT" ->
                    "Processions fill the streets of towns and villages, with the largest on Good Friday, a public holiday when shops and offices close. Expect road closures and book ahead for transport and accommodation."

                "MX" ->
                    "Holy Week and the week after are school holidays, so beaches, resorts and colonial towns are packed and prices rise. Processions and Passion plays close streets, and some offices and businesses close on Thursday and Friday."

                "PH" ->
                    "Maundy Thursday and Good Friday are national holidays: most offices, banks and many shops close and Metro Manila empties. Buses, ferries and flights sell out, so book early; processions and church services close some streets."

                else ->
                    "Processions fill the streets and often close roads for hours. Banks, offices and many shops close on Good Friday and often on Holy Thursday, and travel and accommodation are in high demand, so book ahead."
            }

        // Single-country periods

        const val YOM_KIPPUR_GUIDANCE =
            "Starts the evening before. For about 25 hours almost everything stops — shops, public transport and most traffic. Ben Gurion Airport closes too, so plan food, transport and cash ahead."

        const val SONGKRAN_GUIDANCE =
            "Thailand's New Year is a national holiday with street water fights: expect to get soaked outdoors, so protect phones and papers. Many shops and offices close, and buses, trains and flights are packed as people travel home."

        const val GOLDEN_WEEK_GUIDANCE =
            "Japan's peak domestic travel week: trains, flights and hotels are booked out and pricier, and popular sights are crowded. Most shops and restaurants stay open, but some offices, banks and small businesses close, so reserve transport and lodging early."

        const val FERRAGOSTO_GUIDANCE =
            "A public holiday on 15 August. Many shops, restaurants and small businesses close for a week or two around mid-August, especially in cities, while beaches and resorts are packed. Check opening hours and book ahead."

        const val NYEPI_GUIDANCE =
            "Bali's Day of Silence: the island shuts down for 24 hours from 6 am, including the airport. Stay at your accommodation and keep lights low."

        /** Nyepi dates with an official decree (SKB 3 Menteri). Never extrapolated. */
        val nyepiDates: Map<Int, MonthDay> = mapOf(
            2026 to MonthDay(3, 19),
            2027 to MonthDay(3, 8),
        )

        // Diwali

        /** Lakshmi Puja (the main day of Diwali) in India. */
        val indiaDiwaliDates: Map<Int, MonthDay> = mapOf(
            2026 to MonthDay(11, 8),
            2027 to MonthDay(10, 29),
            2028 to MonthDay(10, 17),
            2029 to MonthDay(11, 5),
            2030 to MonthDay(10, 26),
            2031 to MonthDay(11, 14),
            2032 to MonthDay(11, 2),
        )

        const val PUBLIC_HOLIDAY_DIWALI_GUIDANCE =
            "A public holiday: offices and banks close, while malls, restaurants and public transport mostly keep running. Hindu temples and Indian neighbourhoods are festive and crowded."

        val diwaliRules: Map<String, DiwaliRule> = mapOf(
            "IN" to DiwaliRule(
                name = "Diwali",
                firstDayOffset = -1,
                dayCount = 3,
                confidence = LocalRhythmDateConfidence.EXPECTED,
                guidance = "The festival of lights: banks, offices and many shops close, markets are packed with shoppers beforehand, and trains and flights are booked out. Expect fireworks and noise late into the night, and poor air quality in some cities.",
                dates = indiaDiwaliDates,
            ),
            "NP" to DiwaliRule(
                name = "Tihar (Festival of Lights)",
                firstDayOffset = 0,
                dayCount = 3,
                confidence = LocalRhythmDateConfidence.EXPECTED,
                guidance = "Offices and banks close for a few days, houses and streets are lit with lamps, and there are firecrackers and groups singing from door to door. Travel in and out of Kathmandu is busy.",
                dates = mapOf(
                    2026 to MonthDay(11, 8),
                    2027 to MonthDay(10, 29),
                ),
            ),
            "SG" to DiwaliRule(
                name = "Deepavali",
                firstDayOffset = 0,
                dayCount = 1,
                confidence = LocalRhythmDateConfidence.COMPUTED,
                guidance = PUBLIC_HOLIDAY_DIWALI_GUIDANCE,
                dates = mapOf(
                    2026 to MonthDay(11, 8),
                    2027 to MonthDay(10, 28),
                ),
            ),
            "MY" to DiwaliRule(
                name = "Deepavali",
                firstDayOffset = 0,
                dayCount = 1,
                confidence = LocalRhythmDateConfidence.COMPUTED,
                guidance = PUBLIC_HOLIDAY_DIWALI_GUIDANCE,
                dates = mapOf(
                    2026 to MonthDay(11, 8),
                ),
            ),
        )

        /** Western Easter Sunday by the Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
        fun westernEaster(year: Int): LocalDate {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = ((h + l - 7 * m + 114) % 31) + 1
            return LocalDate.of(year, month, day)
        }
    }
}
