package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.FreeIpApiResponse
import retrofit2.http.GET

interface FreeIpApiService {
    @GET("api/json/")
    suspend fun lookupMe(): FreeIpApiResponse
}

