package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.IpifyResponse
import retrofit2.http.GET

interface IpifyService {
    @GET("api")
    suspend fun lookupIp(): IpifyResponse
}
