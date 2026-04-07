package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.TankerkoenigListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TankerkoenigService {
    @GET("json/list.php")
    suspend fun stations(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("rad") radiusKilometers: Int = 25,
        @Query("sort") sort: String = "dist",
        @Query("type") type: String = "all",
        @Query("apikey") apiKey: String,
    ): TankerkoenigListResponse
}
