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

data class PlacesTextSearchRequest(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val query: String,
    val maxResultCount: Int,
)

data class PlacesNearbySearchPlace(
    val placeId: String?,
    val name: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val primaryType: String?,
    /** The city or town from the address components; used to recognise one hospital listed under two names. */
    val locality: String? = null,
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

    /** A broader text search (for example "urgencias") that the typed nearby search can miss. */
    suspend fun searchHospitalsByText(request: PlacesTextSearchRequest): PlacesNearbySearchOutcome
}
