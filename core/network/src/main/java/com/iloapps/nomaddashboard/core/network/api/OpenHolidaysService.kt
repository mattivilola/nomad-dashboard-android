package com.iloapps.nomaddashboard.core.network.api

import com.iloapps.nomaddashboard.core.network.model.OpenHolidaysHoliday
import com.iloapps.nomaddashboard.core.network.model.OpenHolidaysSubdivision
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenHolidaysService {
    @GET("Subdivisions")
    suspend fun subdivisions(
        @Query("countryIsoCode") countryIsoCode: String,
    ): List<OpenHolidaysSubdivision>

    @GET("SchoolHolidays")
    suspend fun schoolHolidays(
        @Query("countryIsoCode") countryIsoCode: String,
        @Query("subdivisionCode") subdivisionCode: String,
        @Query("languageIsoCode") languageIsoCode: String = "EN",
        @Query("validFrom") validFrom: String,
        @Query("validTo") validTo: String,
    ): List<OpenHolidaysHoliday>
}
