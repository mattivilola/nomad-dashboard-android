package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.IpifyResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface IpifyService {
    @GET(".")
    suspend fun lookupIp(
        @Query("format") format: String = "json",
    ): IpifyResponse
}
