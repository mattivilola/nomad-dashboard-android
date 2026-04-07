package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.SpainFuelResponse
import retrofit2.http.GET

interface SpainFuelPriceService {
    @GET("ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/")
    suspend fun stations(): SpainFuelResponse
}
