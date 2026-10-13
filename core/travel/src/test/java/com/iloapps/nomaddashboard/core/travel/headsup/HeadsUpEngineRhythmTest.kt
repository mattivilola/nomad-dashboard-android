package com.iloapps.nomaddashboard.core.travel.headsup

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import java.time.LocalDate
import org.junit.Test

/**
 * The local-rhythm heads-up through the engine (macOS DashboardInsightEngineTests, local rhythm). The
 * decision rules themselves are covered by `LocalRhythmHeadsUpTest`.
 *
 * Reference (2027): Ramadan 8 Feb - 8 Mar (Umm al-Qura, 29 days), Nyepi 8 Mar (SKB 3 Menteri), Yom Kippur 11 Oct,
 * Songkran 13-15 Apr, Ferragosto 15 Aug, Spring Festival 5-12 Feb (Lunar New Year 6 Feb).
 */
class HeadsUpEngineRhythmTest {
    private fun rhythmItem(travelContext: TravelContextSnapshot, year: Int, month: Int, day: Int): HeadsUpItem? =
        insights(snapshot(travelContext = travelContext), at = parisInstant(year, month, day, 12, 0))
            .item(HeadsUpKind.LOCAL_RHYTHM)

    /** The local-rhythm heads-up for a device in [country] at noon on the given Paris date. */
    private fun rhythmItem(country: String, year: Int, month: Int, day: Int): HeadsUpItem? =
        rhythmItem(rhythmLocation(country), year, month, day)

    @Test
    fun `an upcoming period is an info for the local info card`() {
        val item = rhythmItem("SA", 2027, 2, 3)!!

        assertThat(item.kind).isEqualTo(HeadsUpKind.LOCAL_RHYTHM)
        assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(item.title).isEqualTo("Ramadan starts in 5 days")
        assertThat(item.detail).isEqualTo("Expected 8 Feb · may shift a day")
        assertThat(item.statusText).isEqualTo("Ramadan")
        assertThat(item.relatedCard).isEqualTo(DashboardCardId.LOCAL_INFO)

        assertThat(rhythmItem("SA", 2027, 2, 7)!!.title).isEqualTo("Ramadan starts tomorrow")
    }

    @Test
    fun `the detail is the start date when the date is not expected to shift`() {
        val songkran = rhythmItem("TH", 2027, 4, 1)!!
        assertThat(songkran.title).isEqualTo("Songkran starts in 12 days")
        assertThat(songkran.detail).isEqualTo("From 13 Apr")
        assertThat(songkran.severity).isEqualTo(HeadsUpSeverity.INFO)

        assertThat(rhythmItem("IL", 2027, 10, 8)!!.detail).isEqualTo("From 11 Oct")
    }

    @Test
    fun `an active period counts the days and uses the first sentence of the guidance`() {
        val item = rhythmItem("SA", 2027, 2, 19)!!

        assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(item.title).isEqualTo("Ramadan · day 12 of 29")
        assertThat(item.statusText).isEqualTo("Ramadan")
        assertThat(item.detail).startsWith("Many cafés and restaurants close during")
        assertThat(item.detail).endsWith("…")
        assertThat(item.detail!!.length).isAtMost(HeadsUpText.DETAIL_CHARACTER_LIMIT)

        assertThat(rhythmItem("SA", 2027, 2, 8)!!.title).isEqualTo("Ramadan · day 1 of 29")
        assertThat(rhythmItem("SA", 2027, 3, 8)!!.title).isEqualTo("Ramadan · day 29 of 29")
        assertThat(rhythmItem("SA", 2027, 3, 9)!!.title).isEqualTo("Eid al-Fitr · until 11 Mar")
    }

    @Test
    fun `short periods name their last day and single days say today`() {
        val songkran = rhythmItem("TH", 2027, 4, 14)!!
        assertThat(songkran.title).isEqualTo("Songkran · until 15 Apr")
        assertThat(songkran.detail).isEqualTo("Thailand's New Year is a national holiday w…")
        assertThat(songkran.statusText).isEqualTo("Songkran")

        assertThat(rhythmItem("IT", 2027, 8, 15)!!.title).isEqualTo("Ferragosto today")
    }

    @Test
    fun `an active period outranks an upcoming one`() {
        // Ramadan is on its 28th day and Eid al-Fitr starts in two days.
        assertThat(rhythmItem("SA", 2027, 3, 7)!!.title).isEqualTo("Ramadan · day 28 of 29")
        // Without an active period the soonest upcoming one is reported.
        assertThat(rhythmItem("TH", 2027, 4, 1)!!.title).isEqualTo("Songkran starts in 12 days")
    }

    @Test
    fun `the window is fourteen days`() {
        assertThat(rhythmItem("SA", 2027, 1, 25)!!.title).isEqualTo("Ramadan starts in 14 days")
        assertThat(rhythmItem("SA", 2027, 1, 24)).isNull()
        assertThat(rhythmItem("SA", 2027, 3, 10)).isNotNull() // Eid al-Fitr, 9-11 Mar
        assertThat(rhythmItem("SA", 2027, 3, 12)).isNull()
    }

    @Test
    fun `no country or an uncovered country produces nothing`() {
        assertThat(rhythmItem(TravelContextSnapshot(), 2027, 2, 19)).isNull()
        assertThat(rhythmItem("FR", 2027, 2, 19)).isNull()
    }

    @Test
    fun `the country falls back to the IP location and local info`() {
        val ip = TravelContextSnapshot(countryCode = "SA")
        assertThat(rhythmItem(ip, 2027, 2, 19)!!.title).isEqualTo("Ramadan · day 12 of 29")

        // The device location wins over the IP location.
        val both = TravelContextSnapshot(deviceCountryCode = "SA", countryCode = "TH")
        assertThat(rhythmItem(both, 2027, 2, 19)!!.title).isEqualTo("Ramadan · day 12 of 29")

        val fromLocalInfo = insights(
            snapshot(localInfo = localInfo(countryCode = "SA")),
            at = parisInstant(2027, 2, 19, 12, 0),
        ).item(HeadsUpKind.LOCAL_RHYTHM)
        assertThat(fromLocalInfo!!.title).isEqualTo("Ramadan · day 12 of 29")
    }

    @Test
    fun `full shutdowns are a caution the day before and on the day`() {
        val tomorrow = rhythmItem("IL", 2027, 10, 10)!!
        assertThat(tomorrow.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(tomorrow.title).isEqualTo("Yom Kippur tomorrow: country stops for 25 h")
        assertThat(tomorrow.detail).isEqualTo("From 11 Oct")
        assertThat(tomorrow.statusText).isEqualTo("Yom Kippur")

        val today = rhythmItem("IL", 2027, 10, 11)!!
        assertThat(today.severity).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(today.title).isEqualTo("Yom Kippur today: country stops for 25 h")
        assertThat(today.detail).isEqualTo("Starts the evening before")

        // Two days ahead it is an ordinary heads-up, and it is gone the day after.
        val early = rhythmItem("IL", 2027, 10, 9)!!
        assertThat(early.severity).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(early.title).isEqualTo("Yom Kippur starts in 2 days")
        assertThat(rhythmItem("IL", 2027, 10, 12)).isNull()
    }

    @Test
    fun `Nyepi is a caution when located in Bali or when the region is unknown`() {
        for (device in listOf(rhythmLocation("ID", "Bali"), rhythmLocation("ID", "Provinsi Bali"), rhythmLocation("ID"))) {
            val tomorrow = rhythmItem(device, 2027, 3, 7)!!
            assertThat(tomorrow.severity).isEqualTo(HeadsUpSeverity.CAUTION)
            assertThat(tomorrow.title).isEqualTo("Nyepi tomorrow: Bali shuts down for 24 h")
            assertThat(tomorrow.statusText).isEqualTo("Nyepi")

            val today = rhythmItem(device, 2027, 3, 8)!!
            assertThat(today.severity).isEqualTo(HeadsUpSeverity.CAUTION)
            assertThat(today.title).isEqualTo("Nyepi today: Bali shuts down for 24 h")
        }
    }

    @Test
    fun `Nyepi outranks a running Ramadan in Indonesia`() {
        // Ramadan is on its last day on Nyepi, but the shutdown is what the traveller must plan for.
        val bali = rhythmLocation("ID", "Bali")

        assertThat(rhythmItem(bali, 2027, 3, 8)!!.title).startsWith("Nyepi")
        assertThat(rhythmItem(bali, 2027, 3, 6)!!.title).isEqualTo("Ramadan · day 27 of 29")
    }

    @Test
    fun `Nyepi is dropped outside Bali`() {
        for (region in listOf("Jakarta", "Special Region of Yogyakarta", "Balikpapan")) {
            // Only Ramadan (on its 28th day) is left.
            val item = rhythmItem(rhythmLocation("ID", region), 2027, 3, 7)!!
            assertThat(item.title).isEqualTo("Ramadan · day 28 of 29")
            assertThat(item.severity).isEqualTo(HeadsUpSeverity.INFO)
        }
    }

    @Test
    fun `the Nyepi region comes from the device then the IP location`() {
        fun severity(context: TravelContextSnapshot): HeadsUpSeverity = rhythmItem(context, 2027, 3, 7)!!.severity
        fun context(deviceRegion: String?, ipRegion: String?, deviceCountry: String? = "ID") = TravelContextSnapshot(
            deviceCountryCode = deviceCountry,
            deviceRegion = deviceRegion,
            countryCode = "ID",
            region = ipRegion,
        )

        // The IP region alone decides when the device has none (or a blank region).
        assertThat(severity(context(null, "Bali", deviceCountry = null))).isEqualTo(HeadsUpSeverity.CAUTION)
        assertThat(severity(context(null, "Jakarta", deviceCountry = null))).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(severity(context(" ", "Jakarta"))).isEqualTo(HeadsUpSeverity.INFO)
        // The device location wins over the IP location.
        assertThat(severity(context("Jakarta", "Bali"))).isEqualTo(HeadsUpSeverity.INFO)
        assertThat(severity(context("Bali", "Jakarta"))).isEqualTo(HeadsUpSeverity.CAUTION)
    }

    @Test
    fun `local rhythm ranks right after the public holiday`() {
        // A public holiday tomorrow, Ramadan day 1 and diesel nearby are all info.
        val tomorrow = LocalDate.of(2027, 2, 9)
        val snapshot = snapshot(
            localInfo = localInfo(HolidayPeriod("Armistice", tomorrow, tomorrow), countryCode = null),
            fuel = fuel(diesel = station(FuelType.DIESEL)),
            travelContext = rhythmLocation("SA"),
        )

        val kinds = insights(snapshot, at = parisInstant(2027, 2, 8, 12, 0)).kinds()

        assertThat(kinds).containsExactly(
            HeadsUpKind.PUBLIC_HOLIDAY,
            HeadsUpKind.LOCAL_RHYTHM,
            HeadsUpKind.FUEL_NEARBY,
        ).inOrder()
    }

    @Test
    fun `local rhythm texts stay compact for every country and period`() {
        val countries = listOf(
            "SA", "KW", "QA", "BH", "OM", "IR", "IQ", "LY", "MR", "PK", "JO", "MA", "DZ", "TN", "EG", "AE", "TR", "ID", "MY", "BN",
            "BD", "MV", "SN", "CN", "TW", "HK", "MO", "SG", "VN", "KR", "ES", "PT", "IT", "MT", "MX", "GT", "CO", "PE", "EC", "PH",
            "CR", "SV", "VE", "PY", "IL", "TH", "JP", "IN", "NP",
        )
        val titles = mutableSetOf<String>()
        for (country in countries) {
            // A weekly sweep with a two-week window sees every period of the year.
            for (week in 0 until 53) {
                val day = LocalDate.of(2027, 1, 1).plusWeeks(week.toLong())
                val item = rhythmItem(country, day.year, day.monthValue, day.dayOfMonth) ?: continue
                titles += item.title
                assertWithMessage("$country: ${item.statusText}").that(item.statusText).isNotEmpty()
                assertWithMessage("$country: ${item.statusText}").that(item.statusText.length)
                    .isAtMost(HeadsUpText.STATUS_TEXT_CHARACTER_LIMIT)
                assertWithMessage("$country: ${item.title}").that(item.title.length).isAtMost(44)
                assertWithMessage("$country: ${item.detail}").that(item.detail.orEmpty().length).isIn(1..44)
            }
        }
        assertThat(titles).isNotEmpty()
    }

    @Test
    fun `long period names fall back to a compact status text`() {
        // "Spring Festival", "Lunar New Year" and "Settimana Santa" are longer than a status surface allows.
        assertThat(rhythmItem("CN", 2027, 1, 30)!!.title).isEqualTo("Spring Festival starts in 6 days")
        assertThat(rhythmItem("CN", 2027, 1, 30)!!.statusText).isEqualTo("New Year")
        assertThat(rhythmItem("HK", 2027, 1, 30)!!.statusText).isEqualTo("New Year")
        assertThat(rhythmItem("IT", 2027, 3, 20)!!.statusText).isEqualTo("Holy Week")
        assertThat(rhythmItem("VN", 2027, 1, 30)!!.statusText).isEqualTo("Tết")
    }
}
