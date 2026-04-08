package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.OpenMeteoMarineResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoMarineService {
    @GET("v1/marine")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "wave_height,wave_period,swell_wave_height,swell_wave_period,swell_wave_direction,sea_surface_temperature",
        @Query("forecast_days") forecastDays: Int = 2,
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoMarineResponse
}
