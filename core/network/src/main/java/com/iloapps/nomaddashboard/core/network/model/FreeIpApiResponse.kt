package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FreeIpApiResponse(
    @SerialName("ipAddress") val ipAddress: String? = null,
    @SerialName("cityName") val cityName: String? = null,
    @SerialName("regionName") val regionName: String? = null,
    @SerialName("countryName") val countryName: String? = null,
    @SerialName("countryCode") val countryCode: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("timeZone") val timeZone: String? = null,
)

