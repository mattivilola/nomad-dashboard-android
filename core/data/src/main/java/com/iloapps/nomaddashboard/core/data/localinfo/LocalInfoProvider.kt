package com.iloapps.nomaddashboard.core.data.localinfo

import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot

enum class LocalInfoLocationSource {
    DEVICE,
    IP_GEOLOCATION,
}

data class LocalInfoRequest(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locality: String? = null,
    val region: String? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val timeZoneId: String? = null,
    val locationSource: LocalInfoLocationSource,
)

interface LocalInfoProvider {
    suspend fun localInfo(
        request: LocalInfoRequest,
        hudUserApiToken: String?,
    ): LocalInfoSnapshot
}
