package com.iloapps.nomaddashboard.core.data.location

data class ResolvedVisitedPlace(
    val city: String?,
    val region: String?,
    val country: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
)

interface VisitedDeviceLocationProvider {
    fun hasLocationPermission(): Boolean

    suspend fun currentPlace(): ResolvedVisitedPlace?
}
