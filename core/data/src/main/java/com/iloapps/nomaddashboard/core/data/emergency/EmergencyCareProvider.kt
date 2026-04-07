package com.iloapps.nomaddashboard.core.data.emergency

import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot

data class EmergencyCareSearchRequest(
    val latitude: Double,
    val longitude: Double,
    val countryCode: String?,
    val countryName: String?,
    val locationSource: EmergencyCareLocationSource,
)

interface EmergencyCareProvider {
    suspend fun nearbyHospital(request: EmergencyCareSearchRequest): EmergencyCareSnapshot
}

data class PlacesNearbySearchRequest(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val maxResultCount: Int,
)

data class PlacesNearbySearchPlace(
    val placeId: String?,
    val name: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val primaryType: String?,
)

sealed interface PlacesNearbySearchOutcome {
    data class Success(
        val places: List<PlacesNearbySearchPlace>,
    ) : PlacesNearbySearchOutcome

    data class ConfigurationRequired(
        val message: String,
    ) : PlacesNearbySearchOutcome

    data class Error(
        val message: String,
    ) : PlacesNearbySearchOutcome
}

interface PlacesNearbySearchClient {
    suspend fun searchNearbyHospitals(request: PlacesNearbySearchRequest): PlacesNearbySearchOutcome
}
