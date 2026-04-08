package com.iloapps.nomaddashboard.core.network.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers

interface SmartravellerService {
    @Headers("Accept: text/html,application/xhtml+xml")
    @GET("destinations")
    suspend fun destinations(): ResponseBody
}
