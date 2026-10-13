package com.iloapps.nomaddashboard.core.data.emergency

import com.iloapps.nomaddashboard.core.common.runCatchingCancellable

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class GooglePlacesEmergencyCareProvider @Inject constructor(
    private val searchClient: PlacesNearbySearchClient,
) : EmergencyCareProvider {
    override suspend fun nearbyHospital(request: EmergencyCareSearchRequest): EmergencyCareSnapshot {
        val fetchedAt = java.time.Instant.now()
        return when (val search = expandedHospitalSearch(request)) {
            is ExpandedSearch.Found -> if (search.hospitals.isNotEmpty()) {
                EmergencyCareSnapshot(
                    status = EmergencyCareStatus.READY,
                    countryCode = request.countryCode,
                    countryName = request.countryName,
                    searchRadiusKilometers = search.radiusKilometers,
                    locationSource = request.locationSource,
                    facilities = search.hospitals,
                    fetchedAt = fetchedAt,
                    detail = "Nearby emergency hospitals within ${search.radiusKilometers.toInt()} km.",
                    note = locationSourceNote(request.locationSource),
                )
            } else {
                EmergencyCareSnapshot(
                    status = EmergencyCareStatus.UNAVAILABLE,
                    countryCode = request.countryCode,
                    countryName = request.countryName,
                    searchRadiusKilometers = search.radiusKilometers,
                    locationSource = request.locationSource,
                    fetchedAt = fetchedAt,
                    detail = "No nearby hospitals found within ${search.radiusKilometers.toInt()} km.",
                    note = locationSourceNote(request.locationSource),
                )
            }

            ExpandedSearch.ConfigurationRequired -> EmergencyCareSnapshot(
                status = EmergencyCareStatus.CONFIGURATION_REQUIRED,
                countryCode = request.countryCode,
                countryName = request.countryName,
                locationSource = request.locationSource,
                fetchedAt = fetchedAt,
                detail = "Emergency care is unavailable in this build right now.",
            )

            is ExpandedSearch.Failed -> EmergencyCareSnapshot(
                status = EmergencyCareStatus.ERROR,
                countryCode = request.countryCode,
                countryName = request.countryName,
                locationSource = request.locationSource,
                fetchedAt = fetchedAt,
                detail = "Nearby hospitals are unavailable right now.",
                note = search.message,
            )
        }
    }

    private sealed interface ExpandedSearch {
        data class Found(val hospitals: List<EmergencyCareFacility>, val radiusKilometers: Double) : ExpandedSearch
        data object ConfigurationRequired : ExpandedSearch
        data class Failed(val message: String) : ExpandedSearch
    }

    private data class SearchCandidate(
        val hospitals: List<EmergencyCareFacility>,
        val radiusKilometers: Double,
    ) {
        val totalDistanceKilometers: Double = hospitals.sumOf(EmergencyCareFacility::distanceKilometers)
    }

    /**
     * Searches nearby hospitals at 25 km and, while fewer than the preferred three are found, widens to
     * 50 and then 100 km (at most three requests). Only when no nearby search found any hospital are the
     * hospital and emergency text queries run, once, at 100 km. The best candidate wins and the radius
     * actually used is reported.
     */
    private suspend fun expandedHospitalSearch(request: EmergencyCareSearchRequest): ExpandedSearch {
        val preferredResults = minOf(PREFERRED_RESULTS, MAX_DISPLAYED_HOSPITALS)
        var best: SearchCandidate? = null
        var lastSuccessfulRadius: Double? = null
        var lastError: String? = null

        fun hospitals(candidates: List<HospitalCandidate>) =
            pickEmergencyHospitals(candidates, request.latitude, request.longitude, MAX_DISPLAYED_HOSPITALS)

        for (radius in SEARCH_RADII_KILOMETERS) {
            when (val outcome = searchClient.searchNearbyHospitals(request.toNearbyRequest(radius))) {
                is PlacesNearbySearchOutcome.ConfigurationRequired -> return ExpandedSearch.ConfigurationRequired
                is PlacesNearbySearchOutcome.Error -> lastError = outcome.message

                is PlacesNearbySearchOutcome.Success -> {
                    lastSuccessfulRadius = radius
                    val scoped = outcome.places.candidates(HospitalSearchSource.NEARBY, request, radius)
                    val selected = hospitals(scoped)
                    best = betterCandidate(best, selected, radius)
                    if (selected.size >= preferredResults) return ExpandedSearch.Found(selected, radius)
                }
            }
        }

        // Text search costs five requests, so it only runs when the nearby searches found no hospital at
        // all, once, at the widest radius.
        if (best == null) {
            val radius = SEARCH_RADII_KILOMETERS.last()
            val textOutcomes = coroutineScope {
                FALLBACK_TEXT_QUERIES.map { query ->
                    async { searchClient.searchHospitalsByText(request.toTextRequest(radius, query)) }
                }.awaitAll()
            }
            var hadSuccessfulTextSearch = false
            val combined = mutableListOf<HospitalCandidate>()
            for (outcome in textOutcomes) {
                when (outcome) {
                    is PlacesNearbySearchOutcome.Success -> {
                        hadSuccessfulTextSearch = true
                        lastSuccessfulRadius = radius
                        combined += outcome.places
                            .candidates(HospitalSearchSource.TEXT, request, radius)
                            .filter(::isRelevantBroaderSearchResult)
                    }

                    is PlacesNearbySearchOutcome.Error -> lastError = outcome.message
                    is PlacesNearbySearchOutcome.ConfigurationRequired -> Unit
                }
            }
            if (hadSuccessfulTextSearch) {
                best = betterCandidate(best, hospitals(combined), radius)
            }
        }

        val radius = lastSuccessfulRadius
            ?: return ExpandedSearch.Failed(lastError ?: "Google Places request failed.")
        return best?.let { ExpandedSearch.Found(it.hospitals, it.radiusKilometers) }
            ?: ExpandedSearch.Found(emptyList(), radius)
    }

    /** More hospitals win, then the smaller radius, then the smaller total distance. */
    private fun betterCandidate(
        current: SearchCandidate?,
        hospitals: List<EmergencyCareFacility>,
        radiusKilometers: Double,
    ): SearchCandidate? {
        if (hospitals.isEmpty()) return current
        val candidate = SearchCandidate(hospitals, radiusKilometers)
        if (current == null) return candidate
        return when {
            candidate.hospitals.size != current.hospitals.size ->
                if (candidate.hospitals.size > current.hospitals.size) candidate else current
            candidate.radiusKilometers != current.radiusKilometers ->
                if (candidate.radiusKilometers < current.radiusKilometers) candidate else current
            else -> if (candidate.totalDistanceKilometers < current.totalDistanceKilometers) candidate else current
        }
    }

    private fun List<PlacesNearbySearchPlace>.candidates(
        source: HospitalSearchSource,
        request: EmergencyCareSearchRequest,
        radiusKilometers: Double,
    ): List<HospitalCandidate> = mapNotNull { place ->
        val name = place.name?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val latitude = place.latitude?.takeIf { it in -90.0..90.0 } ?: return@mapNotNull null
        val longitude = place.longitude?.takeIf { it in -180.0..180.0 } ?: return@mapNotNull null
        if (distanceKilometers(request.latitude, request.longitude, latitude, longitude) > radiusKilometers) {
            return@mapNotNull null
        }
        HospitalCandidate(
            placeId = place.placeId,
            name = name,
            address = place.address?.trim()?.takeIf(String::isNotBlank),
            locality = place.locality?.trim()?.takeIf(String::isNotBlank),
            latitude = latitude,
            longitude = longitude,
            hint = place.primaryType,
            source = source,
        )
    }

    private fun EmergencyCareSearchRequest.toNearbyRequest(radiusKilometers: Double) =
        PlacesNearbySearchRequest(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusKilometers * 1_000,
            maxResultCount = MAX_SEARCH_RESULTS,
        )

    private fun EmergencyCareSearchRequest.toTextRequest(radiusKilometers: Double, query: String) =
        PlacesTextSearchRequest(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusKilometers * 1_000,
            query = query,
            maxResultCount = MAX_SEARCH_RESULTS,
        )

    private fun locationSourceNote(source: EmergencyCareLocationSource): String =
        when (source) {
            EmergencyCareLocationSource.DEVICE -> "Using device location."
            EmergencyCareLocationSource.IP_GEOLOCATION -> "Using public IP geolocation."
        }

    private companion object {
        /** 25 km first, then the macOS fallback radii. */
        val SEARCH_RADII_KILOMETERS = listOf(25.0, 50.0, 100.0)

        /** The most Places returns per request; the nearest ones come first (see the search client). */
        const val MAX_SEARCH_RESULTS = 20
        const val MAX_DISPLAYED_HOSPITALS = 3
        const val PREFERRED_RESULTS = 3
        val FALLBACK_TEXT_QUERIES = listOf("hospital", "emergency room", "urgent care", "urgencias", "emergencias")
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
        search { client ->
            val bounds = CircularBounds.newInstance(
                com.google.android.gms.maps.model.LatLng(request.latitude, request.longitude),
                request.radiusMeters,
            )
            val searchRequest = SearchNearbyRequest.builder(bounds, PLACE_FIELDS)
                .setIncludedPrimaryTypes(listOf(HOSPITAL_PRIMARY_TYPE))
                .setMaxResultCount(request.maxResultCount)
                // The default ranks by popularity, which can drop the nearest hospital from the capped list.
                .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
                .build()
            client.searchNearby(searchRequest).awaitResult().places.map(::toSearchPlace)
        }

    override suspend fun searchHospitalsByText(request: PlacesTextSearchRequest): PlacesNearbySearchOutcome =
        search { client ->
            // Text search can only bias, not restrict, to a circle; the provider filters by radius itself.
            val bounds = CircularBounds.newInstance(
                com.google.android.gms.maps.model.LatLng(request.latitude, request.longitude),
                request.radiusMeters,
            )
            val searchRequest = SearchByTextRequest.builder(request.query, PLACE_FIELDS)
                .setLocationBias(bounds)
                .setMaxResultCount(request.maxResultCount)
                .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                .build()
            client.searchByText(searchRequest).awaitResult().places.map(::toSearchPlace)
        }

    private suspend fun search(
        block: suspend (PlacesClient) -> List<PlacesNearbySearchPlace>,
    ): PlacesNearbySearchOutcome = withContext(ioDispatcher) {
        val apiKey = localPlacesApiKey()
        if (apiKey.isBlank()) {
            return@withContext PlacesNearbySearchOutcome.ConfigurationRequired(
                "Emergency care is unavailable in this build right now.",
            )
        }

        val client = synchronized(clientLock) {
            placesClient ?: runCatchingCancellable {
                if (Places.isInitialized().not()) {
                    Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
                }
                Places.createClient(context.applicationContext)
            }.getOrNull()?.also { created ->
                placesClient = created
            }
        } ?: return@withContext PlacesNearbySearchOutcome.ConfigurationRequired(
            "Emergency care is unavailable in this build right now.",
        )

        try {
            PlacesNearbySearchOutcome.Success(block(client))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            when {
                error.isLikelyConfigurationIssue() -> PlacesNearbySearchOutcome.ConfigurationRequired(
                    "Emergency care is unavailable in this build right now.",
                )

                else -> PlacesNearbySearchOutcome.Error(
                    error.localizedMessage?.takeIf(String::isNotBlank)
                        ?: "Google Places request failed.",
                )
            }
        }
    }

    private fun toSearchPlace(place: Place): PlacesNearbySearchPlace =
        PlacesNearbySearchPlace(
            placeId = place.id,
            name = place.displayName,
            address = place.formattedAddress,
            latitude = place.location?.latitude,
            longitude = place.location?.longitude,
            primaryType = place.primaryType,
            locality = place.addressComponents?.asList()
                ?.firstOrNull { LOCALITY_COMPONENT_TYPE in it.types }?.name,
        )

    private fun localPlacesApiKey(): String {
        @Suppress("DEPRECATION")
        val applicationInfo = runCatchingCancellable {
            context.packageManager.getApplicationInfo(context.packageName, LEGACY_GET_META_DATA_FLAG)
        }.getOrNull() ?: return ""

        return applicationInfo.metaData?.getString(GOOGLE_MAPS_API_KEY_METADATA)
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
        const val LEGACY_GET_META_DATA_FLAG = 128
        const val GOOGLE_MAPS_API_KEY_METADATA = "com.google.android.geo.API_KEY"
        const val HOSPITAL_PRIMARY_TYPE = "hospital"
        const val LOCALITY_COMPONENT_TYPE = "locality"
        val PLACE_FIELDS = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
            Place.Field.PRIMARY_TYPE,
            Place.Field.ADDRESS_COMPONENTS,
        )
    }
}
