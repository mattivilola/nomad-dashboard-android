package com.iloapps.nomaddashboard.core.network.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface HudUserFmrService {
    @GET("hudapi/public/fmr/data/{countyGEOID}")
    suspend fun fairMarketRent(
        @Path("countyGEOID") countyGEOID: String,
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/json",
    ): ResponseBody
}
