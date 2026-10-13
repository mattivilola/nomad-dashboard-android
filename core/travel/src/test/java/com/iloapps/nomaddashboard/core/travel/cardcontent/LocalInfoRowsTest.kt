package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.travel.cards.CardTone
import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.now
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.time
import java.time.LocalDate
import org.junit.Test

class LocalInfoRowsTest {
    private fun holiday(phase: LocalHolidayPhase, name: String, start: String, end: String = start) =
        LocalHolidayStatus(phase, HolidayPeriod(name, LocalDate.parse(start), LocalDate.parse(end)))

    private fun rows(snapshot: LocalInfoSnapshot, country: String? = snapshot.countryCode) =
        LocalInfoRows.of(snapshot, country, time, now)

    private fun ready(
        publicHoliday: LocalHolidayStatus? = null,
        schoolHoliday: LocalHolidayStatus? = null,
        prices: LocalPriceLevelSnapshot = LocalPriceLevelSnapshot(),
        countryCode: String? = "ES",
    ) = LocalInfoSnapshot(
        status = LocalInfoStatus.READY,
        countryCode = countryCode,
        publicHoliday = publicHoliday,
        schoolHoliday = schoolHoliday,
        localPriceLevel = prices,
    )

    private fun price(kind: LocalPriceIndicatorKind, value: String, detail: String) = LocalPriceIndicatorRow(
        kind = kind,
        value = value,
        detail = detail,
        precision = LocalPricePrecision.COUNTRY_FALLBACK,
        source = "Eurostat",
    )

    // Subtitle

    @Test
    fun `the subtitle says where the data applies`() {
        val snapshot = ready().copy(
            locality = "Valencia",
            region = "Valencian Community",
            countryName = "Spain",
            timezone = "Europe/Madrid",
        )

        assertThat(LocalInfoRows.subtitle(enabled = true, waitingForLocation = false, snapshot = snapshot))
            .isEqualTo("Valencia · Valencian Community, Spain · Europe/Madrid")
    }

    @Test
    fun `a region that repeats the town is left out`() {
        val snapshot = ready().copy(locality = "Madrid", region = "Madrid", countryName = "Spain")

        assertThat(LocalInfoRows.subtitle(enabled = true, waitingForLocation = false, snapshot = snapshot))
            .isEqualTo("Madrid · Spain")
    }

    @Test
    fun `the subtitle falls back to the country and then the current place`() {
        assertThat(LocalInfoRows.subtitle(true, false, ready().copy(countryName = "Spain"))).isEqualTo("Spain")
        assertThat(LocalInfoRows.subtitle(true, false, ready())).isEqualTo("Current place")
    }

    @Test
    fun `the subtitle explains a card that has no place yet`() {
        val off = LocalInfoSnapshot(status = LocalInfoStatus.OFF)

        assertThat(LocalInfoRows.subtitle(false, false, off)).isEqualTo("Location context, holidays, and price signals")
        assertThat(LocalInfoRows.subtitle(true, true, off)).isEqualTo("Checking device location before local context")
        assertThat(LocalInfoRows.subtitle(true, false, off)).isEqualTo("Off")
        assertThat(LocalInfoRows.subtitle(true, false, off.copy(status = LocalInfoStatus.LOCATION_REQUIRED)))
            .isEqualTo("Location required")
        assertThat(LocalInfoRows.subtitle(true, false, off.copy(status = LocalInfoStatus.CHECKING)))
            .isEqualTo("Looking up local context and holiday calendar")
    }

    // Emergency number

    @Test
    fun `the emergency number leads the rows`() {
        val result = rows(
            ready(
                publicHoliday = holiday(LocalHolidayPhase.NEXT, "Labour Day", "2026-11-01"),
                countryCode = "JP",
            ),
        )

        assertThat(result.first().id).isEqualTo(LocalInfoRows.EMERGENCY_ID)
        assertThat(result.first().title).isEqualTo("Emergency")
        assertThat(result.first().value).isEqualTo("110")
        assertThat(result.first().detail).isEqualTo("Ambulance/Fire 119")
        assertThat(result.first().isValueSelectable).isTrue()
    }

    @Test
    fun `a country with only a general number has no further detail`() {
        val row = rows(ready(countryCode = "FI")).first()

        assertThat(row.value).isEqualTo("112")
        assertThat(row.detail).isEmpty()
    }

    @Test
    fun `rural police numbers are part of the detail`() {
        assertThat(rows(ready(countryCode = "ES")).first().detail).isEqualTo("Guardia Civil 062")
        assertThat(rows(ready(countryCode = "MA")).first().detail).isEqualTo("Ambulance/Fire 15 · Gendarmerie 177")
    }

    @Test
    fun `the number follows the current country, not the local info country`() {
        val result = rows(ready(countryCode = "ES"), country = "JP")

        assertThat(result.first().value).isEqualTo("110")
    }

    @Test
    fun `an unlisted country shows no emergency row`() {
        assertThat(rows(ready(countryCode = "LA")).map { it.id }).doesNotContain(LocalInfoRows.EMERGENCY_ID)
        assertThat(rows(ready(countryCode = null)).map { it.id }).isEmpty()
    }

    @Test
    fun `no rows while the location is required`() {
        val snapshot = LocalInfoSnapshot(status = LocalInfoStatus.LOCATION_REQUIRED, countryCode = "ES")

        assertThat(rows(snapshot)).isEmpty()
    }

    // Holidays

    @Test
    fun `a holiday today is a busy day`() {
        val row = rows(ready(publicHoliday = holiday(LocalHolidayPhase.TODAY, "National Day", "2026-10-12")))
            .single { it.id == LocalInfoRows.PUBLIC_HOLIDAY_ID }

        assertThat(row.title).isEqualTo("Public Holiday")
        assertThat(row.detail).isEqualTo("National Day")
        assertThat(row.badge).isEqualTo(LocalInfoBadge("Busy Today", CardTone.CAUTION))
    }

    @Test
    fun `the next holiday says the date and how far away it is`() {
        val row = rows(ready(publicHoliday = holiday(LocalHolidayPhase.NEXT, "All Saints", "2026-10-24")))
            .single { it.id == LocalInfoRows.PUBLIC_HOLIDAY_ID }

        assertThat(row.value).isEqualTo("24 Oct · in 12 days")
        assertThat(row.detail).isEqualTo("All Saints")
        assertThat(row.badge).isNull()
    }

    @Test
    fun `a holiday the provider calls tomorrow's says tomorrow whatever the date`() {
        val row = rows(ready(publicHoliday = holiday(LocalHolidayPhase.TOMORROW, "Ascension Day", "2026-05-14")))
            .single { it.id == LocalInfoRows.PUBLIC_HOLIDAY_ID }

        assertThat(row.value).isEqualTo("14 May · tomorrow")
    }

    @Test
    fun `tomorrow's holiday says tomorrow`() {
        val row = rows(ready(publicHoliday = holiday(LocalHolidayPhase.TOMORROW, "Ascension Day", "2026-10-13")))
            .single { it.id == LocalInfoRows.PUBLIC_HOLIDAY_ID }

        assertThat(row.value).isEqualTo("13 Oct · tomorrow")
        assertThat(row.badge).isNull()
    }

    @Test
    fun `a school break that is on shows its dates and a busy period chip`() {
        val row = rows(
            ready(schoolHoliday = holiday(LocalHolidayPhase.ON_BREAK, "Spring Holidays", "2026-10-01", "2026-10-18")),
        ).single { it.id == LocalInfoRows.SCHOOL_HOLIDAY_ID }

        assertThat(row.title).isEqualTo("School Break")
        assertThat(row.value).isEqualTo("On break")
        assertThat(row.detail).isEqualTo("Spring Holidays · 1 Oct – 18 Oct")
        assertThat(row.badge).isEqualTo(LocalInfoBadge("Busy Period", CardTone.CAUTION))
    }

    @Test
    fun `the next school break shows its dates and its name`() {
        val row = rows(
            ready(schoolHoliday = holiday(LocalHolidayPhase.NEXT, "Summer Break", "2026-07-04", "2026-08-30")),
        ).single { it.id == LocalInfoRows.SCHOOL_HOLIDAY_ID }

        assertThat(row.value).isEqualTo("4 Jul – 30 Aug")
        assertThat(row.detail).isEqualTo("Summer Break")
        assertThat(row.badge).isNull()
    }

    @Test
    fun `a one day school break is one date`() {
        val row = rows(ready(schoolHoliday = holiday(LocalHolidayPhase.NEXT, "Study Day", "2026-11-02")))
            .single { it.id == LocalInfoRows.SCHOOL_HOLIDAY_ID }

        assertThat(row.value).isEqualTo("2 Nov")
    }

    // Prices

    private fun prices(vararg rows: LocalPriceIndicatorRow, band: LocalPriceSummaryBand? = null) =
        LocalPriceLevelSnapshot(summaryBand = band, rows = rows.toList())

    @Test
    fun `meals groceries and overall collapse into one prices vs eu row`() {
        val result = LocalInfoRows.priceRows(
            prices(
                price(LocalPriceIndicatorKind.MEAL_OUT, "Below Avg", "4% below EU average · Country fallback · 2024"),
                price(LocalPriceIndicatorKind.GROCERIES, "Below Avg", "4% below EU average · Country fallback · 2024"),
                price(LocalPriceIndicatorKind.OVERALL, "Moderate", "0% above EU average · Country fallback · 2024"),
            ),
        )

        assertThat(result).hasSize(1)
        assertThat(result.single().title).isEqualTo("Prices vs EU")
        assertThat(result.single().value).isEqualTo("Moderate")
        assertThat(result.single().detail).isEqualTo("Meals −4% · Groceries −4% · Overall ≈ EU")
    }

    @Test
    fun `differences above the average get a plus and a difference of one percent is about the average`() {
        val result = LocalInfoRows.priceRows(
            prices(
                price(LocalPriceIndicatorKind.MEAL_OUT, "Above Avg", "12% above EU average · Country fallback · 2024"),
                price(LocalPriceIndicatorKind.GROCERIES, "Moderate", "1% below EU average · Country fallback · 2024"),
                price(LocalPriceIndicatorKind.OVERALL, "Moderate", "1% above EU average · Country fallback · 2024"),
            ),
        ).single()

        assertThat(result.detail).isEqualTo("Meals +12% · Groceries ≈ EU · Overall ≈ EU")
    }

    @Test
    fun `without an overall row the first level word answers`() {
        val result = LocalInfoRows.priceRows(
            prices(
                price(LocalPriceIndicatorKind.MEAL_OUT, "Below Avg", "16% below EU average · Country fallback · 2024"),
                price(LocalPriceIndicatorKind.GROCERIES, "Moderate", "4% below EU average · Country fallback · 2024"),
            ),
        ).single()

        assertThat(result.value).isEqualTo("Below Avg")
        assertThat(result.detail).isEqualTo("Meals −16% · Groceries −4%")
    }

    @Test
    fun `the summary band answers when there is no overall row`() {
        val result = LocalInfoRows.priceRows(
            prices(
                price(LocalPriceIndicatorKind.MEAL_OUT, "Above Avg", "12% above EU average · Country fallback · 2024"),
                band = LocalPriceSummaryBand.HIGH,
            ),
        ).single()

        assertThat(result.value).isEqualTo("High")
    }

    @Test
    fun `a row without a readable difference falls back to its level word`() {
        val result = LocalInfoRows.priceRows(
            prices(price(LocalPriceIndicatorKind.MEAL_OUT, "Moderate", "Country fallback · 2024")),
        ).single()

        assertThat(result.detail).isEqualTo("Meals Moderate")
    }

    @Test
    fun `the rent benchmark keeps its own row`() {
        val result = LocalInfoRows.priceRows(
            prices(
                price(LocalPriceIndicatorKind.RENT_ONE_BEDROOM, "$1,420/mo", "County benchmark · Travis County · 2026"),
            ),
        )

        assertThat(result).hasSize(1)
        assertThat(result.single().title).isEqualTo("1BR Rent")
        assertThat(result.single().value).isEqualTo("$1,420/mo")
        assertThat(result.single().detail).isEqualTo("County benchmark · Travis County · 2026")
    }

    @Test
    fun `no price rows without indicators`() {
        assertThat(LocalInfoRows.priceRows(LocalPriceLevelSnapshot())).isEmpty()
    }

    @Test
    fun `the order is emergency, holiday, school break, prices`() {
        val result = rows(
            ready(
                publicHoliday = holiday(LocalHolidayPhase.NEXT, "All Saints", "2026-11-01"),
                schoolHoliday = holiday(LocalHolidayPhase.NEXT, "Autumn Break", "2026-10-26", "2026-10-30"),
                prices = prices(price(LocalPriceIndicatorKind.OVERALL, "Moderate", "0% above EU average · x · 2024")),
            ),
        )

        assertThat(result.map { it.id }).containsExactly(
            LocalInfoRows.EMERGENCY_ID,
            LocalInfoRows.PUBLIC_HOLIDAY_ID,
            LocalInfoRows.SCHOOL_HOLIDAY_ID,
            LocalInfoRows.PRICES_VS_EU_ID,
        ).inOrder()
    }
}
