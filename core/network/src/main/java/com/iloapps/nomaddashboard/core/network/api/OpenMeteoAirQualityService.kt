package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.OpenMeteoAirQualityResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoAirQualityService {
    /**
     * [latitude] and [longitude] are text so the caller controls the exact precision that leaves
     * the device: the air-quality provider only ever sees the ~11 km grid point.
     */
    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: String,
        @Query("longitude") longitude: String,
        @Query("current") current: String = CURRENT_VARIABLES,
        @Query("hourly") hourly: String = HOURLY_VARIABLES,
        // The hourly series starts at the current hour, so one extra hour yields the next 12.
        @Query("forecast_hours") forecastHours: Int = FORECAST_HOURS + 1,
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoAirQualityResponse

    companion object {
        const val FORECAST_HOURS = 12
        const val CURRENT_VARIABLES =
            "european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide," +
                "european_aqi_pm2_5,european_aqi_pm10,european_aqi_ozone," +
                "european_aqi_nitrogen_dioxide,european_aqi_sulphur_dioxide"
        const val HOURLY_VARIABLES = "european_aqi"
    }
}
