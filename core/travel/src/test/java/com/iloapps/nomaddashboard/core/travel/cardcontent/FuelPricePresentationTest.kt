package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.travel.daylight.ClockTimeFormatter
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpFormats
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.now
import com.iloapps.nomaddashboard.core.travel.cardcontent.CardFixtures.utc
import java.util.Locale
import org.junit.Test

class FuelPricePresentationTest {
    private val spanish = CardTime(utc, HeadsUpFormats(locale = Locale.forLanguageTag("es-ES"), clockTime = ClockTimeFormatter.Hour24))

    private fun station(
        type: FuelType,
        price: Double,
        address: String? = null,
        locality: String? = "Tarifa",
        selfService: Boolean? = null,
        updated: Boolean = true,
    ) = FuelStationPrice(
        fuelType = type,
        stationName = "Repsol Tarifa",
        address = address,
        locality = locality,
        pricePerLiter = price,
        currencyCode = "EUR",
        distanceKilometers = 4.84,
        latitude = 36.01,
        longitude = -5.6,
        updatedAt = now.takeIf { updated },
        isSelfService = selfService,
    )

    private fun snapshot(diesel: FuelStationPrice? = null, gasoline: FuelStationPrice? = null) =
        FuelPriceSnapshot(status = FuelPriceStatus.READY, diesel = diesel, gasoline = gasoline)

    @Test
    fun `diesel comes first and the price keeps its unit apart`() {
        val rows = FuelPricePresentation.rows(
            snapshot(diesel = station(FuelType.DIESEL, 1.429), gasoline = station(FuelType.GASOLINE, 1.549)),
            spanish,
        )

        assertThat(rows.map { it.title }).containsExactly("Diesel", "Gasoline").inOrder()
        assertThat(rows[0].price).isEqualTo("1,429\u00a0€")
        assertThat(rows[0].priceUnit).isEqualTo("/L")
        assertThat(rows[1].price).isEqualTo("1,549\u00a0€")
    }

    @Test
    fun `only the fuels with a station are listed`() {
        assertThat(FuelPricePresentation.rows(snapshot(gasoline = station(FuelType.GASOLINE, 1.5)), spanish))
            .hasSize(1)
        assertThat(FuelPricePresentation.rows(snapshot(), spanish)).isEmpty()
    }

    @Test
    fun `the detail has the address, the town, the distance and self service`() {
        val row = FuelPricePresentation.rows(
            snapshot(diesel = station(FuelType.DIESEL, 1.4, address = "N-340 km 80", selfService = true)),
            spanish,
        ).single()

        assertThat(row.detail).isEqualTo("N-340 km 80 · Tarifa · 4.8 km · Self-service")
    }

    @Test
    fun `without an address the detail starts with the town`() {
        val row = FuelPricePresentation.rows(snapshot(diesel = station(FuelType.DIESEL, 1.4)), spanish).single()

        assertThat(row.detail).isEqualTo("Tarifa · 4.8 km")
    }

    @Test
    fun `the update time reads in the card's clock`() {
        val row = FuelPricePresentation.rows(snapshot(diesel = station(FuelType.DIESEL, 1.4)), spanish).single()

        assertThat(row.updated).isEqualTo("Updated 12:00")
        assertThat(
            FuelPricePresentation.rows(snapshot(diesel = station(FuelType.DIESEL, 1.4, updated = false)), spanish)
                .single().updated,
        ).isNull()
    }

    @Test
    fun `the row says where the station is and reads as a sentence`() {
        val row = FuelPricePresentation.rows(snapshot(diesel = station(FuelType.DIESEL, 1.429)), spanish).single()

        assertThat(row.latitude).isEqualTo(36.01)
        assertThat(row.longitude).isEqualTo(-5.6)
        assertThat(row.accessibilityLabel).isEqualTo("Diesel, Repsol Tarifa, 1,429\u00a0€ per liter")
    }
}
