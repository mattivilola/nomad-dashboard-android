package com.iloapps.nomaddashboard.core.model

import java.time.Instant

enum class FuelType {
    DIESEL,
    GASOLINE,
}

enum class FuelPriceStatus {
    READY,
    UNSUPPORTED,
    CONFIGURATION_REQUIRED,
    UNAVAILABLE,
    NO_STATIONS_FOUND,
}

data class FuelStationPrice(
    val fuelType: FuelType,
    val stationName: String,
    val address: String? = null,
    val locality: String? = null,
    val pricePerLiter: Double,
    val currencyCode: String = "EUR",
    val distanceKilometers: Double,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Instant? = null,
    val isSelfService: Boolean? = null,
)

data class FuelPriceSnapshot(
    val status: FuelPriceStatus = FuelPriceStatus.UNAVAILABLE,
    val sourceName: String = "Nomad Fuel Prices",
    val countryCode: String? = null,
    val countryName: String? = null,
    val searchRadiusKilometers: Double = 50.0,
    val diesel: FuelStationPrice? = null,
    val gasoline: FuelStationPrice? = null,
    val fetchedAt: Instant? = null,
    val detail: String = "Enable fuel prices in Settings",
    val note: String? = null,
)
