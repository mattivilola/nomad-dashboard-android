package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TankerkoenigListResponse(
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("message") val message: String? = null,
    @SerialName("stations") val stations: List<TankerkoenigStation> = emptyList(),
)

@Serializable
data class TankerkoenigStation(
    @SerialName("id") val identifier: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("brand") val brand: String? = null,
    @SerialName("street") val street: String? = null,
    @SerialName("houseNumber") val houseNumber: String? = null,
    @SerialName("place") val locality: String? = null,
    @SerialName("lat") val latitude: Double? = null,
    @SerialName("lng") val longitude: Double? = null,
    @SerialName("diesel") val dieselPrice: Double? = null,
    @SerialName("e5") val e5Price: Double? = null,
    @SerialName("e10") val e10Price: Double? = null,
    @SerialName("isOpen") val isOpen: Boolean? = null,
)
