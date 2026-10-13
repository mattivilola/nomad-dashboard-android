package com.iloapps.nomaddashboard.core.travel.rhythm

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// Offline "local rhythm" calendar: cultural and religious periods that visibly change daily life for a
// visitor (opening hours, closures, traffic, etiquette). Everything is computed or looked up on the
// device; there is no network access.
//
// A country is only listed for a period when a visitor would notice it. Unverified countries and
// years are deliberately absent, and the calendar returns nothing for them instead of guessing.
//
// Ported from the macOS app (NomadCore LocalRhythm.swift, verified 2026-10); the rule tables and the
// guidance wording must stay identical to it. The Umm al-Qura and Hebrew calendars are ICU's
// (`LunisolarCalendars`) where macOS uses Foundation's, which is ICU underneath; Lunar New Year is a table
// (`LunarNewYearDates`) because ICU4J's astronomical Chinese calendar is a day off in 2027 and 2030.
//
// Date sources
//
//  - Ramadan, Eid al-Fitr, Eid al-Adha: the Umm al-Qura calendar. Ramadan is all of month 9, Eid al-Fitr
//    is Shawwal 1-3, Eid al-Adha is Dhu al-Hijjah 10-13. Cross-checked against the AlAdhan.com Umm al-Qura
//    tables (https://aladhan.com/hijri-gregorian-calendar/9/1447 and the 10/12 month pages for 1447 and
//    1448). Actual dates follow local moon sighting and differ by a day between countries (for example
//    Indonesia and Singapore have Eid al-Adha on 17 May 2027, Umm al-Qura gives 16 May), so the
//    confidence is `EXPECTED`.
//  - Lunar New Year: the Chinese calendar, month 1 day 1 (not a leap month), as the `LunarNewYearDates`
//    table for 2000-2100. Cross-checked against the
//    Hong Kong and Singapore public holiday lists: 17 Feb 2026 and 6 Feb 2027
//    (https://www.gov.hk/en/about/abouthk/holiday/2026.htm, https://www.mom.gov.sg/employment-practices/public-holidays).
//    Holiday spans are kept to the statutory core, as offsets from the first day of the new year:
//      CN  eve to day 7 (8 days): State Council notice on 2026 holidays, Spring Festival 15-23 Feb 2026 (9 days,
//          LNY 17 Feb, i.e. day -2 to day +6); the 2025 holiday was 28 Jan - 4 Feb (eve to day +6) as well.
//      TW  eve-1 to day 3 (5 days): Executive Yuan rule, the 2026 break was 14-22 Feb (9 days with weekends
//          and make-up days; Taiwan News / CNA, 2025).
//      VN  eve plus the first 4 days (5 days): Labour Code 2019, Article 112 (Tet is 5 days: 1 day at the
//          end of the old year and 4 days at the start of the new one).
//      KR  eve, day 1, day 2 (3 days): Seollal public holiday (substitute holidays are not modelled).
//      SG  days 1-2 (2 days): MOM public holidays, 17-18 Feb 2026 and 6-7 Feb 2027.
//      MY  days 1-2 (2 days): federal public holidays, 17-18 Feb 2026.
//      HK  days 1-3 (3 days): gov.hk general holidays, 17-19 Feb 2026 (2027 swaps day 2 for day 4 because
//          day 2 is a Sunday; only the conservative first three days are modelled).
//      MO  days 1-3 (3 days): the three obligatory Lunar New Year holidays under Macao labour law.
//  - Holy Week: Western Easter by the Anonymous Gregorian (Meeus/Jones/Butcher) computus, Palm Sunday
//    to Easter Sunday. Easter 5 Apr 2026 and 28 Mar 2027 (Indonesian SKB 2027 lists Paskah on 28 Mar;
//    Philippine Proclamation 1006 lists Good Friday 3 Apr 2026).
//  - Yom Kippur: the Hebrew calendar, 10 Tishrei. 21 Sep 2026 and 11 Oct 2027 (publicholidays.com,
//    timeanddate.com/holidays/israel/yom-kippur). It begins at sunset on the previous evening.
//  - Songkran (13-15 Apr, TH), Golden Week (29 Apr - 5 May, JP) and Ferragosto (15 Aug, IT) are fixed dates.
//  - Nyepi (ID): the date follows the Balinese Saka calendar and is set by decree. Only years with an
//    official decree are listed: 19 Mar 2026 (SKB 3 Menteri No. 1497/2025, 2/2025, 5/2025, signed
//    19 Sep 2025; https://www.setneg.go.id/baca/index/inilah_skb_3_menteri_libur_nasional_dan_cuti_bersama_2026)
//    and 8 Mar 2027 (SKB 3 Menteri No. 1205/2026, 3/2026, 2/2026, signed 15 Sep 2026;
//    https://setneg.go.id/baca/index/inilah_skb_3_menteri_libur_nasional_dan_cuti_bersama_2027).
//    Secondary lists (Wikipedia citing news.com.au) give 9 Mar 2027 and 26 Mar 2028, 15 Mar 2029,
//    5 Mar 2030; the 2027 value contradicts the decree, so 2028 onwards is left out until a decree exists.
//  - Diwali: the main day (Lakshmi Puja) is a table. India: Drik Panchang
//    (https://www.drikpanchang.com/diwali/diwali-puja-calendar.html?year=2026 and following years) and
//    https://www.qppstudio.net/global-holidays-observances/northern-deepavali-diwali-amavasya.htm, with
//    2032 from https://www.shreekundli.com/diwali-puja-calendar/strand/2032 and AstroSage. The date can
//    differ by a day between regions (Drik Panchang for London lists 28 Oct 2027, Delhi 29 Oct), hence
//    `EXPECTED`. Nepal (Laxmi Puja, national holiday): 8 Nov 2026 per officeholidays.com/calendarific, 29 Oct
//    2027 per vedicmarga/officeholidays; the Nepal Panchang committee has not issued 2084 BS. Singapore:
//    MOM public holidays, 8 Nov 2026 and 28 Oct 2027. Malaysia: 8 Nov 2026 (federal holiday); the 2027
//    gazette marks Deepavali as provisional, so it is not listed.
//
// Country applicability
//
//  - Ramadan and both Eids: countries where the month is publicly observed and shapes opening hours.
//    Muslim-majority countries with secular daytime life (AZ, UZ, KZ, XK, AL, BA) are left out because a
//    visitor sees little change. The guidance differs by country: Saudi-style conservative (public eating
//    is discouraged or restricted), mixed (tourist areas keep serving), relaxed (UAE, TR) and Asia/Africa
//    (shorter hours, lively evenings).
//  - Holy Week: countries where Holy Thursday and/or Good Friday are public holidays or non-working days
//    and processions are widespread (Bloomberg Linea, 2025-26; Proclamation 1006 for PH): ES, PT, IT, MT,
//    MX, GT, CO, PE, EC, PH, CR, SV, VE, PY. Honduras, Nicaragua and the Dominican Republic are left out
//    until their rules are verified. Countries on the Orthodox calendar follow another Easter date.
//  - Lunar New Year: CN, HK, MO, TW, SG, MY, VN, KR (public holidays).
//  - Yom Kippur: IL only. Songkran: TH. Golden Week: JP. Ferragosto: IT. Nyepi: ID (Bali). Diwali: IN, NP
//    (as Tihar), SG, MY.

/** A kind of local cultural or religious period. Declaration order breaks ties between periods that start together. */
enum class LocalRhythmKind {
    RAMADAN,
    EID_AL_FITR,
    EID_AL_ADHA,
    LUNAR_NEW_YEAR,
    HOLY_WEEK,
    YOM_KIPPUR,
    SONGKRAN,
    GOLDEN_WEEK,
    NYEPI,
    DIWALI,
    FERRAGOSTO,
}

/** How reliable the date of a period is. */
enum class LocalRhythmDateConfidence {
    /** The same civil date every year. */
    FIXED,

    /** Computed from a calendar or taken from an official decree. */
    COMPUTED,

    /** Computed, but the actual date may shift by about a day (moon sighting, regional practice). */
    EXPECTED,
}

/** One period that changes daily life in a country. */
data class LocalRhythmPeriod(
    val kind: LocalRhythmKind,
    /** Name for an English-speaking traveller, e.g. "Ramadan", "Tết (Lunar New Year)". */
    val name: String,
    val countryCode: String,
    /** Start of the first day in the requested time zone. */
    val start: Instant,
    /** The last second of the last day in the requested time zone. */
    val end: Instant,
    val confidence: LocalRhythmDateConfidence,
    /** One or two short practical sentences for a visitor. */
    val guidance: String,
) {
    val id: String
        get() = "${kind.name.lowercase(Locale.ROOT)}.$countryCode.${start.epochSecond}"

    /** [name] without its explanatory parenthetical, for example "Tết", "Nyepi" or "Lunar New Year". */
    val shortName: String
        get() = name.substringBefore('(').trim()
}

/**
 * Where the traveller is, for the rules that depend on more than the country: [region] is the
 * province or state, and [countryCode] the country the location was resolved in (null when unknown).
 */
data class LocalRhythmPlace(
    val countryCode: String?,
    val region: String?,
)

class LocalRhythmCalendar(
    private val calendars: LunisolarCalendars,
) {
    /**
     * Periods that are active on [now] or start within [lookaheadDays], for one country, earliest first.
     *
     * Days are civil Gregorian days in [zone]: the same dates apply wherever the traveller is, and
     * `start` and `end` are the boundaries of those days in [zone]. Returns an empty list for a missing
     * or unknown country.
     */
    fun periods(
        countryCode: String?,
        now: Instant,
        zone: ZoneId,
        lookaheadDays: Int = DEFAULT_LOOKAHEAD_DAYS,
    ): List<LocalRhythmPeriod> {
        val country = countryCode?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        val horizon = now.atZone(zone).plusDays(maxOf(0, lookaheadDays).toLong()).toInstant()
        val firstYear = now.atZone(zone).year - 1
        val lastYear = horizon.atZone(zone).year + 1

        return RhythmRules(calendars).spans(country, firstYear..lastYear)
            .mapNotNull { span ->
                val start = span.first.atStartOfDay(zone).toInstant()
                val endExclusive = span.first.plusDays(span.dayCount.toLong()).atStartOfDay(zone).toInstant()
                if (endExclusive <= now || start > horizon) {
                    null
                } else {
                    LocalRhythmPeriod(
                        kind = span.kind,
                        name = span.name,
                        countryCode = country,
                        start = start,
                        end = endExclusive.minusSeconds(1),
                        confidence = span.confidence,
                        guidance = span.guidance,
                    )
                }
            }
            .sortedWith(compareBy<LocalRhythmPeriod> { it.start }.thenBy { it.kind.ordinal })
    }

    /**
     * [periods] for the traveller's own location.
     *
     * Nyepi only applies to Bali, not to the rest of Indonesia, so it is dropped when the first place
     * (device, else IP) that names a region says a region other than Bali. It is kept when the region is
     * unknown, as its title and guidance say "Bali" themselves. A place that belongs to another country
     * than [countryCode] (a refresh that lags behind a border crossing) says nothing about the region.
     */
    fun periods(
        countryCode: String?,
        places: List<LocalRhythmPlace>,
        now: Instant,
        zone: ZoneId,
        lookaheadDays: Int = DEFAULT_LOOKAHEAD_DAYS,
    ): List<LocalRhythmPeriod> {
        val all = periods(countryCode, now, zone, lookaheadDays)
        val region = if (all.any { it.kind == LocalRhythmKind.NYEPI }) regionName(places, countryCode) else null
        return if (region != null && !region.isBali()) {
            all.filter { it.kind != LocalRhythmKind.NYEPI }
        } else {
            all
        }
    }

    /** The region (province or state) of the first place that has one, in [countryCode]. */
    private fun regionName(places: List<LocalRhythmPlace>, countryCode: String?): String? {
        val country = countryCode?.trim()?.uppercase(Locale.ROOT)
        return places.asSequence()
            .filter { place ->
                val placeCountry = place.countryCode?.trim()?.uppercase(Locale.ROOT)
                placeCountry == null || placeCountry.isEmpty() || placeCountry == country
            }
            .mapNotNull { it.region?.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    /** Whether "Bali" is one of the words ("Bali", "Provinsi Bali"), but not part of another place such as Balikpapan. */
    private fun String.isBali(): Boolean =
        split(NON_LETTERS).any { word ->
            word.removeDiacritics().equals("bali", ignoreCase = true)
        }

    private fun String.removeDiacritics(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

    companion object {
        const val DEFAULT_LOOKAHEAD_DAYS = 14
        private val NON_LETTERS = Regex("[^\\p{L}]+")
        private val COMBINING_MARKS = Regex("\\p{M}+")
    }
}
