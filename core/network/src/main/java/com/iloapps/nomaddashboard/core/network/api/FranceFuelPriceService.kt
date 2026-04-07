package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.FranceFuelRecordsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface FranceFuelPriceService {
    @GET("api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records")
    suspend fun records(
        @Query("select") select: String = "id,adresse,ville,geom,prix",
        @Query("where") where: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): FranceFuelRecordsResponse
}
