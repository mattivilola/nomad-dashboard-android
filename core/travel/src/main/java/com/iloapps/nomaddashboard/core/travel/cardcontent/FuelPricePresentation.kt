package com.iloapps.nomaddashboard.core.travel.cardcontent

import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import java.time.Instant

/**
 * One fuel row of the Fuel Prices card. The price is neutral text, never coloured by fuel type; the
 * number and its "/L" unit are kept apart so the unit can be drawn smaller.
 *
 * @property price The pump price in the locale's currency style ("1,429 €").
 * @property updated "Updated 11:30", null when the station did not say.
 */
data class FuelPriceRow(
    val fuelType: FuelType,
    val title: String,
    val stationName: String,
    val detail: String,
    val price: String,
    val priceUnit: String,
    val updated: String?,
    val latitude: Double,
    val longitude: Double,
) {
    /** The whole row as one sentence, for accessibility. */
    val accessibilityLabel: String
        get() = "$title, $stationName, $price per liter"
}

object FuelPricePresentation {
    /** Diesel first, then gasoline; only the fuels the snapshot has a station for. */
    fun rows(snapshot: FuelPriceSnapshot, time: CardTime): List<FuelPriceRow> =
        listOfNotNull(snapshot.diesel, snapshot.gasoline).map { row(it, time) }

    private fun row(station: FuelStationPrice, time: CardTime): FuelPriceRow =
        FuelPriceRow(
            fuelType = station.fuelType,
            title = when (station.fuelType) {
                FuelType.DIESEL -> "Diesel"
                FuelType.GASOLINE -> "Gasoline"
            },
            stationName = station.stationName,
            detail = detail(station),
            price = TravelFormatters.fuelPrice(station.pricePerLiter, station.currencyCode, time.locale),
            priceUnit = TravelFormatters.FuelPriceUnit,
            updated = station.updatedAt?.let { updatedText(it, time) },
            latitude = station.latitude,
            longitude = station.longitude,
        )

    /** "Calle Mayor 1 · Tarifa · 4.8 km · Self-service". */
    private fun detail(station: FuelStationPrice): String {
        val pieces = listOfNotNull(
            station.locality,
            TravelFormatters.kilometers(station.distanceKilometers),
            "Self-service".takeIf { station.isSelfService == true },
        )
        return (listOfNotNull(station.address) + pieces).joinToString(" · ")
    }

    private fun updatedText(updatedAt: Instant, time: CardTime): String = "Updated ${time.clock(updatedAt)}"
}
