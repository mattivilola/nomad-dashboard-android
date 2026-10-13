package com.iloapps.nomaddashboard.core.travel.environment

import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpDecision
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpSeverity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.iloapps.nomaddashboard.core.travel.rhythm.IcuLunisolarCalendars
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmCalendar
import com.iloapps.nomaddashboard.core.travel.rhythm.LocalRhythmPlace
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Test

/**
 * Reference (2027): Ramadan 8 Feb - 8 Mar (Umm al-Qura, 29 days), Nyepi 8 Mar (SKB 3 Menteri), Yom Kippur 11 Oct,
 * Songkran 13-15 Apr, Ferragosto 15 Aug, Spring Festival 5-12 Feb (Lunar New Year 6 Feb).
 */
class LocalRhythmHeadsUpTest {
    private val rhythm = LocalRhythmCalendar(IcuLunisolarCalendars())
    private val paris = ZoneId.of("Europe/Paris")
    private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    private fun date(year: Int, month: Int, day: Int, hour: Int = 12): Instant =
        LocalDateTime.of(year, month, day, hour, 0).atZone(paris).toInstant()

    private fun headsUp(places: List<LocalRhythmPlace>, country: String?, at: Instant): HeadsUpDecision? =
        LocalRhythmHeadsUp.evaluate(rhythm, country, places, at, paris) { dayFormat.format(it) }

    private fun headsUp(country: String?, year: Int, month: Int, day: Int): HeadsUpDecision? =
        headsUp(listOf(LocalRhythmPlace(country, null)), country, date(year, month, day))

    @Test
    fun `upcoming period is an info with the days until it starts`() {
        val decision = headsUp("SA", 2027, 2, 3)!!

        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(decision.title).isEqualTo("Ramadan starts in 5 days")
        assertThat(decision.detail).isEqualTo("Expected 8 Feb · may shift a day")
        assertThat(decision.statusText).isEqualTo("Ramadan")

        assertThat(headsUp("SA", 2027, 2, 7)!!.title).isEqualTo("Ramadan starts tomorrow")
    }

    @Test
    fun `upcoming detail is the start date when the date is not expected to shift`() {
        val songkran = headsUp("TH", 2027, 4, 1)!!
        assertThat(songkran.title).isEqualTo("Songkran starts in 12 days")
        assertThat(songkran.detail).isEqualTo("From 13 Apr")
        assertThat(songkran.severity).isEqualTo(HeadsUpSeverity.INFO)

        // A computed (not moon-sighted) date also reads "From".
        assertThat(headsUp("IL", 2027, 10, 8)!!.detail).isEqualTo("From 11 Oct")
    }

    @Test
    fun `active period counts the days and uses the first sentence of the guidance`() {
        val decision = headsUp("SA", 2027, 2, 19)!!

        assertThat(decision.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(decision.title).isEqualTo("Ramadan · day 12 of 29")
        assertThat(decision.statusText).isEqualTo("Ramadan")
        assertThat(decision.detail).startsWith("Many cafés and restaurants close during")
        assertThat(decision.detail).endsWith("…")
        assertThat(decision.detail!!.length).isAtMost(44)

        assertThat(headsUp("SA", 2027, 2, 8)!!.title).isEqualTo("Ramadan · day 1 of 29")
        assertThat(headsUp("SA", 2027, 3, 8)!!.title).isEqualTo("Ramadan · day 29 of 29")
        assertThat(headsUp("SA", 2027, 3, 9)!!.title).isEqualTo("Eid al-Fitr · until 11 Mar")
    }

    @Test
    fun `short periods name their last day and single days say today`() {
        val songkran = headsUp("TH", 2027, 4, 14)!!
        assertThat(songkran.title).isEqualTo("Songkran · until 15 Apr")
        assertThat(songkran.detail).isEqualTo("Thailand's New Year is a national holiday w…")
        assertThat(songkran.statusText).isEqualTo("Songkran")

        assertThat(headsUp("IT", 2027, 8, 15)!!.title).isEqualTo("Ferragosto today")
    }

    @Test
    fun `an active period outranks an upcoming one`() {
        // Ramadan is on its 28th day and Eid al-Fitr starts in two days.
        assertThat(headsUp("SA", 2027, 3, 7)!!.title).isEqualTo("Ramadan · day 28 of 29")
        // Without an active period the soonest upcoming one is reported.
        assertThat(headsUp("TH", 2027, 4, 1)!!.title).isEqualTo("Songkran starts in 12 days")
    }

    @Test
    fun `the window is fourteen days`() {
        assertThat(headsUp("SA", 2027, 1, 25)!!.title).isEqualTo("Ramadan starts in 14 days")
        assertThat(headsUp("SA", 2027, 1, 24)).isNull()
        assertThat(headsUp("SA", 2027, 3, 10)).isNotNull() // Eid al-Fitr, 9-11 Mar
        assertThat(headsUp("SA", 2027, 3, 12)).isNull()
    }

    @Test
    fun `no country or an uncovered country produces nothing`() {
        assertThat(headsUp(null, 2027, 2, 19)).isNull()
        assertThat(headsUp("FR", 2027, 2, 19)).isNull()
    }

    @Test
    fun `full shutdowns are a caution the day before and on the day`() {
        val tomorrow = headsUp("IL", 2027, 10, 10)!!
        assertThat(tomorrow.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(tomorrow.title).isEqualTo("Yom Kippur tomorrow: country stops for 25 h")
        assertThat(tomorrow.detail).isEqualTo("From 11 Oct")
        assertThat(tomorrow.statusText).isEqualTo("Yom Kippur")

        val today = headsUp("IL", 2027, 10, 11)!!
        assertThat(today.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(today.title).isEqualTo("Yom Kippur today: country stops for 25 h")
        assertThat(today.detail).isEqualTo("Starts the evening before")

        // Two days ahead it is an ordinary heads-up, and it is gone the day after.
        val early = headsUp("IL", 2027, 10, 9)!!
        assertThat(early.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(early.title).isEqualTo("Yom Kippur starts in 2 days")
        assertThat(headsUp("IL", 2027, 10, 12)).isNull()
    }

    private fun indonesia(region: String?, vararg more: LocalRhythmPlace) =
        listOf(LocalRhythmPlace("ID", region)) + more

    @Test
    fun `nyepi is a caution when located in bali or when the region is unknown`() {
        for (places in listOf(indonesia("Bali"), indonesia("Provinsi Bali"), indonesia(null))) {
            val tomorrow = headsUp(places, "ID", date(2027, 3, 7))!!
            assertThat(tomorrow.severity).isEqualTo(HeadsUpSeverity.CAUTION)
            assertThat(tomorrow.title).isEqualTo("Nyepi tomorrow: Bali shuts down for 24 h")
            assertThat(tomorrow.statusText).isEqualTo("Nyepi")

            val today = headsUp(places, "ID", date(2027, 3, 8))!!
            assertThat(today.severity).isEqualTo(HeadsUpSeverity.CAUTION)
            assertThat(today.title).isEqualTo("Nyepi today: Bali shuts down for 24 h")
        }
    }

    @Test
    fun `nyepi outranks a running ramadan in indonesia`() {
        // Ramadan is on its last day on Nyepi, but the shutdown is what the traveller must plan for.
        assertThat(headsUp(indonesia("Bali"), "ID", date(2027, 3, 8))!!.title).startsWith("Nyepi")
        assertThat(headsUp(indonesia("Bali"), "ID", date(2027, 3, 6))!!.title).isEqualTo("Ramadan · day 27 of 29")
    }

    @Test
    fun `nyepi is dropped outside bali`() {
        for (region in listOf("Jakarta", "Special Region of Yogyakarta", "Balikpapan")) {
            // Only Ramadan (on its 28th day) is left.
            val decision = headsUp(indonesia(region), "ID", date(2027, 3, 7))!!
            assertThat(decision.title).isEqualTo("Ramadan · day 28 of 29")
            assertThat(decision.severity).isEqualTo(HeadsUpSeverity.INFO)
        }
    }

    @Test
    fun `nyepi region comes from the device then the ip location`() {
        val day = date(2027, 3, 7)
        val bali = LocalRhythmPlace("ID", "Bali")
        val jakarta = LocalRhythmPlace("ID", "Jakarta")
        // IP region alone decides when the device has none (or a blank region).
        assertThat(headsUp(listOf(bali), "ID", day)!!.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(headsUp(listOf(jakarta), "ID", day)!!.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(headsUp(listOf(LocalRhythmPlace("ID", " "), jakarta), "ID", day)!!.severity).isEqualTo(HeadsUpSeverity.INFO)
        // The device location wins over the IP location.
        assertThat(headsUp(listOf(jakarta, bali), "ID", day)!!.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(headsUp(listOf(bali, jakarta), "ID", day)!!.severity).isEqualTo(HeadsUpSeverity.CAUTION)
    }

    @Test
    fun `texts stay compact for every country and period`() {
        val countries = listOf(
            "SA", "KW", "QA", "BH", "OM", "IR", "IQ", "LY", "MR", "PK", "JO", "MA", "DZ", "TN", "EG", "AE", "TR", "ID", "MY", "BN",
            "BD", "MV", "SN", "CN", "TW", "HK", "MO", "SG", "VN", "KR", "ES", "PT", "IT", "MT", "MX", "GT", "CO", "PE", "EC", "PH",
            "CR", "SV", "VE", "PY", "IL", "TH", "JP", "IN", "NP",
        )
        val titles = mutableSetOf<String>()
        for (country in countries) {
            // A weekly sweep with a two-week window sees every period of the year.
            for (week in 0 until 53) {
                val day = LocalDate.of(2027, 1, 1).plusDays(week * 7L)
                val decision = headsUp(country, day.year, day.monthValue, day.dayOfMonth) ?: continue
                titles += decision.title
                assertWithMessage("$country: ${decision.statusText}")
                    .that(decision.statusText.length in 1..12).isTrue()
                assertWithMessage("$country: ${decision.title}").that(decision.title.length).isAtMost(44)
                assertWithMessage("$country: ${decision.detail}").that(decision.detail!!.length).isAtMost(44)
            }
        }
        assertThat(titles).isNotEmpty()
    }

    @Test
    fun `long period names fall back to a compact status text`() {
        // "Spring Festival", "Lunar New Year" and "Settimana Santa" are longer than a status surface allows.
        assertThat(headsUp("CN", 2027, 1, 30)!!.title).isEqualTo("Spring Festival starts in 6 days")
        assertThat(headsUp("CN", 2027, 1, 30)!!.statusText).isEqualTo("New Year")
        assertThat(headsUp("HK", 2027, 1, 30)!!.statusText).isEqualTo("New Year")
        assertThat(headsUp("IT", 2027, 3, 20)!!.statusText).isEqualTo("Holy Week")
        assertThat(headsUp("VN", 2027, 1, 30)!!.statusText).isEqualTo("Tết")
    }
}
