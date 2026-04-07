package com.iloapps.nomaddashboard.core.network.api

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET

interface SmartravellerService {
    @GET("destinations-export")
    suspend fun destinations(): JsonElement
}
