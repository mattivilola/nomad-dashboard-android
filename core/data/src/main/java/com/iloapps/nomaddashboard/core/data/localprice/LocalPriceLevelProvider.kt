package com.iloapps.nomaddashboard.core.data.localprice

import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot

data class LocalPriceLevelRequest(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val locality: String? = null,
)

interface LocalPriceLevelProvider {
    suspend fun prices(
        request: LocalPriceLevelRequest,
        hudUserApiToken: String?,
    ): LocalPriceLevelSnapshot

    suspend fun clearUsCache()
}
