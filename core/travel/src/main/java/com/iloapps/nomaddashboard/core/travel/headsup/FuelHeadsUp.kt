package com.iloapps.nomaddashboard.core.travel.headsup

import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import java.util.Locale

/** Cheapest-fuel heads-up: the best station per type, diesel first. */
object FuelHeadsUp {
    /**
     * "Diesel 1,429 € · 4.8 km" with "Cheapest within 50 km" below. The "/L" unit is left out of the
     * title, as on macOS. Null unless the fuel prices are ready and have a station.
     */
    fun evaluate(fuel: FuelPriceSnapshot, locale: Locale): HeadsUpDecision? {
        if (fuel.status != FuelPriceStatus.READY) return null
        // The snapshot only carries the best station per fuel type; diesel leads, matching the fuel card's row order.
        val station = fuel.diesel ?: fuel.gasoline ?: return null

        val price = TravelFormatters.fuelPrice(station.pricePerLiter, station.currencyCode, locale)
        val distance = TravelFormatters.kilometers(station.distanceKilometers)
        return HeadsUpDecision(
            severity = HeadsUpSeverity.INFO,
            title = "${displayName(station.fuelType)} $price · $distance",
            detail = "Cheapest within ${fuel.searchRadiusKilometers.toInt()} km",
            statusText = price,
        )
    }

    private fun displayName(type: FuelType): String =
        when (type) {
            FuelType.DIESEL -> "Diesel"
            FuelType.GASOLINE -> "Gasoline"
        }
}
