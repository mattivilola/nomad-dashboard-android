package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.CensusCountyLookupResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CensusGeocoderService {
    @GET("geocoder/geographies/coordinates")
    suspend fun countyForCoordinate(
        @Query("x") longitude: Double,
        @Query("y") latitude: Double,
        @Query("benchmark") benchmark: String = "Public_AR_Current",
        @Query("vintage") vintage: String = "Current_Current",
        @Query("format") format: String = "json",
    ): CensusCountyLookupResponse
}
