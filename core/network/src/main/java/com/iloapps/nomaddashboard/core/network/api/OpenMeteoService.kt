package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.OpenMeteoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,precipitation_probability,wind_speed_10m,wind_direction_10m",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoResponse
}

