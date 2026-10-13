package com.iloapps.nomaddashboard.core.travel.summary

import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import com.iloapps.nomaddashboard.core.travel.format.TravelFormatters
import java.util.Locale

/** Text for the Saved essentials sheet, matching how the dashboard cards format the same values. */
object OfflineEssentialsPresentation {
    /** "19° · Partly Cloudy", or the condition alone when the temperature is unknown. */
    fun weatherSummary(weather: WeatherSnapshot): String {
        val temperature = weather.currentTemperatureCelsius?.let { "${TravelFormatters.temperature(it)} · " }.orEmpty()
        return temperature + weather.conditionDescription
    }

    /** "Station · 1,429 €/L", using the three-decimal pump-price format of the fuel card. */
    fun fuelLine(station: FuelStationPrice, locale: Locale = Locale.getDefault()): String {
        val price = TravelFormatters.fuelPrice(station.pricePerLiter, station.currencyCode, locale)
        return "${station.stationName} · $price${TravelFormatters.FuelPriceUnit}"
    }
}
