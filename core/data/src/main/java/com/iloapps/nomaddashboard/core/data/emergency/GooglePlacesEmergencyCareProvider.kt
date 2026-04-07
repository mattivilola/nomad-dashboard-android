package com.iloapps.nomaddashboard.core.data.emergency

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class GooglePlacesEmergencyCareProvider @Inject constructor(
    private val searchClient: PlacesNearbySearchClient,
) : EmergencyCareProvider {
    override suspend fun nearbyHospital(request: EmergencyCareSearchRequest): EmergencyCareSnapshot {
        val fetchedAt = java.time.Instant.now()
        return when (val outcome = searchClient.searchNearbyHospitals(request.toPlacesRequest())) {
            is PlacesNearbySearchOutcome.Success -> {
                val facility = outcome.places
                    .mapNotNull { it.toFacility(request.latitude, request.longitude) }
                    .minByOrNull(EmergencyCareFacility::distanceKilometers)

                if (facility != null) {
                    EmergencyCareSnapshot(
                        status = EmergencyCareStatus.READY,
                        countryCode = request.countryCode,
                        countryName = request.countryName,
                        locationSource = request.locationSource,
                        facility = facility,
                        fetchedAt = fetchedAt,
                        detail = "Nearest hospital found within 10 km.",
                        note = locationSourceNote(request.locationSource),
                    )
                } else {
                    EmergencyCareSnapshot(
                        status = EmergencyCareStatus.UNAVAILABLE,
                        countryCode = request.countryCode,
                        countryName = request.countryName,
                        locationSource = request.locationSource,
                        fetchedAt = fetchedAt,
                        detail = "No nearby hospitals found within 10 km.",
                        note = locationSourceNote(request.locationSource),
                    )
                }
            }

            is PlacesNearbySearchOutcome.ConfigurationRequired -> EmergencyCareSnapshot(
                status = EmergencyCareStatus.CONFIGURATION_REQUIRED,
                countryCode = request.countryCode,
                countryName = request.countryName,
                locationSource = request.locationSource,
                fetchedAt = fetchedAt,
                detail = outcome.message,
            )

            is PlacesNearbySearchOutcome.Error -> EmergencyCareSnapshot(
                status = EmergencyCareStatus.ERROR,
                countryCode = request.countryCode,
                countryName = request.countryName,
                locationSource = request.locationSource,
                fetchedAt = fetchedAt,
                detail = "Nearby hospitals are unavailable right now.",
                note = outcome.message,
            )
        }
    }

    private fun EmergencyCareSearchRequest.toPlacesRequest(): PlacesNearbySearchRequest =
        PlacesNearbySearchRequest(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = SEARCH_RADIUS_METERS,
            maxResultCount = MAX_RESULTS,
        )

    private fun PlacesNearbySearchPlace.toFacility(
        originLatitude: Double,
        originLongitude: Double,
    ): EmergencyCareFacility? {
        val resolvedName = name?.trim()?.takeIf(String::isNotBlank) ?: return null
        val resolvedLatitude = latitude ?: return null
        val resolvedLongitude = longitude ?: return null
        return EmergencyCareFacility(
            placeId = placeId,
            name = resolvedName,
            address = address?.trim()?.takeIf(String::isNotBlank),
            distanceKilometers = distanceKilometers(
                startLatitude = originLatitude,
                startLongitude = originLongitude,
                endLatitude = resolvedLatitude,
                endLongitude = resolvedLongitude,
            ),
            latitude = resolvedLatitude,
            longitude = resolvedLongitude,
            primaryType = primaryType,
        )
    }

    private fun locationSourceNote(source: EmergencyCareLocationSource): String =
        when (source) {
            EmergencyCareLocationSource.DEVICE -> "Using device location."
            EmergencyCareLocationSource.IP_GEOLOCATION -> "Using public IP geolocation."
        }

    private companion object {
        const val MAX_RESULTS = 5
        const val SEARCH_RADIUS_METERS = 10_000.0
    }
}

@Singleton
class GooglePlacesNearbySearchClient @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlacesNearbySearchClient {
    private var placesClient: PlacesClient? = null
    private val clientLock = Any()

    override suspend fun searchNearbyHospitals(request: PlacesNearbySearchRequest): PlacesNearbySearchOutcome =
        withContext(ioDispatcher) {
            val apiKey = localPlacesApiKey()
            if (apiKey.isBlank()) {
                return@withContext PlacesNearbySearchOutcome.ConfigurationRequired(
                    "Add a local Android Maps/Places key before emergency-care lookups can run.",
                )
            }

            val client = synchronized(clientLock) {
                placesClient ?: runCatching {
                    if (Places.isInitialized().not()) {
                        Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
                    }
                    Places.createClient(context.applicationContext)
                }.getOrNull()?.also { created ->
                    placesClient = created
                }
            } ?: return@withContext PlacesNearbySearchOutcome.ConfigurationRequired(
                "Google Places could not initialize from the local Android app key.",
            )

            val fields = listOf(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION,
                Place.Field.PRIMARY_TYPE,
            )
            val bounds = CircularBounds.newInstance(
                com.google.android.gms.maps.model.LatLng(request.latitude, request.longitude),
                request.radiusMeters,
            )
            val searchRequest = SearchNearbyRequest.builder(bounds, fields)
                .setIncludedPrimaryTypes(listOf(HOSPITAL_PRIMARY_TYPE))
                .setMaxResultCount(request.maxResultCount)
                .build()

            try {
                val response = client.searchNearby(searchRequest).awaitResult()
                PlacesNearbySearchOutcome.Success(
                    response.places.map { place ->
                        PlacesNearbySearchPlace(
                            placeId = place.id,
                            name = place.displayName,
                            address = place.formattedAddress,
                            latitude = place.location?.latitude,
                            longitude = place.location?.longitude,
                            primaryType = place.primaryType,
                        )
                    },
                )
            } catch (error: Throwable) {
                when {
                    error.isLikelyConfigurationIssue() -> PlacesNearbySearchOutcome.ConfigurationRequired(
                        error.localizedMessage?.takeIf(String::isNotBlank)
                            ?: "Enable Places API (New) for the local Android Maps key.",
                    )

                    else -> PlacesNearbySearchOutcome.Error(
                        error.localizedMessage?.takeIf(String::isNotBlank)
                            ?: "Google Places request failed.",
                    )
                }
            }
        }

    private fun localPlacesApiKey(): String {
        return context.applicationInfo.metaData?.getString(GOOGLE_MAPS_API_KEY_METADATA)
            ?.trim()
            .orEmpty()
    }

    private fun Throwable.isLikelyConfigurationIssue(): Boolean {
        val message = (this as? ApiException)?.statusMessage
            ?: localizedMessage
            ?: this.message
            ?: return false
        return message.contains("api key", ignoreCase = true) ||
            message.contains("not authorized", ignoreCase = true) ||
            message.contains("places api", ignoreCase = true)
    }

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
            addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.cancel()
                }
            }
        }

    private companion object {
        const val GOOGLE_MAPS_API_KEY_METADATA = "com.google.android.geo.API_KEY"
        const val HOSPITAL_PRIMARY_TYPE = "hospital"
    }
}

private fun distanceKilometers(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val earthRadiusKilometers = 6_371.0
    val deltaLatitude = Math.toRadians(endLatitude - startLatitude)
    val deltaLongitude = Math.toRadians(endLongitude - startLongitude)
    val originLatitudeRadians = Math.toRadians(startLatitude)
    val destinationLatitudeRadians = Math.toRadians(endLatitude)
    val haversine = sin(deltaLatitude / 2).pow(2) +
        cos(originLatitudeRadians) * cos(destinationLatitudeRadians) *
        sin(deltaLongitude / 2).pow(2)
    val arc = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    return earthRadiusKilometers * arc
}
