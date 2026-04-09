package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.NagerPublicHoliday
import retrofit2.http.GET
import retrofit2.http.Path

interface NagerDateService {
    @GET("api/v3/publicholidays/{year}/{countryCode}")
    suspend fun publicHolidays(
        @Path("year") year: Int,
        @Path("countryCode") countryCode: String,
    ): List<NagerPublicHoliday>
}
