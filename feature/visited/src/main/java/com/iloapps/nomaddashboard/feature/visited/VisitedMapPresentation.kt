package com.iloapps.nomaddashboard.feature.visited

import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace

internal data class VisitedMapCoordinate(
    val latitude: Double,
    val longitude: Double,
)

internal data class VisitedMapBounds(
    val southWest: VisitedMapCoordinate,
    val northEast: VisitedMapCoordinate,
) {
    val latitudeSpan: Double
        get() = northEast.latitude - southWest.latitude

    val longitudeSpan: Double
        get() = northEast.longitude - southWest.longitude

    val center: VisitedMapCoordinate
        get() = VisitedMapCoordinate(
            latitude = (southWest.latitude + northEast.latitude) / 2.0,
            longitude = (southWest.longitude + northEast.longitude) / 2.0,
        )

    fun union(other: VisitedMapBounds): VisitedMapBounds =
        VisitedMapBounds(
            southWest = VisitedMapCoordinate(
                latitude = minOf(southWest.latitude, other.southWest.latitude),
                longitude = minOf(southWest.longitude, other.southWest.longitude),
            ),
            northEast = VisitedMapCoordinate(
                latitude = maxOf(northEast.latitude, other.northEast.latitude),
                longitude = maxOf(northEast.longitude, other.northEast.longitude),
            ),
        )

    fun expanded(
        minLatitudeSpan: Double,
        minLongitudeSpan: Double,
        paddingFraction: Double,
    ): VisitedMapBounds {
        val expandedLatitudeSpan = maxOf(latitudeSpan, minLatitudeSpan) * (1.0 + paddingFraction)
        val expandedLongitudeSpan = maxOf(longitudeSpan, minLongitudeSpan) * (1.0 + paddingFraction)
        return around(
            coordinate = center,
            latitudeSpan = expandedLatitudeSpan,
            longitudeSpan = expandedLongitudeSpan,
        )
    }

    companion object {
        val World = VisitedMapBounds(
            southWest = VisitedMapCoordinate(latitude = -55.0, longitude = -170.0),
            northEast = VisitedMapCoordinate(latitude = 80.0, longitude = 170.0),
        )

        fun fromCoordinates(coordinates: List<VisitedMapCoordinate>): VisitedMapBounds? {
            if (coordinates.isEmpty()) {
                return null
            }

            val latitudes = coordinates.map(VisitedMapCoordinate::latitude)
            val longitudes = coordinates.map(VisitedMapCoordinate::longitude)
            return VisitedMapBounds(
                southWest = VisitedMapCoordinate(
                    latitude = latitudes.min(),
                    longitude = longitudes.min(),
                ),
                northEast = VisitedMapCoordinate(
                    latitude = latitudes.max(),
                    longitude = longitudes.max(),
                ),
            )
        }

        fun around(
            coordinate: VisitedMapCoordinate,
            latitudeSpan: Double,
            longitudeSpan: Double,
        ): VisitedMapBounds {
            val boundedLatitudeSpan = latitudeSpan.coerceIn(2.0, 140.0)
            val boundedLongitudeSpan = longitudeSpan.coerceIn(2.0, 340.0)
            return VisitedMapBounds(
                southWest = VisitedMapCoordinate(
                    latitude = (coordinate.latitude - boundedLatitudeSpan / 2.0).coerceIn(-85.0, 85.0),
                    longitude = (coordinate.longitude - boundedLongitudeSpan / 2.0).coerceIn(-180.0, 180.0),
                ),
                northEast = VisitedMapCoordinate(
                    latitude = (coordinate.latitude + boundedLatitudeSpan / 2.0).coerceIn(-85.0, 85.0),
                    longitude = (coordinate.longitude + boundedLongitudeSpan / 2.0).coerceIn(-180.0, 180.0),
                ),
            )
        }
    }
}

internal data class VisitedMapMarker(
    val id: String,
    val title: String,
    val subtitle: String?,
    val coordinate: VisitedMapCoordinate,
)

internal enum class VisitedMapViewportSource {
    HIGHLIGHTED_COUNTRIES,
    MARKERS,
    WORLD,
}

internal data class VisitedMapViewport(
    val bounds: VisitedMapBounds,
    val source: VisitedMapViewportSource,
)

internal data class VisitedMapPresentation(
    val highlightedCountryCodes: Set<String>,
    val markers: List<VisitedMapMarker>,
    val focusLabel: String?,
    val focusCoordinate: VisitedMapCoordinate?,
    val viewport: VisitedMapViewport,
)

internal fun buildVisitedMapPresentation(
    places: List<VisitedPlace>,
    countryDays: List<VisitedCountryDay>,
    selectedYear: Int,
    countryBoundsByCode: Map<String, VisitedMapBounds>,
): VisitedMapPresentation {
    val highlightedCountryCodes = countryDays.asSequence()
        .filter { it.date.year == selectedYear }
        .mapNotNull { normalizeCountryCode(it.countryCode) }
        .toSet()

    val markers = places.mapNotNull(::toVisitedMapMarker)
    val highlightedBounds = highlightedCountryCodes.mapNotNull(countryBoundsByCode::get)
    val focusMarker = resolveFocusMarker(
        places = places,
        highlightedCountryCodes = highlightedCountryCodes,
    )

    val viewport = when {
        highlightedBounds.isNotEmpty() -> VisitedMapViewport(
            bounds = highlightedBounds
                .reduce(VisitedMapBounds::union)
                .let { bounds ->
                    focusMarker?.let { marker ->
                        bounds.union(
                            VisitedMapBounds.around(
                                coordinate = marker.coordinate,
                                latitudeSpan = 8.0,
                                longitudeSpan = 10.0,
                            ),
                        )
                    } ?: bounds
                }
                .expanded(
                    minLatitudeSpan = 10.0,
                    minLongitudeSpan = 14.0,
                    paddingFraction = 0.18,
                ),
            source = VisitedMapViewportSource.HIGHLIGHTED_COUNTRIES,
        )

        markers.isNotEmpty() -> VisitedMapViewport(
            bounds = markersViewportBounds(
                markers = markers,
                focusMarker = focusMarker ?: markers.first(),
            ),
            source = VisitedMapViewportSource.MARKERS,
        )

        else -> VisitedMapViewport(
            bounds = VisitedMapBounds.World,
            source = VisitedMapViewportSource.WORLD,
        )
    }

    return VisitedMapPresentation(
        highlightedCountryCodes = highlightedCountryCodes,
        markers = markers,
        focusLabel = focusMarker?.title,
        focusCoordinate = focusMarker?.coordinate,
        viewport = viewport,
    )
}

private fun markersViewportBounds(
    markers: List<VisitedMapMarker>,
    focusMarker: VisitedMapMarker,
): VisitedMapBounds {
    val allMarkerBounds = VisitedMapBounds.fromCoordinates(markers.map(VisitedMapMarker::coordinate))
    val focusBounds = VisitedMapBounds.around(
        coordinate = focusMarker.coordinate,
        latitudeSpan = 10.0,
        longitudeSpan = 14.0,
    )
    val combinedBounds = when {
        allMarkerBounds == null -> focusBounds
        allMarkerBounds.latitudeSpan <= 12.0 && allMarkerBounds.longitudeSpan <= 18.0 ->
            allMarkerBounds.union(focusBounds)

        else -> focusBounds
    }
    return combinedBounds.expanded(
        minLatitudeSpan = 10.0,
        minLongitudeSpan = 14.0,
        paddingFraction = 0.12,
    )
}

private fun toVisitedMapMarker(place: VisitedPlace): VisitedMapMarker? {
    if (place.supportsMapPin.not()) {
        return null
    }

    val latitude = place.latitude ?: return null
    val longitude = place.longitude ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return null
    }

    return VisitedMapMarker(
        id = place.id,
        title = place.displayName,
        subtitle = place.region?.takeIf(String::isNotBlank) ?: place.country,
        coordinate = VisitedMapCoordinate(latitude = latitude, longitude = longitude),
    )
}

private fun normalizeCountryCode(countryCode: String?): String? =
    countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase()

private data class FocusMarkerCandidate(
    val marker: VisitedMapMarker,
    val countryCode: String?,
)

private fun resolveFocusMarker(
    places: List<VisitedPlace>,
    highlightedCountryCodes: Set<String>,
): VisitedMapMarker? {
    val candidates = places.sortedByDescending(VisitedPlace::lastVisitedAt)
        .mapNotNull { place ->
            toVisitedMapMarker(place)?.let { marker ->
                FocusMarkerCandidate(
                    marker = marker,
                    countryCode = normalizeCountryCode(place.countryCode),
                )
            }
        }

    return candidates.firstOrNull { candidate ->
        candidate.countryCode != null && highlightedCountryCodes.contains(candidate.countryCode)
    }?.marker ?: candidates.firstOrNull()?.marker
}
