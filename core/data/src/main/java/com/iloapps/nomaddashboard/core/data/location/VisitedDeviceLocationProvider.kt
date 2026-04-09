package com.iloapps.nomaddashboard.core.data.location

data class ResolvedVisitedPlace(
    val city: String?,
    val region: String?,
    val country: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
)

enum class DeviceLocationResolutionStatus {
    PERMISSION_MISSING,
    NO_FIX,
    COORDINATES_ONLY,
    PLACE_RESOLVED,
}

data class DeviceLocationSnapshot(
    val status: DeviceLocationResolutionStatus,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null

    val hasPlace: Boolean
        get() = country != null

    fun toResolvedVisitedPlaceOrNull(): ResolvedVisitedPlace? =
        country?.let { resolvedCountry ->
            ResolvedVisitedPlace(
                city = city,
                region = region,
                country = resolvedCountry,
                countryCode = countryCode,
                latitude = latitude,
                longitude = longitude,
            )
        }
}

interface VisitedDeviceLocationProvider {
    fun hasLocationPermission(): Boolean

    suspend fun currentLocation(): DeviceLocationSnapshot
}
