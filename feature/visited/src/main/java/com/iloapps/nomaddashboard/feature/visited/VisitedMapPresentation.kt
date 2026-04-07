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

    val viewport = when {
        highlightedBounds.isNotEmpty() -> VisitedMapViewport(
            bounds = highlightedBounds.reduce(VisitedMapBounds::union),
            source = VisitedMapViewportSource.HIGHLIGHTED_COUNTRIES,
        )

        markers.isNotEmpty() -> VisitedMapViewport(
            bounds = VisitedMapBounds.fromCoordinates(markers.map(VisitedMapMarker::coordinate))
                ?: VisitedMapBounds.World,
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
        viewport = viewport,
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
