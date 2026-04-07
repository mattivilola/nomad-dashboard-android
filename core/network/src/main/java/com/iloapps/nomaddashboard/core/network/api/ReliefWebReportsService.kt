package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.ReliefWebReportsRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface ReliefWebReportsService {
    @POST("v2/reports")
    suspend fun reports(
        @Query("appname") appName: String,
        @Body request: ReliefWebReportsRequest,
    ): Response<ResponseBody>
}
