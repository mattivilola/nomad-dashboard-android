package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.EurostatDatasetResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface EurostatService {
    @GET("api/dissemination/statistics/1.0/data/prc_ppp_ind_1")
    suspend fun purchasingPowerParity(
        @Query("geo") countryCode: String,
        @Query("indic_ppp") indicator: String = "PLI_EU27_2020",
        @Query("ppp_cat18") category: String,
        @Query("freq") frequency: String = "A",
        @Query("lang") language: String = "EN",
    ): EurostatDatasetResponse
}
