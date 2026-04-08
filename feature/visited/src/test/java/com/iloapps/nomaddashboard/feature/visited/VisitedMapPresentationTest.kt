package com.iloapps.nomaddashboard.feature.visited

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class VisitedMapPresentationTest {
    @Test
    fun `selected year highlights matching country codes`() {
        val presentation = buildVisitedMapPresentation(
            places = emptyList(),
            countryDays = listOf(
                countryDay(countryCode = "fi", year = 2026),
                countryDay(countryCode = "SE", year = 2026),
                countryDay(countryCode = "DE", year = 2025),
            ),
            selectedYear = 2026,
            countryBoundsByCode = mapOf(
                "FI" to bounds(60.0, 20.0, 70.0, 32.0),
                "SE" to bounds(55.0, 11.0, 69.0, 24.0),
                "DE" to bounds(47.0, 5.0, 55.0, 15.0),
            ),
        )

        assertThat(presentation.highlightedCountryCodes).containsExactly("FI", "SE")
        assertThat(presentation.viewport.source).isEqualTo(VisitedMapViewportSource.HIGHLIGHTED_COUNTRIES)
    }

    @Test
    fun `highlighted viewport also keeps the latest marker region in frame`() {
        val presentation = buildVisitedMapPresentation(
            places = listOf(
                place(
                    city = "Tarifa",
                    country = "Spain",
                    countryCode = "ES",
                    latitude = 36.013,
                    longitude = -5.606,
                    lastVisitedAt = "2026-04-06T18:20:00Z",
                ),
            ),
            countryDays = listOf(countryDay(countryCode = "FR", year = 2026)),
            selectedYear = 2026,
            countryBoundsByCode = mapOf(
                "FR" to bounds(42.0, -5.5, 51.5, 8.5),
            ),
        )

        assertThat(presentation.viewport.source).isEqualTo(VisitedMapViewportSource.HIGHLIGHTED_COUNTRIES)
        assertThat(presentation.focusLabel).isEqualTo("Tarifa, Spain")
        assertThat(presentation.viewport.bounds.southWest.latitude).isLessThan(36.1)
        assertThat(presentation.viewport.bounds.southWest.longitude).isLessThan(-5.6)
        assertThat(presentation.viewport.bounds.northEast.longitude).isGreaterThan(8.0)
    }

    @Test
    fun `marker extraction ignores entries without valid pin coordinates`() {
        val presentation = buildVisitedMapPresentation(
            places = listOf(
                place(city = "Helsinki", latitude = 60.1699, longitude = 24.9384),
                place(city = null, latitude = 60.0, longitude = 24.0),
                place(city = "Broken", latitude = 120.0, longitude = 24.0),
                place(city = "Missing", latitude = null, longitude = null),
            ),
            countryDays = emptyList(),
            selectedYear = 2026,
            countryBoundsByCode = emptyMap(),
        )

        assertThat(presentation.markers).hasSize(1)
        assertThat(presentation.markers.single().title).isEqualTo("Helsinki, Finland")
        assertThat(presentation.viewport.source).isEqualTo(VisitedMapViewportSource.MARKERS)
    }

    @Test
    fun `marker viewport favors the latest region over global spread`() {
        val presentation = buildVisitedMapPresentation(
            places = listOf(
                place(
                    city = "Tokyo",
                    country = "Japan",
                    countryCode = "JP",
                    latitude = 35.6764,
                    longitude = 139.65,
                    lastVisitedAt = "2026-02-02T10:00:00Z",
                ),
                place(
                    city = "Tarifa",
                    country = "Spain",
                    countryCode = "ES",
                    latitude = 36.013,
                    longitude = -5.606,
                    lastVisitedAt = "2026-04-06T18:20:00Z",
                ),
            ),
            countryDays = emptyList(),
            selectedYear = 2026,
            countryBoundsByCode = emptyMap(),
        )

        assertThat(presentation.viewport.source).isEqualTo(VisitedMapViewportSource.MARKERS)
        assertThat(presentation.focusLabel).isEqualTo("Tarifa, Spain")
        assertThat(presentation.viewport.bounds.longitudeSpan).isLessThan(25.0)
        assertThat(presentation.viewport.bounds.center.longitude).isWithin(6.0).of(-5.606)
    }

    @Test
    fun `viewport falls back from highlighted countries to markers to world`() {
        val markerOnly = buildVisitedMapPresentation(
            places = listOf(place(city = "Tallinn", latitude = 59.437, longitude = 24.7536)),
            countryDays = listOf(countryDay(countryCode = "EE", year = 2026)),
            selectedYear = 2026,
            countryBoundsByCode = emptyMap(),
        )

        val worldFallback = buildVisitedMapPresentation(
            places = emptyList(),
            countryDays = emptyList(),
            selectedYear = 2026,
            countryBoundsByCode = emptyMap(),
        )

        assertThat(markerOnly.viewport.source).isEqualTo(VisitedMapViewportSource.MARKERS)
        assertThat(worldFallback.viewport.source).isEqualTo(VisitedMapViewportSource.WORLD)
        assertThat(worldFallback.viewport.bounds).isEqualTo(VisitedMapBounds.World)
    }

    private fun countryDay(
        countryCode: String,
        year: Int,
    ) = VisitedCountryDay(
        date = LocalDate.of(year, 4, 7),
        country = countryCode,
        countryCode = countryCode,
        source = VisitedPlaceSource.PUBLIC_IP_GEOLOCATION,
        isInferred = false,
    )

    private fun place(
        city: String?,
        country: String = "Finland",
        countryCode: String? = "FI",
        latitude: Double?,
        longitude: Double?,
        lastVisitedAt: String = "2026-04-07T10:15:30Z",
    ) = VisitedPlace(
        city = city,
        region = "Region",
        country = country,
        countryCode = countryCode,
        latitude = latitude,
        longitude = longitude,
        sources = listOf(VisitedPlaceSource.PUBLIC_IP_GEOLOCATION),
        firstVisitedAt = Instant.parse("2026-04-07T10:15:30Z"),
        lastVisitedAt = Instant.parse(lastVisitedAt),
    )

    private fun bounds(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ) = VisitedMapBounds(
        southWest = VisitedMapCoordinate(latitude = south, longitude = west),
        northEast = VisitedMapCoordinate(latitude = north, longitude = east),
    )
}
