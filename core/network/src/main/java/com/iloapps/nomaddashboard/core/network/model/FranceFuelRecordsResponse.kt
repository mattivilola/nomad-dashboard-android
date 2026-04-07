package com.iloapps.nomaddashboard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FranceFuelRecordsResponse(
    @SerialName("total_count") val totalCount: Int? = null,
    @SerialName("results") val results: List<FranceFuelRecord> = emptyList(),
)

@Serializable
data class FranceFuelRecord(
    @SerialName("id") val id: Long? = null,
    @SerialName("adresse") val address: String? = null,
    @SerialName("ville") val locality: String? = null,
    @SerialName("geom") val geometry: FranceFuelGeometry? = null,
    @SerialName("prix") val pricesJson: String? = null,
)

@Serializable
data class FranceFuelGeometry(
    @SerialName("lon") val longitude: Double? = null,
    @SerialName("lat") val latitude: Double? = null,
)
