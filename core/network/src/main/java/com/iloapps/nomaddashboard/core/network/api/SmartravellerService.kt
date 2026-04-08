package com.iloapps.nomaddashboard.core.network.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

interface SmartravellerService {
    @Headers(
        "Accept: text/html,application/xhtml+xml",
        "Accept-Language: en-AU,en;q=0.9",
        "User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36",
    )
    @GET("destinations")
    suspend fun destinations(): Response<ResponseBody>

    @Headers(
        "Accept: application/json,text/plain,*/*",
        "Accept-Language: en-AU,en;q=0.9",
        "User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36",
    )
    @GET("destinations-export")
    suspend fun destinationsExport(): Response<ResponseBody>
}
