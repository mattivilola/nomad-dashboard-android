package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.FreeIpApiResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface FreeIpApiService {
    @GET("api/json/")
    suspend fun lookupMe(): FreeIpApiResponse

    @GET("api/json/{ipAddress}")
    suspend fun lookup(
        @Path("ipAddress") ipAddress: String,
    ): FreeIpApiResponse
}
