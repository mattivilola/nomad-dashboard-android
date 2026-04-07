package com.iloapps.nomaddashboard.core.data.fuel

import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot

data class FuelSearchRequest(
    val latitude: Double,
    val longitude: Double,
    val countryCode: String,
    val countryName: String?,
    val searchRadiusKilometers: Double = 50.0,
)

interface FuelPriceProvider {
    suspend fun prices(request: FuelSearchRequest): FuelPriceSnapshot
}
